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
  const farmerId = testPlayerId("legacy-farmer");
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
      farmerId,
      playerId,
    );
    await recordGame(match.round.players, match);
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
    await cleanupPlayers(playerId, farmerId);
  }
});

Deno.test("recordGame 没有 match 时保留总统计但不累计阵营分项", async () => {
  const playerId = testPlayerId("no-match");
  const secondPlayerId = testPlayerId("no-match-second");

  try {
    await recordGame([
      { playerId, name: "无场次玩家", delta: 3, isAi: false },
      { playerId: secondPlayerId, name: "第二位玩家", delta: -3, isAi: false },
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
    await cleanupPlayers(playerId, secondPlayerId);
  }
});

Deno.test("recordGame 的地主 ID 不属于该局玩家时不累计阵营分项", async () => {
  const playerId = testPlayerId("invalid-landlord");
  const secondPlayerId = testPlayerId("invalid-landlord-second");
  const match = factionContext(
    testPlayerId("missing-landlord"),
    playerId,
    playerId,
  );
  match.round.players = [
    match.round.players[1],
    {
      playerId: secondPlayerId,
      name: "第二位玩家",
      delta: -1,
      isAi: false,
    },
  ];

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
    await cleanupPlayers(playerId, secondPlayerId);
  }
});

Deno.test("recordGame 单真人对 AI 不创建个人统计", async () => {
  const humanId = testPlayerId("human");
  const aiId = testPlayerId("ai");
  const match = factionContext(aiId, humanId, humanId, true);

  try {
    await recordGame(match.round.players, match);
    const human = await getStats(humanId);
    const ai = await getStats(aiId);
    assertEquals(human, null, "单真人战绩");
    assertEquals(ai, null, "AI 统计");
  } finally {
    await cleanupPlayers(humanId, aiId);
  }
});

Deno.test("recordGame 两名真人加 AI 时正常记录真人", async () => {
  const humanId = testPlayerId("two-human");
  const secondHumanId = testPlayerId("two-human-second");
  const aiId = testPlayerId("two-human-ai");
  const match = factionContext(aiId, humanId, humanId, true);
  match.round.players.push({
    playerId: secondHumanId,
    name: "第二位真人",
    delta: -1,
    isAi: false,
  });

  try {
    await recordGame(match.round.players, match);
    const human = await getStats(humanId);
    const secondHuman = await getStats(secondHumanId);
    const ai = await getStats(aiId);
    assertEquals(human?.games, 1, "第一位真人局数");
    assertEquals(secondHuman?.games, 1, "第二位真人局数");
    assertEquals(ai, null, "AI 统计");
  } finally {
    await cleanupPlayers(humanId, secondHumanId, aiId);
  }
});

Deno.test("分类战绩分别累计斗地主和五子棋", async () => {
  const playerId = testPlayerId("game-stats");
  const farmerId = testPlayerId("game-stats-farmer");
  const doudizhu = factionContext(playerId, farmerId, playerId);
  const gomoku: MatchContext = {
    matchId: crypto.randomUUID(),
    roomCode: "654321",
    startedAt: 3,
    gameType: "GOMOKU",
    round: {
      gameNumber: 1,
      finishedAt: 4,
      winnerPlayerId: playerId,
      landlordPlayerId: null,
      multiplier: 1,
      players: [
        { playerId, name: "五子棋玩家", delta: 1, isAi: false },
        { playerId: farmerId, name: "对手", delta: -1, isAi: false },
      ],
    },
  };

  try {
    await recordGame(doudizhu.round.players, doudizhu);
    await recordGame(gomoku.round.players, gomoku);
    const stats = await getStats(playerId);
    assertEquals(stats?.gameStats?.DOUDIZHU?.games, 1, "斗地主分类局数");
    assertEquals(stats?.gameStats?.DOUDIZHU?.landlordWins, 1, "斗地主地主胜场");
    assertEquals(stats?.gameStats?.GOMOKU?.games, 1, "五子棋分类局数");
    assertEquals(stats?.gameStats?.GOMOKU?.wins, 1, "五子棋分类胜场");
    assertEquals(stats?.gameStats?.GOMOKU?.landlordGames, 0, "五子棋不记录地主局数");
    assertEquals(stats?.gameStats?.GOMOKU?.farmerGames, 0, "五子棋不记录农民局数");
  } finally {
    await cleanupPlayers(playerId, farmerId);
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
