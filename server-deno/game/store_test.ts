import {
  getRecentMatches,
  getStats,
  MatchContext,
  mergeRecentMatch,
  PlayerScoreDelta,
  RecentMatch,
  recordGame,
} from "./store.ts";
import { kv } from "./db.ts";

Deno.test("同一场的不同小局合并并按局号排序", () => {
  let matches = mergeRecentMatch([], context("match-a", 2, 200));
  matches = mergeRecentMatch(matches, context("match-a", 1, 100));

  assertEquals(matches.length, 1, "场次数");
  assertEquals(
    matches[0].rounds.map((round) => round.gameNumber).join(","),
    "1,2",
    "小局顺序",
  );
});

Deno.test("单真人对 AI 不会写入统计或最近战绩", async () => {
  const humanId = testPlayerId("solo-human");
  const aiId = testPlayerId("solo-ai");
  const match = createMatch(
    [human(humanId, "真人", 1), ai(aiId, "AI", -1)],
    humanId,
  );
  try {
    await recordGame(match.round.players, match);
    assertEquals(await getStats(humanId), null, "单真人统计");
    assertEquals((await getRecentMatches(humanId)).length, 0, "单真人历史");
  } finally {
    await cleanup(humanId, aiId);
  }
});

Deno.test("没有场次上下文的兼容入口只写聚合统计，不写最近战绩", async () => {
  const firstId = testPlayerId("no-context-first");
  const secondId = testPlayerId("no-context-second");
  try {
    await recordGame([human(firstId, "甲", 2), human(secondId, "乙", -2)]);
    const result = await getStats(firstId);
    assertEquals(result?.games, 1, "无场次聚合统计");
    assertEquals((await getRecentMatches(firstId)).length, 0, "无场次不写历史");
  } finally {
    await cleanup(firstId, secondId);
  }
});

Deno.test("两名已登录真人参与时只记录真人，并保持分类统计", async () => {
  const firstId = testPlayerId("two-human-first");
  const secondId = testPlayerId("two-human-second");
  const aiId = testPlayerId("two-human-ai");
  const match = createMatch(
    [
      human(firstId, "甲", 2),
      human(secondId, "乙", -1),
      ai(aiId, "AI", -1),
    ],
    firstId,
  );
  try {
    await recordGame(match.round.players, match);
    const first = await getStats(firstId);
    const second = await getStats(secondId);
    assertEquals(first?.games, 1, "甲总局数");
    assertEquals(first?.gameStats?.DOUDIZHU?.games, 1, "甲斗地主局数");
    assertEquals(first?.landlordGames, 1, "甲地主局数");
    assertEquals(first?.landlordWins, 1, "甲地主胜场");
    assertEquals(second?.farmerGames, 1, "乙农民局数");
    assertEquals(await getStats(aiId), null, "AI 无个人统计");
  } finally {
    await cleanup(firstId, secondId, aiId);
  }
});

Deno.test("分类战绩会跨场次累计并正确汇总到总战绩", async () => {
  const firstId = testPlayerId("cumulative-first");
  const secondId = testPlayerId("cumulative-second");
  const thirdId = testPlayerId("cumulative-third");
  const first = human(firstId, "甲", 2);
  const second = human(secondId, "乙", -2);
  const third = human(thirdId, "丙", -1);
  const doudizhuOne = createMatch([first, second], firstId);
  const doudizhuTwo = {
    ...doudizhuOne,
    matchId: crypto.randomUUID(),
    round: { ...doudizhuOne.round, gameNumber: 1, players: [
      human(firstId, "甲", 3),
      human(secondId, "乙", -3),
    ] },
  };
  const gomoku = {
    ...doudizhuOne,
    matchId: crypto.randomUUID(),
    gameType: "GOMOKU" as const,
    round: {
      ...doudizhuOne.round,
      players: [human(firstId, "甲", -1), third],
    },
  };
  try {
    await recordGame(doudizhuOne.round.players, doudizhuOne);
    await recordGame(doudizhuTwo.round.players, doudizhuTwo);
    await recordGame(gomoku.round.players, gomoku);
    const result = await getStats(firstId);
    assertEquals(result?.gameStats?.DOUDIZHU?.games, 2, "斗地主局数累计");
    assertEquals(result?.gameStats?.DOUDIZHU?.totalScore, 5, "斗地主分数累计");
    assertEquals(result?.gameStats?.GOMOKU?.games, 1, "五子棋局数累计");
    assertEquals(result?.gameStats?.GOMOKU?.totalScore, -1, "五子棋分数累计");
    assertEquals(result?.games, 3, "总局数累计");
    assertEquals(result?.totalScore, 4, "总分累计");
  } finally {
    await cleanup(firstId, secondId, thirdId);
  }
});

Deno.test("总战绩仅从 gameStats 聚合，不混用旧顶层字段", async () => {
  const playerId = testPlayerId("aggregate");
  try {
    await kv.set(["players", playerId], {
      playerId,
      name: "旧玩家",
      totalScore: 999,
      games: 99,
      wins: 88,
      landlordGames: 77,
      landlordWins: 66,
      farmerGames: 55,
      farmerWins: 44,
      gameStats: {
        DOUDIZHU: stats(8, 2, 1, 1, 1, 1, 0),
        GOMOKU: stats(-1, 3, 1),
      },
    });

    const result = await getStats(playerId);
    assertEquals(result?.totalScore, 7, "总分");
    assertEquals(result?.games, 5, "总局数");
    assertEquals(result?.wins, 2, "总胜场");
    assertEquals(result?.landlordGames, 1, "地主局数");
    assertEquals(result?.farmerGames, 1, "农民局数");
  } finally {
    await cleanup(playerId);
  }
});

Deno.test("无 gameStats 的旧顶层字段不会作为可见统计返回", async () => {
  const playerId = testPlayerId("legacy");
  try {
    await kv.set(["players", playerId], {
      playerId,
      name: "旧玩家",
      totalScore: 50,
      games: 9,
      wins: 8,
      landlordGames: 5,
      landlordWins: 4,
      farmerGames: 4,
      farmerWins: 4,
    });

    const result = await getStats(playerId);
    assertEquals(result?.totalScore, 0, "旧总分");
    assertEquals(result?.games, 0, "旧局数");
    assertEquals(Object.keys(result?.gameStats ?? {}).length, 0, "分类统计");
  } finally {
    await cleanup(playerId);
  }
});

Deno.test("最近战绩会过滤旧的单真人陪练和不可信玩家标识", async () => {
  const firstId = testPlayerId("history-first");
  const secondId = testPlayerId("history-second");
  const aiId = testPlayerId("history-ai");
  const valid = createMatch(
    [human(firstId, "甲", 1), human(secondId, "乙", -1)],
    firstId,
  );
  const solo = createMatch(
    [human(firstId, "甲", 1), ai(aiId, "AI", -1)],
    firstId,
  );
  const untrusted: RecentMatch = {
    matchId: crypto.randomUUID(),
    roomCode: "111111",
    startedAt: 1,
    updatedAt: 3,
    gameType: "DOUDIZHU",
    rounds: [{
      ...valid.round,
      gameNumber: 2,
      players: valid.round.players.map((player) => ({
        ...player,
        isAi: undefined as unknown as boolean,
      })),
    }],
  };
  try {
    await kv.set(["recentMatches", firstId], [
      toRecentMatch(valid),
      toRecentMatch(solo),
      untrusted,
    ]);
    const matches = await getRecentMatches(firstId);
    assertEquals(matches.length, 1, "仅保留可信真人对局");
    assertEquals(matches[0].matchId, valid.matchId, "保留的场次");
  } finally {
    await cleanup(firstId, secondId, aiId);
  }
});

function createMatch(
  players: PlayerScoreDelta[],
  winnerPlayerId: string,
): MatchContext {
  return {
    matchId: crypto.randomUUID(),
    roomCode: "123456",
    startedAt: 1,
    gameType: "DOUDIZHU",
    round: {
      gameNumber: 1,
      finishedAt: 2,
      winnerPlayerId,
      landlordPlayerId: players[0]?.playerId ?? null,
      multiplier: 1,
      players,
    },
  };
}

function context(
  matchId: string,
  gameNumber: number,
  finishedAt: number,
): MatchContext {
  const match = createMatch(
    [human("player-a", "玩家A", 2), human("player-b", "玩家B", -2)],
    "player-a",
  );
  return {
    ...match,
    matchId,
    round: { ...match.round, gameNumber, finishedAt },
  };
}

function toRecentMatch(context: MatchContext): RecentMatch {
  return {
    matchId: context.matchId,
    roomCode: context.roomCode,
    startedAt: context.startedAt,
    updatedAt: context.round.finishedAt,
    gameType: context.gameType,
    rounds: [context.round],
  };
}

function human(playerId: string, name: string, delta: number): PlayerScoreDelta {
  return { playerId, name, delta, isAi: false };
}

function ai(playerId: string, name: string, delta: number): PlayerScoreDelta {
  return { playerId, name, delta, isAi: true };
}

function stats(
  totalScore: number,
  games: number,
  wins: number,
  landlordGames = 0,
  landlordWins = 0,
  farmerGames = 0,
  farmerWins = 0,
) {
  return {
    totalScore,
    games,
    wins,
    landlordGames,
    landlordWins,
    farmerGames,
    farmerWins,
  };
}

function testPlayerId(label: string): string {
  return `store-test-${label}-${crypto.randomUUID()}`;
}

async function cleanup(...playerIds: string[]) {
  for (const playerId of playerIds) {
    await kv.delete(["players", playerId]);
    await kv.delete(["recentMatches", playerId]);
  }
}

function assertEquals(actual: unknown, expected: unknown, label: string) {
  if (!Object.is(actual, expected)) {
    throw new Error(
      `${label}: expected ${JSON.stringify(expected)}, received ${
        JSON.stringify(actual)
      }`,
    );
  }
}
