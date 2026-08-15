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

export async function recordGame(players: PlayerScoreDelta[]) {
  for (const player of players) {
    if (!player.playerId || player.isAi) continue;
    const key = ["players", player.playerId];

    while (true) {
      const entry = await kv.get<PlayerStats>(key);
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
      const result = await kv.atomic()
        .check(entry)
        .set(key, next)
        .commit();
      if (result.ok) break;
    }
  }
}

export async function getStats(playerId: string): Promise<PlayerStats | null> {
  const result = await kv.get<PlayerStats>(["players", playerId]);
  return result.value ?? null;
}
