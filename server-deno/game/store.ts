export interface PlayerStats {
  name: string;
  games: number;
  wins: number;
}

const kv = await Deno.openKv();

export async function recordGame(
  names: string[],
  winnerIndex: number,
  landlordIndex: number,
  scoreDelta: number[]
) {
  for (let i = 0; i < names.length; i++) {
    const name = names[i];
    if (!name || name === "等待加入") continue;
    const key = ["players", name];
    const current = (await kv.get<PlayerStats>(key)).value ?? {
      name,
      games: 0,
      wins: 0,
    };
    const win = scoreDelta[i] > 0;
    await kv.set(key, {
      name,
      games: current.games + 1,
      wins: current.wins + (win ? 1 : 0),
    });
  }
}

export async function getStats(name: string): Promise<PlayerStats | null> {
  const result = await kv.get<PlayerStats>(["players", name]);
  return result.value ?? null;
}
