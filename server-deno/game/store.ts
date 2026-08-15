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
  rounds: MatchRoundRecord[];
}

export interface MatchContext {
  matchId: string;
  roomCode: string;
  startedAt: number;
  round: MatchRoundRecord;
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

export async function recordGame(
  players: PlayerScoreDelta[],
  match?: MatchContext,
) {
  const factionResult = getFactionResult(match);
  for (const player of players) {
    if (!player.playerId || player.isAi) continue;
    const key = ["players", player.playerId];
    const historyKey = ["recentMatches", player.playerId];

    while (true) {
      const [entry, historyEntry] = await kv.getMany<
        [PlayerStats, RecentMatch[]]
      >([key, historyKey]);
      const current = normalizePlayerStats(entry.value, player);
      const isLandlord = factionResult?.landlordPlayerId === player.playerId;
      const isFactionWinner = factionResult
        ? (isLandlord
          ? factionResult.winnerFaction === "LANDLORD"
          : factionResult.winnerFaction === "FARMER")
        : false;
      const next: PlayerStats = {
        playerId: player.playerId,
        name: player.name,
        totalScore: current.totalScore + player.delta,
        games: current.games + 1,
        wins: current.wins + (player.delta > 0 ? 1 : 0),
        landlordGames: current.landlordGames +
          (factionResult && isLandlord ? 1 : 0),
        landlordWins: current.landlordWins +
          (factionResult && isLandlord && isFactionWinner ? 1 : 0),
        farmerGames: current.farmerGames +
          (factionResult && !isLandlord ? 1 : 0),
        farmerWins: current.farmerWins +
          (factionResult && !isLandlord && isFactionWinner ? 1 : 0),
      };
      let operation = kv.atomic()
        .check(entry)
        .set(key, next);
      if (match) {
        operation = operation
          .check(historyEntry)
          .set(historyKey, mergeRecentMatch(historyEntry.value ?? [], match));
      }
      const result = await operation.commit();
      if (result.ok) break;
    }
  }
}

export async function getStats(playerId: string): Promise<PlayerStats | null> {
  const result = await kv.get<PlayerStats>(["players", playerId]);
  if (!result.value) return null;
  return normalizePlayerStats(result.value, {
    playerId: result.value.playerId,
    name: result.value.name,
  });
}

function normalizePlayerStats(
  stats: PlayerStats | null,
  player: Pick<PlayerScoreDelta, "playerId" | "name">,
): PlayerStats {
  return {
    playerId: stats?.playerId ?? player.playerId,
    name: stats?.name ?? player.name,
    totalScore: stats?.totalScore ?? 0,
    games: stats?.games ?? 0,
    wins: stats?.wins ?? 0,
    landlordGames: stats?.landlordGames ?? 0,
    landlordWins: stats?.landlordWins ?? 0,
    farmerGames: stats?.farmerGames ?? 0,
    farmerWins: stats?.farmerWins ?? 0,
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
  return result.value ?? [];
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
    rounds,
  };
  return [
    updated,
    ...current.filter((match) => match.matchId !== context.matchId),
  ].sort((left, right) => right.updatedAt - left.updatedAt).slice(0, 10);
}
