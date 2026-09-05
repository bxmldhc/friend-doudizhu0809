import {
  createLoveLetterDeck,
  LoveLetterEngine,
  serializeLoveLetterState,
} from "./love_letter.ts";

Deno.test("情书使用经典 16 张牌组", () => {
  const deck = createLoveLetterDeck();
  if (deck.length !== 16) throw new Error("牌组数量错误");
  const counts = Object.fromEntries(deck.map((card) => [card.name, 0]));
  for (const card of deck) counts[card.name] += 1;
  if (counts.GUARD !== 5 || counts.PRINCESS !== 1 || counts.COUNTESS !== 1) {
    throw new Error("牌组配比错误");
  }
});

Deno.test("情书开局当前玩家摸牌，其他玩家保持一张", () => {
  const engine = new LoveLetterEngine(3);
  engine.startNewGame();
  const state = engine.state;
  if (state.players[state.turnIndex].hand.length !== 2) throw new Error("当前玩家没有两张手牌");
  if (state.players.filter((player) => player.seat !== state.turnIndex).some((player) => player.hand.length !== 1)) {
    throw new Error("非当前玩家手牌错误");
  }
  if (state.deck.length !== 11) throw new Error("牌堆数量错误");
});

Deno.test("情书序列化不会泄露对手手牌", () => {
  const engine = new LoveLetterEngine(2);
  engine.startNewGame();
  const state = serializeLoveLetterState(engine.state, engine.state.turnIndex);
  const players = state.players as Array<Record<string, unknown>>;
  const opponent = players.find((player) => player.seat !== engine.state.turnIndex)!;
  const hand = opponent.hand as Array<Record<string, unknown>>;
  if (hand.some((card) => card.value !== undefined || card.name !== undefined)) {
    throw new Error("对手手牌泄露");
  }
});

Deno.test("情书强制伯爵夫人规则生效", () => {
  const engine = new LoveLetterEngine(2);
  engine.startNewGame();
  const seat = engine.state.turnIndex;
  const player = engine.state.players[seat];
  player.hand = [
    { id: "countess-01", name: "COUNTESS", value: 7 },
    { id: "king-01", name: "KING", value: 6 },
  ];
  let rejected = false;
  try {
    engine.play(seat, { cardId: "king-01", targetSeat: (seat + 1) % 2 });
  } catch {
    rejected = true;
  }
  if (!rejected) throw new Error("未强制打出伯爵夫人");
});
