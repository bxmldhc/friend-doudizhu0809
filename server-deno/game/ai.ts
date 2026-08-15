import { Card } from "./cards.ts";
import { Difficulty, EngineState } from "./engine.ts";
import { allPlays, beats, classify, ComboType, Pattern } from "./hand.ts";

export type AiAction =
  | { type: "call"; want: boolean }
  | { type: "play"; cards: Card[] }
  | { type: "pass" };

export function decide(
  state: EngineState,
  difficulty: Difficulty,
  rand: () => number = Math.random,
): AiAction {
  if (state.phase === "BIDDING") {
    return biddingDecision(state, difficulty, rand);
  }
  if (state.phase !== "PLAYING") return { type: "pass" };

  const hand = state.seats[state.turnIndex].hand;
  const legal = state.lastPlay.length === 0
    ? allPlays(hand)
    : beats(hand, state.lastPlay);
  if (legal.length === 0) return { type: "pass" };

  if (difficulty === "EASY") {
    return { type: "play", cards: legal[Math.floor(rand() * legal.length)] };
  }
  if (difficulty === "MEDIUM") {
    const best = legal.sort(
      (a, b) => a.length - b.length || primaryOf(a) - primaryOf(b),
    )[0];
    return { type: "play", cards: best };
  }
  return hardDecision(state, hand, legal);
}

function biddingDecision(
  state: EngineState,
  difficulty: Difficulty,
  rand: () => number,
): AiAction {
  const strength = handStrength(state.seats[state.turnIndex].hand);
  const hasCandidate = state.bidCandidateIndex !== null;
  const shouldCall = difficulty === "EASY"
    ? rand() < 0.22
    : difficulty === "MEDIUM"
    ? strength >= 24
    : hasCandidate
    ? strength >= 28
    : strength >= 22;
  return { type: "call", want: shouldCall };
}

function hardDecision(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
): AiAction {
  const finish = legal.find((play) => play.length === hand.length);
  if (finish) return { type: "play", cards: finish };

  if (state.lastPlay.length === 0) return chooseLeading(state, hand, legal);
  return chooseResponse(state, hand, legal, state.lastPlay);
}

function chooseLeading(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
): AiAction {
  const sequenceTypes = new Set([
    "STRAIGHT",
    "PAIR_STRAIGHT",
    "AIRPLANE",
    "AIRPLANE_SINGLE",
    "AIRPLANE_PAIR",
  ]);
  const sequences = legal.filter((play) =>
    sequenceTypes.has(classify(play)?.type ?? "")
  );
  if (sequences.length > 0) {
    const best = sequences.sort(
      (a, b) => b.length - a.length || primaryOf(a) - primaryOf(b),
    )[0];
    return { type: "play", cards: best };
  }

  const groups = new Map<number, number>();
  for (const c of hand) groups.set(c.r, (groups.get(c.r) ?? 0) + 1);
  const pairCount = [...groups.values()].filter((n) => n >= 2).length;
  const tripleCount = [...groups.values()].filter((n) => n >= 3).length;
  const singleCount = [...groups.values()].filter((n) => n === 1).length;

  if (pairCount >= 2) {
    const pair = legal
      .filter((p) => classify(p)?.type === "PAIR")
      .sort((a, b) => primaryOf(a) - primaryOf(b))[0];
    if (pair) return { type: "play", cards: pair };
  }
  if (tripleCount >= 2) {
    const triple = legal
      .filter((p) => {
        const pattern = classify(p);
        return pattern !== null &&
          ["TRIPLE", "TRIPLE_SINGLE", "TRIPLE_PAIR"].includes(pattern.type) &&
          pattern.primary < 15;
      })
      .sort((a, b) => primaryOf(a) - primaryOf(b))[0];
    if (triple) return { type: "play", cards: triple };
  }

  const single = legal
    .filter((p) => classify(p)?.type === "SINGLE")
    .sort((a, b) => primaryOf(a) - primaryOf(b))[0];
  return { type: "play", cards: single ?? legal[0] };
}

function chooseResponse(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
  last: Card[],
): AiAction {
  const lastPattern = classify(last);
  if (!lastPattern) return { type: "pass" };
  const landlordIndex = state.landlordIndex;
  const lastPlayerIndex = state.lastPlayerIndex;
  const teammateLed = landlordIndex !== null &&
    state.turnIndex !== landlordIndex &&
    lastPlayerIndex !== null &&
    lastPlayerIndex !== landlordIndex &&
    lastPlayerIndex !== state.turnIndex;
  const landlordDanger = landlordIndex !== null &&
    state.seats[landlordIndex].hand.length >= 1 &&
    state.seats[landlordIndex].hand.length <= 3;

  if (teammateLed && !landlordDanger) {
    return { type: "pass" };
  }

  const normal = legal.filter((p) => {
    const type = classify(p)?.type;
    return type !== "BOMB" && type !== "ROCKET";
  });
  const bombs = legal.filter((p) => {
    const type = classify(p)?.type;
    return type === "BOMB" || type === "ROCKET";
  });

  if (lastPattern.type === "SINGLE") {
    return chooseSingleResponse(state, hand, normal, bombs, lastPattern);
  }
  if (lastPattern.type === "PAIR") {
    return choosePairResponse(state, hand, normal, bombs, lastPattern);
  }
  const candidates = normal.map((play) => ({
    play,
    score: play.length + breakPenalty(hand, play),
  }));
  const best = candidates.sort(
    (a, b) => a.score - b.score || primaryOf(a.play) - primaryOf(b.play),
  )[0];
  if (best) return { type: "play", cards: best.play };
  if (bombs.length > 0 && (hand.length <= 8 || minOpponentCount(state) <= 5)) {
    return { type: "play", cards: bombs[0] };
  }
  return { type: "pass" };
}

function chooseSingleResponse(
  state: EngineState,
  hand: Card[],
  normal: Card[][],
  bombs: Card[][],
  lastPattern: Pattern,
): AiAction {
  const singles = normal
    .filter((p) => classify(p)?.type === "SINGLE")
    .sort((a, b) => primaryOf(a) - primaryOf(b));
  if (singles.length > 0) {
    const low = singles
      .filter((p) => primaryOf(p) < 15)
      .sort(
        (a, b) =>
          primaryOf(a) - primaryOf(b) ||
          breakPenalty(hand, a) - breakPenalty(hand, b),
      )[0];
    if (low) {
      const penalty = breakPenalty(hand, low);
      if (
        penalty <= 1 ||
        hand.length <= 6 ||
        lastPattern.primary >= 14 ||
        minOpponentCount(state) <= 5
      ) {
        return { type: "play", cards: low };
      }
      return { type: "pass" };
    }
    const lowest = singles[0];
    if (hand.length <= 5 || lastPattern.primary >= 14) {
      return { type: "play", cards: lowest };
    }
    return { type: "pass" };
  }
  if (bombs.length > 0 && (hand.length <= 8 || minOpponentCount(state) <= 4)) {
    return { type: "play", cards: bombs[0] };
  }
  return { type: "pass" };
}

function choosePairResponse(
  state: EngineState,
  hand: Card[],
  normal: Card[][],
  bombs: Card[][],
  lastPattern: Pattern,
): AiAction {
  const pairs = normal.filter((p) => classify(p)?.type === "PAIR");
  if (pairs.length > 0) {
    const best = pairs.sort(
      (a, b) =>
        primaryOf(a) - primaryOf(b) ||
        breakPenalty(hand, a) - breakPenalty(hand, b),
    )[0];
    const penalty = breakPenalty(hand, best);
    if (
      penalty <= 1 ||
      hand.length <= 6 ||
      lastPattern.primary >= 13 ||
      minOpponentCount(state) <= 5
    ) {
      return { type: "play", cards: best };
    }
    return { type: "pass" };
  }
  if (bombs.length > 0 && (hand.length <= 8 || minOpponentCount(state) <= 4)) {
    return { type: "play", cards: bombs[0] };
  }
  return { type: "pass" };
}

function minOpponentCount(state: EngineState): number {
  const t = state.turnIndex;
  return Math.min(
    state.seats[(t + 1) % 3].hand.length,
    state.seats[(t + 2) % 3].hand.length,
  );
}

function breakPenalty(hand: Card[], cards: Card[]): number {
  const pattern = classify(cards);
  if (!pattern) return 0;
  const groupSize = hand.filter((c) => c.r === pattern.primary).length;
  const used = cards.filter((c) => c.r === pattern.primary).length;
  if (groupSize >= 4 && used < 4) return 4;
  if (groupSize === 3 && used < 3) return 3;
  if (groupSize === 2 && used < 2) return 1;
  return 0;
}

function primaryOf(cards: Card[]): number {
  return classify(cards)?.primary ?? 3;
}

function handStrength(hand: Card[]): number {
  const high: Record<number, number> = {
    15: 6,
    14: 4,
    13: 3,
    12: 2,
    11: 1,
    16: 7,
    17: 8,
  };
  let score = hand.reduce((sum, c) => sum + (high[c.r] ?? 0), 0);
  const counts = new Map<number, number>();
  for (const c of hand) counts.set(c.r, (counts.get(c.r) ?? 0) + 1);
  score += [...counts.values()].filter((n) => n === 4).length * 9;
  score += [...counts.values()].filter((n) => n === 3).length * 2;
  return score;
}
