import { get2048Leaderboard, submit2048Score } from "./store.ts";
import { kv } from "./db.ts";

function assertEquals(actual: unknown, expected: unknown, label = "") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${label}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

Deno.test("2048 leaderboard keeps the highest score per account", async () => {
  const suffix = crypto.randomUUID();
  const first = await submit2048Score(`p-${suffix}`, "甲", 100, 16, 10);
  const lower = await submit2048Score(`p-${suffix}`, "甲改名", 80, 32, 5);
  assertEquals(first.score, 100);
  assertEquals(lower.score, 100);
  assertEquals(lower.maxTile, 16);

  const entries = await get2048Leaderboard(100);
  assertEquals(entries.some((entry) => entry.playerId === `p-${suffix}` && entry.score === 100), true);
  await kv.delete(["leaderboard", "2048", `p-${suffix}`]);
});
