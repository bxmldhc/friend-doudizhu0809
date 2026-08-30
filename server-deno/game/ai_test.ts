import { AiAction, decide } from "./ai.ts";
import { Card, parseCards, removeCards } from "./cards.ts";
import { EngineState, SeatState } from "./engine.ts";
import { DoudizhuEngine } from "./engine.ts";
import { allPlays, classify } from "./hand.ts";

function cards(...notations: string[]): Card[] {
  return parseCards(notations);
}

function seat(
  index: number,
  hand: Card[],
  isLandlord = false,
): SeatState {
  return {
    index,
    name: `seat-${index}`,
    isHuman: false,
    isAway: false,
    hand,
    isLandlord,
    score: 0,
  };
}

function playingState(options: {
  aiHand: Card[];
  landlordHand?: Card[];
  teammateHand?: Card[];
  lastPlay?: Card[];
  lastPlayerIndex?: number | null;
}): EngineState {
  return {
    phase: "PLAYING",
    seats: [
      seat(
        0,
        options.landlordHand ??
          cards("6S", "7S", "8S", "9S", "10S", "JS"),
        true,
      ),
      seat(1, options.aiHand),
      seat(
        2,
        options.teammateHand ??
          cards("6H", "7H", "8H", "9H", "10H", "JH"),
      ),
    ],
    turnIndex: 1,
    landlordIndex: 0,
    bidStartIndex: 0,
    bidCandidateIndex: 0,
    bidCount: 1,
    bidMultiplier: 1,
    actedCount: 3,
    lastPlay: options.lastPlay ?? [],
    lastPlayerIndex: options.lastPlayerIndex ?? null,
    lastPlaysBySeat: [[], [], []],
    lastActionsBySeat: [null, null, null],
    passes: 0,
    bottomCards: [],
    multiplier: 1,
    seenRanks: {},
    winnerIndex: null,
    scoreDelta: [],
    message: "",
    gameNumber: 1,
  };
}

function assertPass(action: AiAction, label: string): void {
  if (action.type !== "pass") {
    throw new Error(`${label}: 期望不出，实际动作是 ${action.type}`);
  }
}

function assertPlay(action: AiAction, label: string): Card[] {
  if (action.type !== "play") {
    throw new Error(`${label}: 期望出牌，实际动作是 ${action.type}`);
  }
  return action.cards;
}

Deno.test("困难农民非紧急时不压队友", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "5S", "7S", "7H", "9S"),
      lastPlay: cards("3S"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );

  assertPass(action, "队友领出的低单张不应被压");
});

Deno.test("队友仅剩一张时困难农民会用低单张抬高地主接牌门槛", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "7S", "7H", "9S"),
      teammateHand: cards("9C"),
      lastPlay: cards("3D"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );
  const played = assertPlay(action, "队友报单且领出小牌时应接住队友");
  const pattern = classify(played);

  if (pattern?.type !== "SINGLE" || pattern.primary !== 4) {
    throw new Error(
      `期望用最低单张 4 抬高门槛，实际牌型 ${
        pattern?.type ?? "非法"
      }，点数 ${pattern?.primary ?? "无"}`,
    );
  }
});

Deno.test("队友仅剩一对时困难农民会用低对子抬高地主接牌门槛", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "4H", "7S", "7H", "9S"),
      teammateHand: cards("9C", "9D"),
      lastPlay: cards("3D", "3C"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );
  const played = assertPlay(action, "队友剩一对且领出低对时应接住队友");
  const pattern = classify(played);

  if (pattern?.type !== "PAIR" || pattern.primary !== 4) {
    throw new Error(
      `期望用最低对子 4 抬高门槛，实际牌型 ${
        pattern?.type ?? "非法"
      }，点数 ${pattern?.primary ?? "无"}`,
    );
  }
});

Deno.test("队友剩两张散牌且领出单张时困难农民会继续接牌", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "7S", "7H", "9S"),
      teammateHand: cards("9C", "JD"),
      lastPlay: cards("3D"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );
  const played = assertPlay(action, "队友剩两张散牌时报单时应接住队友");
  const pattern = classify(played);
  if (pattern?.type !== "SINGLE" || pattern.primary !== 4) {
    throw new Error("应使用最低安全单张 4 接牌");
  }
});

Deno.test("队友残局抢牌权不会为了接低对子使用炸弹", () => {
  const action = decide(
    playingState({
      aiHand: cards("3S", "4S", "4H", "4C", "4D"),
      teammateHand: cards("9C", "9D"),
      lastPlay: cards("3D", "3C"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );

  assertPass(action, "不能为了接住队友的小对子而使用炸弹");
});

Deno.test("队友剩 1 张且困难农民领牌时优先送低单张", () => {
  const action = decide(
    playingState({
      aiHand: cards("3S", "4S", "7S", "7H", "8S", "8H"),
      teammateHand: cards("9C"),
    }),
    "HARD",
  );
  const played = assertPlay(action, "队友报单时应主动送单张");
  const pattern = classify(played);

  if (pattern?.type !== "SINGLE" || pattern.primary !== 3) {
    throw new Error(
      `期望送最低单张 3，实际牌型 ${pattern?.type ?? "非法"}，点数 ${
        pattern?.primary ?? "无"
      }`,
    );
  }
});

Deno.test("队友剩 2 张且困难农民领牌时优先送低对子", () => {
  const action = decide(
    playingState({
      aiHand: cards("3S", "3H", "7S", "8S", "10S"),
      teammateHand: cards("9C", "9D"),
    }),
    "HARD",
  );
  const played = assertPlay(action, "队友剩两张时应优先送对子");
  const pattern = classify(played);

  if (pattern?.type !== "PAIR" || pattern.primary !== 3) {
    throw new Error(
      `期望送最低对子 3，实际牌型 ${pattern?.type ?? "非法"}，点数 ${
        pattern?.primary ?? "无"
      }`,
    );
  }
});

Deno.test("地主报单时困难农民会积极拆牌拦截", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "4H", "4C", "7S", "8S", "9S", "10S"),
      landlordHand: cards("AS"),
      lastPlay: cards("3D"),
      lastPlayerIndex: 0,
    }),
    "HARD",
  );
  const played = assertPlay(action, "地主报单时不应保守放过");
  const pattern = classify(played);

  if (pattern?.type !== "SINGLE" || pattern.primary !== 4) {
    throw new Error(
      `期望拆出最低可压单张 4，实际牌型 ${pattern?.type ?? "非法"}，点数 ${
        pattern?.primary ?? "无"
      }`,
    );
  }
});

Deno.test("8 张以内存在两手走完结构时优先减少后续手数", () => {
  const hand = cards("3S", "3H", "3C", "4S", "4H", "5S", "5H");
  const action = decide(
    playingState({
      aiHand: hand,
    }),
    "HARD",
  );
  const played = assertPlay(action, "短残局应优先形成两手走完结构");
  const remaining = removeCards(hand, played);
  const canFinishNext = allPlays(remaining).some((play) =>
    play.length === remaining.length
  );

  if (!canFinishNext) {
    throw new Error(
      `期望首手后剩余牌可一手出完，实际首手牌型 ${
        classify(played)?.type ?? "非法"
      }，剩余 ${remaining.length} 张`,
    );
  }
});

Deno.test("非紧急且只有炸弹能压时困难 AI 选择保留炸弹", () => {
  const action = decide(
    playingState({
      aiHand: cards(
        "3S",
        "3H",
        "3C",
        "3D",
        "4S",
        "4H",
        "5S",
        "5H",
        "6S",
      ),
      landlordHand: cards("7S", "8S", "9S", "10S", "JS", "QS"),
      teammateHand: cards("7H", "8H", "9H", "10H", "JH", "QH"),
      lastPlay: cards("7C", "8C", "9C", "10C", "JC"),
      lastPlayerIndex: 0,
    }),
    "HARD",
  );

  assertPass(action, "局势不紧急时不应为了普通一手牌使用炸弹");
});

Deno.test("地主作为下家报单时困难农民不主动送单张", () => {
  const action = decide(
    playingState({
      aiHand: cards("3S", "4S", "4H", "7S", "8S"),
      landlordHand: cards("10S"),
    }),
    "HARD",
  );
  const played = assertPlay(action, "地主下家报单时应改变领牌牌型");
  const pattern = classify(played);

  if (pattern?.type === "SINGLE") {
    throw new Error("地主作为下家且只剩一张时不应主动领单张");
  }
});

Deno.test("地主报单时队友领出的牌已有保护价值则不盲目抢牌权", () => {
  const action = decide(
    playingState({
      aiHand: cards("KS", "AS", "4S", "4H", "7S"),
      landlordHand: cards("10S"),
      lastPlay: cards("QS"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );

  assertPass(action, "不能确认封顶时应信任队友的高单张");
});

Deno.test("地主报单时困难农民可以用确定控制牌接住队友牌", () => {
  const action = decide(
    playingState({
      aiHand: cards("B", "4S", "4H", "7S"),
      landlordHand: cards("10S"),
      lastPlay: cards("AS"),
      lastPlayerIndex: 2,
    }),
    "HARD",
  );
  const played = assertPlay(action, "有确定控制牌时应阻止地主接牌");

  if (classify(played)?.type !== "SINGLE" || played[0].r !== 17) {
    throw new Error("应使用大王接住队友的单张并封住地主");
  }
});

Deno.test("响应单张时优先保留完整顺子结构", () => {
  const action = decide(
    playingState({
      aiHand: cards("4S", "5S", "6S", "7S", "8S", "JS"),
      lastPlay: cards("3D"),
      lastPlayerIndex: 0,
    }),
    "HARD",
  );
  const played = assertPlay(action, "有孤张可用时不应拆顺子响应");

  if (classify(played)?.type !== "SINGLE" || played[0].r !== 11) {
    throw new Error("应优先出顺子之外的 J");
  }
});

Deno.test("困难 AI 可以连续完成 100 局且不产生非法动作", () => {
  let seed = 20260815;
  const random = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 0x100000000;
  };

  for (let game = 0; game < 100; game++) {
    const engine = new DoudizhuEngine(
      { humanCount: 0, difficulty: "HARD" },
      random,
    );
    engine.startNewGame();
    let actions = 0;
    while (engine.state.phase !== "FINISHED" && actions < 1000) {
      const action = decide(engine.state, "HARD", random);
      if (action.type === "call") {
        engine.submitCall(action.want);
      } else if (action.type === "play") {
        engine.submitPlay(action.cards);
      } else {
        engine.submitPass();
      }
      actions++;
    }
    if (engine.state.phase !== "FINISHED") {
      throw new Error(`第 ${game + 1} 局在 1000 次动作内没有结束`);
    }
    if (
      engine.state.winnerIndex === null ||
      engine.state.seats[engine.state.winnerIndex].hand.length !== 0
    ) {
      throw new Error(`第 ${game + 1} 局结束状态无效`);
    }
  }
});
