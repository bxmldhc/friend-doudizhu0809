import {
  getStats,
  MatchContext,
  mergeRecentMatch,
  PlayerStats,
  RecentMatch,
  recordGame,
} from "./store.ts";
import { kv } from "./db.ts";

Deno.test("同一房间的小局聚合为一场并按局号排列", () => {
  let matches = mergeRecentMatch([], context("match-a", 2, 200));
  matches = mergeRecentMatch(matches, context("match-a", 1, 100));

  assertEquals(matches.length, 1, "场次数");
  assertEquals(
    matches[0].rounds.map((round) => round.gameNumber).join(","),
    "1,2",
    "小局顺序",
  );
});

Deno.test("最近战绩只保留十场且相同小局不会重复", () => {
  let matches: RecentMatch[] = [];
  for (let index = 0; index < 12; index++) {
    matches = mergeRecentMatch(
      matches,
      context(`match-${index}`, 1, index + 1),
    );
  }
  assertEquals(matches.length, 10, "最近场次数量");
  assertEquals(matches[0].matchId, "match-11", "最新场次");
  assertEquals(matches.at(-1)?.matchId, "match-2", "最早保留场次");

  matches = mergeRecentMatch(matches, context("match-11", 1, 99));
  assertEquals(matches[0].rounds.length, 1, "重复小局数量");
  assertEquals(matches[0].rounds[0].finishedAt, 99, "重复小局更新");
});

Deno.test("recordGame 按地主和农民阵营累计局数与胜场", async () => {
  const landlordId = testPlayerId("landlord");
  const farmerId = testPlayerId("farmer");
  const match = factionContext(landlordId, farmerId, landlordId);

  try {
    await recordGame(match.round.players, match);

    const landlord = await getStats(landlordId);
    const farmer = await getStats(farmerId);
    assertStats(
      landlord,
      { landlordGames: 1, landlordWins: 1, farmerGames: 0, farmerWins: 0 },
      "地主统计",
    );
    assertStats(
      farmer,
      { landlordGames: 0, landlordWins: 0, farmerGames: 1, farmerWins: 0 },
      "农民统计",
    );
  } finally {
    await cleanupPlayers(landlordId, farmerId);
  }
});

Deno.test("旧 KV 统计缺少阵营字段时按零兼容", async () => {
  const playerId = testPlayerId("legacy");
  const legacyStats = {
    playerId,
    name: "旧玩家",
    totalScore: 7,
    games: 3,
    wins: 2,
  };

  try {
    await kv.set(["players", playerId], legacyStats);
    const before = await getStats(playerId);
    assertStats(
      before,
      { landlordGames: 0, landlordWins: 0, farmerGames: 0, farmerWins: 0 },
      "旧数据读取",
    );

    const match = factionContext(
      playerId,
      testPlayerId("legacy-farmer"),
      playerId,
    );
    await recordGame([match.round.players[0]], match);
    const after = await getStats(playerId);
    assertEquals(after?.totalScore, 9, "旧数据总分");
    assertEquals(after?.games, 4, "旧数据总局数");
    assertEquals(after?.wins, 3, "旧数据总胜场");
    assertStats(
      after,
      { landlordGames: 1, landlordWins: 1, farmerGames: 0, farmerWins: 0 },
      "旧数据更新",
    );
  } finally {
    await cleanupPlayers(playerId);
  }
});

Deno.test("recordGame 没有 match 时保留总统计但不累计阵营分项", async () => {
  const playerId = testPlayerId("no-match");

  try {
    await recordGame([
      { playerId, name: "无场次玩家", delta: 3, isAi: false },
    ]);
    const stats = await getStats(playerId);
    assertEquals(stats?.games, 1, "无场次总局数");
    assertEquals(stats?.wins, 1, "无场次总胜场");
    assertStats(
      stats,
      { landlordGames: 0, landlordWins: 0, farmerGames: 0, farmerWins: 0 },
      "无场次阵营统计",
    );
  } finally {
    await cleanupPlayers(playerId);
  }
});

Deno.test("recordGame 的地主 ID 不属于该局玩家时不累计阵营分项", async () => {
  const playerId = testPlayerId("invalid-landlord");
  const match = factionContext(
    testPlayerId("missing-landlord"),
    playerId,
    playerId,
  );
  match.round.players = [match.round.players[1]];

  try {
    await recordGame(match.round.players, match);
    const stats = await getStats(playerId);
    assertEquals(stats?.games, 1, "无效地主总局数");
    assertStats(
      stats,
      { landlordGames: 0, landlordWins: 0, farmerGames: 0, farmerWins: 0 },
      "无效地主阵营统计",
    );
  } finally {
    await cleanupPlayers(playerId);
  }
});

Deno.test("recordGame 不为 AI 创建个人统计", async () => {
  const humanId = testPlayerId("human");
  const aiId = testPlayerId("ai");
  const match = factionContext(aiId, humanId, humanId, true);

  try {
    await recordGame(match.round.players, match);
    const human = await getStats(humanId);
    const ai = await getStats(aiId);
    assertStats(
      human,
      { landlordGames: 0, landlordWins: 0, farmerGames: 1, farmerWins: 1 },
      "真人农民统计",
    );
    assertEquals(human?.wins, 0, "兼容总胜场仍按 delta");
    assertEquals(ai, null, "AI 统计");
  } finally {
    await cleanupPlayers(humanId, aiId);
  }
});

function context(
  matchId: string,
  gameNumber: number,
  finishedAt: number,
): MatchContext {
  return {
    matchId,
    roomCode: "123456",
    startedAt: 1,
    round: {
      gameNumber,
      finishedAt,
      winnerPlayerId: "player-a",
      landlordPlayerId: "player-a",
      multiplier: 2,
      players: [
        { playerId: "player-a", name: "玩家A", delta: 2, isAi: false },
        { playerId: "ai-1", name: "小智", delta: -1, isAi: true },
        { playerId: "ai-2", name: "阿强", delta: -1, isAi: true },
      ],
    },
  };
}

function factionContext(
  landlordPlayerId: string,
  farmerPlayerId: string,
  winnerPlayerId: string,
  landlordIsAi = false,
): MatchContext {
  return {
    matchId: crypto.randomUUID(),
    roomCode: "123456",
    startedAt: 1,
    round: {
      gameNumber: 1,
      finishedAt: 2,
      winnerPlayerId,
      landlordPlayerId,
      multiplier: 2,
      players: [
        {
          playerId: landlordPlayerId,
          name: landlordIsAi ? "地主AI" : "地主玩家",
          delta: 2,
          isAi: landlordIsAi,
        },
        {
          playerId: farmerPlayerId,
          name: "农民玩家",
          delta: -1,
          isAi: false,
        },
      ],
    },
  };
}

function testPlayerId(label: string): string {
  return `store-test-${label}-${crypto.randomUUID()}`;
}

async function cleanupPlayers(...playerIds: string[]) {
  for (const playerId of playerIds) {
    await kv.delete(["players", playerId]);
    await kv.delete(["recentMatches", playerId]);
  }
}

function assertStats(
  actual: PlayerStats | null,
  expected: Pick<
    PlayerStats,
    "landlordGames" | "landlordWins" | "farmerGames" | "farmerWins"
  >,
  label: string,
) {
  if (!actual) {
    throw new Error(`${label}不存在`);
  }
  assertEquals(
    actual.landlordGames,
    expected.landlordGames,
    `${label}地主局数`,
  );
  assertEquals(actual.landlordWins, expected.landlordWins, `${label}地主胜场`);
  assertEquals(actual.farmerGames, expected.farmerGames, `${label}农民局数`);
  assertEquals(actual.farmerWins, expected.farmerWins, `${label}农民胜场`);
}

function assertEquals(actual: unknown, expected: unknown, label: string) {
  if (!Object.is(actual, expected)) {
    throw new Error(
      `${label}不符合预期：期望 ${JSON.stringify(expected)}，实际 ${
        JSON.stringify(actual)
      }`,
    );
  }
}
