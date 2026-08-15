import { kv } from "./db.ts";

export interface PlayerStats {
  playerId: string;
  name: string;
  totalScore: number;
  games: number;
  wins: number;
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

export async function recordGame(
  players: PlayerScoreDelta[],
  match?: MatchContext,
) {
  for (const player of players) {
    if (!player.playerId || player.isAi) continue;
    const key = ["players", player.playerId];
    const historyKey = ["recentMatches", player.playerId];

    while (true) {
      const [entry, historyEntry] = await kv.getMany<
        [PlayerStats, RecentMatch[]]
      >([key, historyKey]);
      const current = entry.value ?? {
        playerId: player.playerId,
        name: player.name,
        totalScore: 0,
        games: 0,
        wins: 0,
      };
      const next: PlayerStats = {
        playerId: player.playerId,
        name: player.name,
        totalScore: current.totalScore + player.delta,
        games: current.games + 1,
        wins: current.wins + (player.delta > 0 ? 1 : 0),
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
  return result.value ?? null;
}

export async function getRecentMatches(playerId: string): Promise<RecentMatch[]> {
  const result = await kv.get<RecentMatch[]>(["recentMatches", playerId]);
  return result.value ?? [];
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
