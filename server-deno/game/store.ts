import { kv } from "./db.ts";

export interface PlayerStats {
  playerId: string;
  name: string;
  totalScore: number;
  games: number;
  wins: number;
  landlordGames: number;
  landlordWins: number;
  farmerGames: number;
  farmerWins: number;
  gameStats?: Record<string, GameStats>;
}

export interface GameStats {
  totalScore: number;
  games: number;
  wins: number;
  landlordGames: number;
  landlordWins: number;
  farmerGames: number;
  farmerWins: number;
}

export interface GameLeaderboardEntry {
  playerId: string;
  name: string;
  score: number;
  maxTile: number;
  moves: number;
  updatedAt: number;
}

export interface PlayerScoreDelta {
  playerId: string;
  name: string;
  delta: number;
  isAi: boolean;
}

export interface MatchRoundRecord {
  gameNumber: number;
  finishedAt: number;
  winnerPlayerId: string | null;
  landlordPlayerId: string | null;
  multiplier: number;
  players: PlayerScoreDelta[];
}

export interface RecentMatch {
  matchId: string;
  roomCode: string;
  startedAt: number;
  updatedAt: number;
  gameType?: "DOUDIZHU" | "GOMOKU" | "LOVE_LETTER";
  rounds: MatchRoundRecord[];
}

export interface MatchContext {
  matchId: string;
  roomCode: string;
  startedAt: number;
  round: MatchRoundRecord;
  gameType?: "DOUDIZHU" | "GOMOKU" | "LOVE_LETTER";
}

export interface AdminAuditRecord {
  auditId: string;
  createdAt: number;
  actorAccountId: string;
  actorNickname: string;
  action:
    | "ACCOUNT_DISABLED"
    | "ACCOUNT_ENABLED"
    | "PASSWORD_RESET"
    | "REGISTRATION_ENABLED"
    | "REGISTRATION_DISABLED";
  targetAccountId?: string;
  targetNickname?: string;
}

const EMPTY_GAME_STATS: GameStats = {
  totalScore: 0,
  games: 0,
  wins: 0,
  landlordGames: 0,
  landlordWins: 0,
  farmerGames: 0,
  farmerWins: 0,
};

export async function recordGame(
  players: PlayerScoreDelta[],
  match?: MatchContext,
) {
  // 至少两名不同的非 AI 玩家才可能形成可见真人战绩。
  // 生产房间结算会携带完整 match；保留旧的无场次统计入口，
  // 但它不写入最近战绩，避免把不完整对局伪装成一场可回看的比赛。
  if (!hasEnoughHumanPlayers(players)) return;

  const verifiedMatch = match ? normalizeMatchContext(match, players) : undefined;
  const factionResult = getFactionResult(verifiedMatch);
  const gameType = verifiedMatch?.gameType ?? "DOUDIZHU";
  for (const player of players) {
    if (!isAuthenticatedHuman(player)) continue;
    const key = ["players", player.playerId];
    const historyKey = ["recentMatches", player.playerId];

    while (true) {
      const [entry, historyEntry] = await kv.getMany<
        [PlayerStats, RecentMatch[]]
      >([key, historyKey]);
      const current = normalizePlayerStats(entry.value, player);
      const nextGameStats = {
        ...current.gameStats,
        [gameType]: mergeGameStats(
          current.gameStats?.[gameType],
          player,
          factionResult,
        ),
      };
      const next: PlayerStats = {
        playerId: player.playerId,
        name: player.name,
        totalScore: 0,
        games: 0,
        wins: 0,
        landlordGames: 0,
        landlordWins: 0,
        farmerGames: 0,
        farmerWins: 0,
        gameStats: nextGameStats,
      };
      applyAggregateStats(next);
      const operation = kv.atomic()
        .check(entry)
        .set(key, next);
      if (verifiedMatch) {
        operation
          .check(historyEntry)
          .set(
            historyKey,
            mergeRecentMatch(historyEntry.value ?? [], verifiedMatch),
          );
      }
      const committed = await operation.commit();
      if (committed.ok) break;
    }
  }
}

export async function getStats(playerId: string): Promise<PlayerStats | null> {
  const result = await kv.get<PlayerStats>(["players", playerId]);
  if (!result.value) return null;
  const normalized = normalizePlayerStats(result.value, {
    playerId: result.value.playerId,
    name: result.value.name,
  });
  applyAggregateStats(normalized);
  return normalized;
}

export async function submit2048Score(
  playerId: string,
  name: string,
  score: number,
  maxTile: number,
  moves: number,
): Promise<GameLeaderboardEntry> {
  const key = ["leaderboard", "2048", playerId];
  const cleanScore = Math.max(0, Math.floor(score));
  const cleanTile = Math.max(0, Math.floor(maxTile));
  const cleanMoves = Math.max(0, Math.floor(moves));
  while (true) {
    const current = await kv.get<GameLeaderboardEntry>(key);
    const previous = current.value;
    const next: GameLeaderboardEntry = previous && previous.score >= cleanScore
      ? { ...previous, name, updatedAt: Date.now() }
      : {
        playerId,
        name,
        score: cleanScore,
        maxTile: cleanTile,
        moves: cleanMoves,
        updatedAt: Date.now(),
      };
    const committed = await kv.atomic().check(current).set(key, next).commit();
    if (committed.ok) return next;
  }
}

export async function get2048Leaderboard(limit = 20): Promise<GameLeaderboardEntry[]> {
  const entries: GameLeaderboardEntry[] = [];
  for await (const entry of kv.list<GameLeaderboardEntry>(
    { prefix: ["leaderboard", "2048"] },
  )) entries.push(entry.value);
  return entries
    .sort((left, right) => right.score - left.score || right.maxTile - left.maxTile || left.name.localeCompare(right.name, "zh-CN"))
    .slice(0, Math.max(1, Math.min(100, Math.floor(limit))));
}

function normalizePlayerStats(
  stats: PlayerStats | null,
  player: Pick<PlayerScoreDelta, "playerId" | "name">,
): PlayerStats {
  return {
    playerId: stats?.playerId ?? player.playerId,
    name: stats?.name ?? player.name,
    // 新的可见累计值只从分类统计推导，不混入旧顶层字段。
    totalScore: 0,
    games: 0,
    wins: 0,
    landlordGames: 0,
    landlordWins: 0,
    farmerGames: 0,
    farmerWins: 0,
    gameStats: normalizeGameStatsMap(stats?.gameStats),
  };
}

function mergeGameStats(
  current: GameStats | undefined,
  player: PlayerScoreDelta,
  factionResult: ReturnType<typeof getFactionResult>,
): GameStats {
  const value = normalizeGameStats(current);
  const isLandlord = factionResult?.landlordPlayerId === player.playerId;
  const isFactionWinner = factionResult
    ? (isLandlord
      ? factionResult.winnerFaction === "LANDLORD"
      : factionResult.winnerFaction === "FARMER")
    : false;
  return {
    totalScore: value.totalScore + player.delta,
    games: value.games + 1,
    wins: value.wins + (player.delta > 0 ? 1 : 0),
    landlordGames: value.landlordGames + (isLandlord ? 1 : 0),
    landlordWins: value.landlordWins + (isLandlord && isFactionWinner ? 1 : 0),
    farmerGames: value.farmerGames + (factionResult && !isLandlord ? 1 : 0),
    farmerWins: value.farmerWins +
      (factionResult && !isLandlord && isFactionWinner ? 1 : 0),
  };
}

function getFactionResult(match?: MatchContext): {
  landlordPlayerId: string;
  winnerFaction: "LANDLORD" | "FARMER" | null;
} | null {
  if (!match) return null;
  const landlordPlayerId = match.round.landlordPlayerId?.trim();
  if (
    !landlordPlayerId ||
    !match.round.players.some((player) => player.playerId === landlordPlayerId)
  ) {
    return null;
  }
  const winnerPlayerId = match.round.winnerPlayerId;
  const winnerIsValid = winnerPlayerId !== null &&
    match.round.players.some((player) => player.playerId === winnerPlayerId);
  return {
    landlordPlayerId,
    winnerFaction: !winnerIsValid
      ? null
      : winnerPlayerId === landlordPlayerId
      ? "LANDLORD"
      : "FARMER",
  };
}

export async function getRecentMatches(
  playerId: string,
): Promise<RecentMatch[]> {
  const result = await kv.get<RecentMatch[]>(["recentMatches", playerId]);
  return (result.value ?? [])
    .filter(isVisibleRecentMatch)
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, 10);
}

export async function recordAdminAudit(
  record: Omit<AdminAuditRecord, "auditId" | "createdAt">,
): Promise<AdminAuditRecord> {
  const stored: AdminAuditRecord = {
    ...record,
    auditId: crypto.randomUUID(),
    createdAt: Date.now(),
  };
  await kv.set(
    ["adminAudit", stored.createdAt, stored.auditId],
    stored,
  );
  return stored;
}

export async function listAdminAudit(
  requestedLimit = 50,
): Promise<AdminAuditRecord[]> {
  const limit = Math.max(
    1,
    Math.min(
      100,
      Number.isFinite(requestedLimit) ? Math.floor(requestedLimit) : 50,
    ),
  );
  const records: AdminAuditRecord[] = [];
  for await (
    const entry of kv.list<AdminAuditRecord>(
      { prefix: ["adminAudit"] },
      { reverse: true, limit },
    )
  ) {
    records.push(entry.value);
  }
  return records;
}

export function mergeRecentMatch(
  current: RecentMatch[],
  context: MatchContext,
): RecentMatch[] {
  const existing = current.find((match) => match.matchId === context.matchId);
  const rounds = [
    ...(existing?.rounds ?? []).filter((round) =>
      round.gameNumber !== context.round.gameNumber
    ),
    context.round,
  ].sort((left, right) => left.gameNumber - right.gameNumber);
  const updated: RecentMatch = {
    matchId: context.matchId,
    roomCode: context.roomCode,
    startedAt: existing?.startedAt ?? context.startedAt,
    updatedAt: context.round.finishedAt,
    gameType: context.gameType ?? existing?.gameType ?? "DOUDIZHU",
    rounds,
  };
  return [
    updated,
    ...current.filter((match) => match.matchId !== context.matchId),
  ].sort((left, right) => right.updatedAt - left.updatedAt).slice(0, 10);
}

function isAuthenticatedHuman(player: PlayerScoreDelta): boolean {
  return player.isAi === false && Boolean(player.playerId?.trim());
}

function hasEnoughHumanPlayers(players: PlayerScoreDelta[]): boolean {
  return new Set(
    players
      .filter(isAuthenticatedHuman)
      .map((player) => player.playerId.trim()),
  ).size >= 2;
}

function normalizeMatchContext(
  match: MatchContext,
  players: PlayerScoreDelta[],
): MatchContext {
  const playersById = new Map(
    players
      .filter((player) => Boolean(player.playerId?.trim()))
      .map((player) => [player.playerId, player]),
  );
  return {
    ...match,
    round: {
      ...match.round,
      players: match.round.players.map((roundPlayer) => {
        const verified = playersById.get(roundPlayer.playerId);
        return verified
          ? {
            ...roundPlayer,
            playerId: verified.playerId,
            name: verified.name,
            delta: verified.delta,
            isAi: verified.isAi,
          }
          : roundPlayer;
      }),
    },
  };
}

function isVisibleRecentMatch(match: RecentMatch): boolean {
  if (!match?.matchId || !Array.isArray(match.rounds) || !match.rounds.length) {
    return false;
  }
  return match.rounds.every((round) =>
    hasEnoughHumanPlayers(round.players ?? [])
  );
}

function normalizeGameStatsMap(
  gameStats: Record<string, GameStats> | undefined,
): Record<string, GameStats> {
  if (!gameStats) return {};
  return Object.fromEntries(
    Object.entries(gameStats)
      .filter(([gameType]) => Boolean(gameType.trim()))
      .map(([gameType, stats]) => [gameType, normalizeGameStats(stats)]),
  );
}

function normalizeGameStats(stats: GameStats | undefined): GameStats {
  return {
    totalScore: validNumber(stats?.totalScore),
    games: validCount(stats?.games),
    wins: validCount(stats?.wins),
    landlordGames: validCount(stats?.landlordGames),
    landlordWins: validCount(stats?.landlordWins),
    farmerGames: validCount(stats?.farmerGames),
    farmerWins: validCount(stats?.farmerWins),
  };
}

function applyAggregateStats(stats: PlayerStats): void {
  const aggregate = Object.values(stats.gameStats ?? {}).reduce<GameStats>(
    (total, current) => ({
      totalScore: total.totalScore + current.totalScore,
      games: total.games + current.games,
      wins: total.wins + current.wins,
      landlordGames: total.landlordGames + current.landlordGames,
      landlordWins: total.landlordWins + current.landlordWins,
      farmerGames: total.farmerGames + current.farmerGames,
      farmerWins: total.farmerWins + current.farmerWins,
    }),
    { ...EMPTY_GAME_STATS },
  );
  stats.totalScore = aggregate.totalScore;
  stats.games = aggregate.games;
  stats.wins = aggregate.wins;
  stats.landlordGames = aggregate.landlordGames;
  stats.landlordWins = aggregate.landlordWins;
  stats.farmerGames = aggregate.farmerGames;
  stats.farmerWins = aggregate.farmerWins;
}

function validNumber(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function validCount(value: unknown): number {
  return Math.max(0, Math.floor(validNumber(value)));
}
