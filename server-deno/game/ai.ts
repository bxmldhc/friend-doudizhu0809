import { Card, removeCards } from "./cards.ts";
import { Difficulty, EngineState } from "./engine.ts";
import { allPlays, beats, classify, ComboType, Pattern } from "./hand.ts";

export type AiAction =
  | { type: "call"; want: boolean }
  | { type: "play"; cards: Card[] }
  | { type: "pass" };

const SEQUENCE_TYPES = new Set<ComboType>([
  "STRAIGHT",
  "PAIR_STRAIGHT",
  "AIRPLANE",
  "AIRPLANE_SINGLE",
  "AIRPLANE_PAIR",
]);

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
    const best = [...legal].sort(
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
  const hand = state.seats[state.turnIndex].hand;
  const strength = handStrength(hand);
  const hasCandidate = state.bidCandidateIndex !== null;
  const isFinalDecision = state.actedCount >= 3;
  const shouldCall = difficulty === "EASY"
    ? rand() < 0.22
    : difficulty === "MEDIUM"
    ? strength >= 25
    : hasCandidate
    ? strength >= (isFinalDecision ? 34 : 32)
    : strength >= 25;
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
  const landlord = state.landlordIndex;
  const landlordIsNext = landlord !== null &&
    landlord === nextSeatIndex(state.turnIndex);
  const landlordCount = landlord === null
    ? Number.POSITIVE_INFINITY
    : state.seats[landlord].hand.length;
  if (landlordIsNext && landlordCount <= 2) {
    const blockingLead = chooseDangerBlockingLead(
      state,
      hand,
      legal,
      landlordCount,
    );
    if (blockingLead) return { type: "play", cards: blockingLead };
  }

  const teammate = teammateIndex(state);
  if (teammate !== null) {
    const teammateCount = state.seats[teammate].hand.length;
    if (teammateCount === 1) {
      const single = lowestIntactPlay(hand, legal, "SINGLE");
      if (single) return { type: "play", cards: single };
    }
    if (teammateCount === 2) {
      const pair = lowestIntactPlay(hand, legal, "PAIR");
      if (pair) return { type: "play", cards: pair };
    }
  }

  if (hand.length <= 10) {
    const endgame = chooseEndgamePlay(state, hand, legal);
    if (endgame) return { type: "play", cards: endgame };
  }

  const sequences = legal.filter((play) =>
    SEQUENCE_TYPES.has(classify(play)?.type as ComboType)
  );
  if (sequences.length > 0) {
    const best = [...sequences].sort(
      (a, b) =>
        b.length - a.length ||
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b),
    )[0];
    return { type: "play", cards: best };
  }

  const groups = rankCounts(hand);
  if ([...groups.values()].filter((count) => count >= 2).length >= 2) {
    const pair = lowestIntactPlay(hand, legal, "PAIR");
    if (pair) return { type: "play", cards: pair };
  }

  const triple = legal
    .filter((play) => {
      const pattern = classify(play);
      return pattern !== null &&
        ["TRIPLE", "TRIPLE_SINGLE", "TRIPLE_PAIR"].includes(pattern.type) &&
        pattern.primary < 15;
    })
    .sort(
      (a, b) =>
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b) ||
        b.length - a.length,
    )[0];
  if (triple) return { type: "play", cards: triple };

  const singles = legal
    .filter((play) => classify(play)?.type === "SINGLE")
    .sort(
      (a, b) =>
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b),
    );
  return {
    type: "play",
    cards: singles[0] ?? leastCostPlay(state, hand, legal),
  };
}

function chooseResponse(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
  last: Card[],
): AiAction {
  const lastPattern = classify(last);
  if (!lastPattern) return { type: "pass" };

  const teammateLed = isTeammate(state, state.lastPlayerIndex);
  const danger = opponentDangerCount(state);

  const normal = legal.filter((play) => !isBomb(play));
  const bombs = legal.filter(isBomb).sort(
    (a, b) => primaryOf(a) - primaryOf(b),
  );

  if (
    danger === 1 &&
    state.lastPlayerIndex !== null &&
    !teammateLed &&
    lastPattern.type === "SINGLE"
  ) {
    const immediateBlock = normal
      .filter((play) => classify(play)?.type === "SINGLE")
      .sort((a, b) => primaryOf(a) - primaryOf(b))[0];
    if (immediateBlock) return { type: "play", cards: immediateBlock };
  }

  if (hand.length <= 10 && !teammateLed) {
    const endgame = chooseEndgamePlay(state, hand, normal);
    if (endgame) return { type: "play", cards: endgame };
  }

  if (teammateLed) {
    const blocker = chooseTeammateCover(
      state,
      hand,
      normal,
      lastPattern,
      danger,
    );
    if (blocker) return { type: "play", cards: blocker };
    if (bombs.length > 0 && danger === 1 && lastPattern.type === "SINGLE") {
      const landlord = state.landlordIndex;
      const lastRank = lastPattern.primary;
      const landlordMayBeat = landlord !== null &&
        unknownRemaining(state, lastRank + 1) > 0;
      if (landlordMayBeat && shouldBomb(state, hand, bombs)) {
        return { type: "play", cards: bombs[0] };
      }
    }
    return { type: "pass" };
  }

  if (lastPattern.type === "SINGLE") {
    return chooseSingleResponse(state, hand, normal, bombs, lastPattern);
  }
  if (lastPattern.type === "PAIR") {
    return choosePairResponse(state, hand, normal, bombs, lastPattern);
  }

  const best = leastCostPlay(state, hand, normal);
  if (best) return { type: "play", cards: best };
  if (shouldBomb(state, hand, bombs)) {
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
    .filter((play) => classify(play)?.type === "SINGLE")
    .sort(
      (a, b) =>
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b),
    );
  if (singles.length > 0) {
    const best = singles[0];
    const urgent = opponentDangerCount(state) <= 2;
    if (
      breakPenalty(hand, best) <= 1 ||
      hand.length <= 6 ||
      lastPattern.primary >= 14 ||
      urgent ||
      isControlSingle(state, best[0].r)
    ) {
      return { type: "play", cards: best };
    }
  }
  if (shouldBomb(state, hand, bombs)) {
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
  const pairs = normal
    .filter((play) => classify(play)?.type === "PAIR")
    .sort(
      (a, b) =>
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b),
    );
  if (pairs.length > 0) {
    const best = pairs[0];
    if (
      breakPenalty(hand, best) <= 1 ||
      hand.length <= 6 ||
      lastPattern.primary >= 13 ||
      opponentDangerCount(state) <= 2
    ) {
      return { type: "play", cards: best };
    }
  }
  if (shouldBomb(state, hand, bombs)) {
    return { type: "play", cards: bombs[0] };
  }
  return { type: "pass" };
}

function chooseEndgamePlay(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
): Card[] | null {
  if (legal.length === 0) return null;
  const urgent = opponentDangerCount(state) <= 2;
  const memo = new Map<string, number>();
  const budget = {
    nodes: 0,
    limit: hand.length <= 8 ? 7000 : 14000,
  };
  const scored = legal.map((play) => {
    const remaining = removeCards(hand, play);
    const turns = minTurns(remaining, memo, budget);
    const bombPenalty = isBomb(play) && !urgent && remaining.length > 0 ? 3 : 0;
    return {
      play,
      score: turns * 20 + bombPenalty * 20 + breakPenalty(hand, play) * 3 +
        controlCost(state, play),
    };
  });
  return scored.sort(
    (a, b) =>
      a.score - b.score ||
      b.play.length - a.play.length ||
      primaryOf(a.play) - primaryOf(b.play),
  )[0]?.play ?? null;
}

function minTurns(
  hand: Card[],
  memo: Map<string, number>,
  budget: { nodes: number; limit: number },
): number {
  if (hand.length === 0) return 0;
  if (budget.nodes++ > budget.limit) return hand.length;
  const key = hand.map((card) => `${card.r}${card.s}`).sort().join(",");
  const cached = memo.get(key);
  if (cached !== undefined) return cached;
  let best = hand.length;
  for (const play of allPlays(hand)) {
    best = Math.min(best, 1 + minTurns(removeCards(hand, play), memo, budget));
    if (best === 1) break;
  }
  memo.set(key, best);
  return best;
}

function lowestIntactPlay(
  hand: Card[],
  legal: Card[][],
  type: ComboType,
): Card[] | null {
  return legal
    .filter((play) => classify(play)?.type === type)
    .sort(
      (a, b) =>
        breakPenalty(hand, a) - breakPenalty(hand, b) ||
        primaryOf(a) - primaryOf(b),
    )[0] ?? null;
}

function leastCostPlay(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
): Card[] | null {
  return [...legal].sort(
    (a, b) =>
      playCost(state, hand, a) - playCost(state, hand, b) ||
      primaryOf(a) - primaryOf(b),
  )[0] ?? null;
}

function playCost(state: EngineState, hand: Card[], play: Card[]): number {
  const pattern = classify(play);
  if (!pattern) return 1000;
  return breakPenalty(hand, play) * 12 +
    sequenceBreakPenalty(hand, play) * 8 +
    controlCost(state, play) +
    (isBomb(play) ? 80 : 0) -
    play.length * 2;
}

function controlCost(state: EngineState, play: Card[]): number {
  const pattern = classify(play);
  if (!pattern) return 0;
  if (pattern.type === "SINGLE" && isControlSingle(state, pattern.primary)) {
    return opponentDangerCount(state) <= 2 ? -8 : 8;
  }
  return pattern.primary >= 15 ? 4 : 0;
}

function shouldBomb(
  state: EngineState,
  hand: Card[],
  bombs: Card[][],
): boolean {
  if (bombs.length === 0) return false;
  if (bombs.some((play) => play.length === hand.length)) return true;
  const danger = opponentDangerCount(state);
  if (danger <= 2) return true;
  if (hand.length <= 6) {
    return bombs.some((play) =>
      minTurns(removeCards(hand, play), new Map(), { nodes: 0, limit: 7000 }) <=
        1
    );
  }
  return false;
}

function teammateIndex(state: EngineState): number | null {
  const landlord = state.landlordIndex;
  if (landlord === null || state.turnIndex === landlord) return null;
  return [0, 1, 2].find((index) =>
    index !== landlord && index !== state.turnIndex
  ) ?? null;
}

function isTeammate(state: EngineState, index: number | null): boolean {
  return index !== null && teammateIndex(state) === index;
}

function opponentIndices(state: EngineState): number[] {
  const landlord = state.landlordIndex;
  if (landlord === null) {
    return [0, 1, 2].filter((index) => index !== state.turnIndex);
  }
  if (state.turnIndex === landlord) {
    return [0, 1, 2].filter((index) => index !== landlord);
  }
  return [landlord];
}

function opponentDangerCount(state: EngineState): number {
  return Math.min(
    ...opponentIndices(state).map((index) => state.seats[index].hand.length),
  );
}

function nextSeatIndex(index: number): number {
  return (index + 2) % 3;
}

function isControlSingle(state: EngineState, rank: number): boolean {
  for (let higher = rank + 1; higher <= 17; higher++) {
    if (unknownRemaining(state, higher) > 0) return false;
  }
  return true;
}

function unknownRemaining(state: EngineState, rank: number): number {
  const total = rank >= 16 ? 1 : 4;
  const seen = state.seenRanks[rank] ?? 0;
  const own =
    state.seats[state.turnIndex].hand.filter((card) => card.r === rank).length;
  return Math.max(0, total - seen - own);
}

function isBomb(cards: Card[]): boolean {
  const type = classify(cards)?.type;
  return type === "BOMB" || type === "ROCKET";
}

function breakPenalty(hand: Card[], cards: Card[]): number {
  const used = rankCounts(cards);
  const groups = rankCounts(hand);
  let penalty = 0;
  for (const [rank, count] of used) {
    const groupSize = groups.get(rank) ?? 0;
    if (groupSize === 4 && count < 4) penalty += 5;
    else if (groupSize === 3 && count < 3) penalty += 3;
    else if (groupSize === 2 && count < 2) penalty += 1;
  }
  return penalty;
}

function sequenceBreakPenalty(hand: Card[], cards: Card[]): number {
  const playedPattern = classify(cards);
  if (playedPattern && SEQUENCE_TYPES.has(playedPattern.type)) return 0;
  const before = rankCounts(hand);
  const after = rankCounts(removeCards(hand, cards));
  return sequenceCoverage(before) - sequenceCoverage(after);
}

function sequenceCoverage(counts: Map<number, number>): number {
  let coverage = 0;
  coverage += longestRun(counts, 1, 5);
  coverage += longestRun(counts, 2, 3) * 2;
  coverage += longestRun(counts, 3, 2) * 3;
  return coverage;
}

function longestRun(
  counts: Map<number, number>,
  requiredCount: number,
  minimumLength: number,
): number {
  let current = 0;
  let best = 0;
  for (let rank = 3; rank <= 14; rank++) {
    if ((counts.get(rank) ?? 0) >= requiredCount) {
      current++;
      best = Math.max(best, current);
    } else {
      current = 0;
    }
  }
  return best >= minimumLength ? best : 0;
}

function chooseDangerBlockingLead(
  state: EngineState,
  hand: Card[],
  legal: Card[][],
  landlordCount: number,
): Card[] | null {
  const nonBombs = legal.filter((play) => !isBomb(play));
  const matchingFinish = nonBombs.filter((play) =>
    play.length === landlordCount
  );
  const saferShapes = nonBombs.filter((play) => play.length > landlordCount);
  if (saferShapes.length > 0) {
    return leastCostPlay(state, hand, saferShapes);
  }
  if (landlordCount === 1) {
    const singles = matchingFinish
      .filter((play) => classify(play)?.type === "SINGLE")
      .sort((a, b) =>
        Number(isControlSingle(state, b[0].r)) -
          Number(isControlSingle(state, a[0].r)) ||
        primaryOf(b) - primaryOf(a)
      );
    return singles[0] ?? null;
  }
  return [...matchingFinish].sort((a, b) =>
    primaryOf(b) - primaryOf(a) ||
    playCost(state, hand, a) - playCost(state, hand, b)
  )[0] ?? null;
}

function chooseTeammateCover(
  state: EngineState,
  hand: Card[],
  normal: Card[][],
  lastPattern: Pattern,
  danger: number,
): Card[] | null {
  if (danger > 2 || lastPattern.length !== danger) return null;
  if (danger === 1 && lastPattern.type === "SINGLE") {
    const singles = normal
      .filter((play) => classify(play)?.type === "SINGLE")
      .sort((a, b) =>
        Number(isControlSingle(state, b[0].r)) -
          Number(isControlSingle(state, a[0].r)) ||
        primaryOf(b) - primaryOf(a) ||
        playCost(state, hand, a) - playCost(state, hand, b)
      );
    const control = singles.find((play) => isControlSingle(state, play[0].r));
    return control ?? null;
  }
  if (danger === 2 && lastPattern.type === "PAIR") {
    const pairs = normal
      .filter((play) => classify(play)?.type === "PAIR")
      .sort((a, b) => primaryOf(b) - primaryOf(a));
    const highest = pairs[0];
    if (!highest) return null;
    return isControlPair(state, highest[0].r) ? highest : null;
  }
  return null;
}

function isControlPair(state: EngineState, rank: number): boolean {
  for (let higher = rank + 1; higher <= 15; higher++) {
    if (unknownRemaining(state, higher) >= 2) return false;
  }
  return true;
}

function rankCounts(cards: Card[]): Map<number, number> {
  const counts = new Map<number, number>();
  for (const card of cards) {
    counts.set(card.r, (counts.get(card.r) ?? 0) + 1);
  }
  return counts;
}

function primaryOf(cards: Card[]): number {
  return classify(cards)?.primary ?? 3;
}

function handStrength(hand: Card[]): number {
  const high: Record<number, number> = {
    11: 1,
    12: 2,
    13: 3,
    14: 4,
    15: 6,
    16: 7,
    17: 8,
  };
  let score = hand.reduce((sum, card) => sum + (high[card.r] ?? 0), 0);
  const counts = rankCounts(hand);
  score += [...counts.values()].filter((count) => count === 4).length * 10;
  score += [...counts.values()].filter((count) => count === 3).length * 2;
  if (counts.has(16) && counts.has(17)) score += 5;
  const longPlay = allPlays(hand).reduce(
    (max, play) => isBomb(play) ? max : Math.max(max, play.length),
    0,
  );
  score += Math.max(0, longPlay - 5);
  score -= [...counts.values()].filter((count) => count === 1).length;
  const sequenceValue = sequenceCoverage(counts);
  score += Math.min(8, sequenceValue);
  const lowLooseCards =
    [...counts.entries()].filter(([rank, count]) => rank < 11 && count === 1)
      .length;
  score -= Math.max(0, lowLooseCards - 3);
  return score;
}
