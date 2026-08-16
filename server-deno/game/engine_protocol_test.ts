import { DoudizhuEngine, EngineState } from "./engine.ts";
import { Card } from "./cards.ts";
import { deserializeState, serializeState } from "./protocol.ts";

function card(r: number, s: Card["s"] = "S"): Card {
  return { r, s };
}

function playingState(): EngineState {
  return {
    phase: "PLAYING",
    seats: [0, 1, 2].map((index) => ({
      index,
      name: `玩家${index + 1}`,
      isHuman: true,
      isAway: false,
      hand: index === 0
        ? [card(3), card(4)]
        : index === 1
        ? [card(7), card(8)]
        : [card(5), card(6)],
      isLandlord: index === 0,
      score: 0,
    })),
    turnIndex: 0,
    landlordIndex: 0,
    bidStartIndex: 0,
    bidCandidateIndex: null,
    bidCount: 1,
    bidMultiplier: 1,
    actedCount: 0,
    lastPlay: [],
    lastPlayerIndex: null,
    lastPlaysBySeat: [[], [], []],
    lastActionsBySeat: [null, null, null],
    passes: 0,
    bottomCards: [card(7), card(8), card(9)],
    multiplier: 1,
    seenRanks: {},
    winnerIndex: null,
    scoreDelta: [],
    message: "",
    gameNumber: 1,
  };
}

function biddingState(): EngineState {
  return {
    ...playingState(),
    phase: "BIDDING",
    seats: playingState().seats.map((seat) => ({
      ...seat,
      isLandlord: false,
      hand: [card(3), card(4)],
    })),
    turnIndex: 0,
    landlordIndex: null,
    bidStartIndex: 0,
    bidCandidateIndex: null,
    bidCount: 0,
    bidMultiplier: 1,
    actedCount: 0,
    bottomCards: [card(7), card(8), card(9)],
    message: "等待叫地主",
  };
}

Deno.test("首次发牌显示为第 1 局", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.startNewGame();

  if (engine.state.gameNumber !== 1) {
    throw new Error(`首次发牌局数应为 1，实际为 ${engine.state.gameNumber}`);
  }
});

Deno.test("出牌按座位写入本轮独立出牌区，并保留兼容字段", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = playingState();

  engine.submitPlay([card(3)]);

  if (engine.state.lastPlayerIndex !== 0) {
    throw new Error("lastPlayerIndex 未记录出牌座位");
  }
  if (engine.state.lastPlay.length !== 1 || engine.state.lastPlay[0].r !== 3) {
    throw new Error("lastPlay 未保留上一手牌");
  }
  if (engine.state.lastPlaysBySeat[0][0].r !== 3) {
    throw new Error("0 号座位没有记录出牌");
  }
  if (
    engine.state.lastPlaysBySeat[1].length !== 0 ||
    engine.state.lastPlaysBySeat[2].length !== 0
  ) {
    throw new Error("其他座位不应出现未出的牌");
  }
});

Deno.test("submitPlay 返回服务器确认的权威牌型", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = playingState();

  const pattern = engine.submitPlay([card(3)]);

  if (
    pattern.type !== "SINGLE" || pattern.primary !== 3 ||
    pattern.length !== 1
  ) {
    throw new Error("submitPlay 未返回准确的服务器牌型");
  }
});

Deno.test("公开记牌只累计服务器确认出过的牌", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = playingState();

  engine.submitPlay([card(3)]);
  engine.submitPlay([card(5)]);

  if (engine.state.seenRanks[3] !== 1 || engine.state.seenRanks[5] !== 1) {
    throw new Error("已确认出牌没有正确累计到公开记牌");
  }
  if (engine.state.seenRanks[4] !== undefined) {
    throw new Error("公开记牌不应包含仍在手牌中的隐藏牌");
  }
});

Deno.test("协议往返保留公开记牌并兼容旧快照", () => {
  const state = playingState();
  state.seenRanks = { 3: 2, 15: 1, 17: 1 };
  const serialized = serializeState(state, 0);
  const decoded = deserializeState(serialized);
  if (
    decoded.seenRanks[3] !== 2 ||
    decoded.seenRanks[15] !== 1 ||
    decoded.seenRanks[17] !== 1
  ) {
    throw new Error("公开记牌协议往返失败");
  }

  delete serialized.seenRanks;
  if (Object.keys(deserializeState(serialized).seenRanks).length !== 0) {
    throw new Error("旧快照缺少公开记牌时应兼容为空");
  }
});

Deno.test("协议仅在牌局结束后公开三家剩余手牌", () => {
  const playing = playingState();
  const playingSeats = serializeState(playing, 0).seats as Record<
    string,
    unknown
  >[];
  if (
    (playingSeats[0].hand as unknown[]).length !== 2 ||
    (playingSeats[1].hand as unknown[]).length !== 0 ||
    (playingSeats[2].hand as unknown[]).length !== 0
  ) {
    throw new Error("进行中必须只向当前玩家公开自己的手牌");
  }

  const finished: EngineState = {
    ...playing,
    phase: "FINISHED",
    winnerIndex: 0,
    scoreDelta: [2, -1, -1],
  };
  const finishedSeats = serializeState(finished, null).seats as Record<
    string,
    unknown
  >[];
  if (
    finishedSeats.some((seat) => (seat.hand as unknown[]).length !== 2)
  ) {
    throw new Error("牌局结束后应向所有客户端公开三家剩余手牌");
  }
});

Deno.test("两次 PASS 只清空裁判上一手，保留三家本轮视觉出牌", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = playingState();

  engine.submitPlay([card(3)]);
  engine.submitPlay([card(5)]);
  engine.submitPlay([card(7)]);

  engine.submitPass();
  if (
    engine.state.lastPlaysBySeat.some((play) => play.length !== 1) ||
    engine.state.lastPlay.length !== 1
  ) {
    throw new Error("第一次 PASS 不应破坏本轮三家视觉出牌");
  }

  engine.submitPass();
  const stateAfterNewRound = engine.state;
  if (
    stateAfterNewRound.lastPlay.length !== 0 ||
    stateAfterNewRound.lastPlayerIndex !== null
  ) {
    throw new Error("两次 PASS 后兼容字段未开启新轮");
  }
  if (stateAfterNewRound.lastPlaysBySeat.some((play) => play.length !== 1)) {
    throw new Error("两次 PASS 不应清空三家本轮视觉出牌");
  }
});

Deno.test("协议缺少 lastPlaysBySeat 时按旧状态兼容为三个空出牌区", () => {
  const state = playingState();
  const serialized = serializeState(state, 0);
  delete serialized.lastPlaysBySeat;
  delete (serialized.seats as Record<string, unknown>[])[1].isAway;

  const decoded = deserializeState(serialized);
  if (
    decoded.lastPlaysBySeat.length !== 3 ||
    decoded.lastPlaysBySeat.some((play) => play.length !== 0)
  ) {
    throw new Error("缺少字段时没有生成三个空出牌区");
  }
  if (decoded.lastPlay.length !== 0 || decoded.seats[0].hand.length !== 2) {
    throw new Error("旧协议状态的兼容字段被破坏");
  }
  if (decoded.seats.some((seat) => seat.isAway)) {
    throw new Error("旧协议缺少 isAway 时应默认全部为 false");
  }
});

Deno.test("协议序列化保留三个座位的出牌记录", () => {
  const state = playingState();
  state.seats[1] = { ...state.seats[1], isHuman: false, isAway: true };
  state.lastPlaysBySeat = [[card(10, "H")], [], [card(12, "D"), card(12, "C")]];
  const serialized = serializeState(state, 0);
  const decoded = deserializeState(serialized);

  if (
    decoded.lastPlaysBySeat[0][0].r !== 10 ||
    decoded.lastPlaysBySeat[0][0].s !== "H"
  ) {
    throw new Error("0 号座位出牌记录序列化失败");
  }
  if (
    decoded.lastPlaysBySeat[2].length !== 2 ||
    decoded.lastPlaysBySeat[2][1].s !== "C"
  ) {
    throw new Error("2 号座位出牌记录序列化失败");
  }
  if (decoded.seats[1].isHuman || !decoded.seats[1].isAway) {
    throw new Error("isHuman/isAway 没有正确序列化");
  }
});

Deno.test("出牌顺序为逆时针 0 -> 2 -> 1 -> 0", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = playingState();

  engine.submitPlay([card(3)]);
  if (engine.state.turnIndex !== 2) throw new Error("0 号出牌后没有轮到 2 号");
  engine.submitPlay([card(5)]);
  const nextTurn = Number(engine.state.turnIndex);
  if (nextTurn !== 1) throw new Error("2 号出牌后没有轮到 1 号");
  engine.submitPlay([card(7)]);
  const finalTurn = Number(engine.state.turnIndex);
  if (finalTurn !== 0) throw new Error("1 号出牌后没有轮到 0 号");
});

Deno.test("抢地主：起始玩家抢，另外两人都不抢，起始玩家直接成为地主", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = biddingState();

  engine.submitCall(true);
  engine.submitCall(false);
  engine.submitCall(false);

  if (engine.state.phase !== "PLAYING" || engine.state.landlordIndex !== 0) {
    throw new Error("起始玩家抢后无人挑战时没有确认地主");
  }
});

Deno.test("抢地主：其他玩家抢后，选择权回到起始玩家", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = biddingState();

  engine.submitCall(false);
  engine.submitCall(true);
  engine.submitCall(false);

  if (engine.state.phase !== "BIDDING" || engine.state.turnIndex !== 0) {
    throw new Error("其他玩家抢地主后没有回到起始玩家");
  }
  engine.submitCall(false);
  if (engine.state.landlordIndex !== 2) {
    throw new Error("起始玩家不抢时没有保留最后的抢地主者");
  }
});

Deno.test("抢地主：起始玩家可以在最终回合重新抢回地主", () => {
  const engine = new DoudizhuEngine({ humanCount: 3, difficulty: "MEDIUM" });
  engine.state = biddingState();

  engine.submitCall(true);
  engine.submitCall(true);
  engine.submitCall(false);
  engine.submitCall(true);

  if (engine.state.phase !== "PLAYING" || engine.state.landlordIndex !== 0) {
    throw new Error("起始玩家最终抢回地主失败");
  }
});
