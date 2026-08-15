import {
  MatchContext,
  mergeRecentMatch,
  RecentMatch,
} from "./store.ts";

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

function assertEquals(actual: unknown, expected: unknown, label: string) {
  if (!Object.is(actual, expected)) {
    throw new Error(
      `${label}不符合预期：期望 ${JSON.stringify(expected)}，实际 ${
        JSON.stringify(actual)
      }`,
    );
  }
}
