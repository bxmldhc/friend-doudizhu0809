import { Card, cardNotation, parseCards, sortCards, containsAll } from "./cards.ts";

export type ComboType =
  | "SINGLE"
  | "PAIR"
  | "TRIPLE"
  | "TRIPLE_SINGLE"
  | "TRIPLE_PAIR"
  | "STRAIGHT"
  | "PAIR_STRAIGHT"
  | "AIRPLANE"
  | "AIRPLANE_SINGLE"
  | "AIRPLANE_PAIR"
  | "FOUR_TWO_SINGLE"
  | "FOUR_TWO_PAIR"
  | "BOMB"
  | "ROCKET";

export interface Pattern {
  type: ComboType;
  primary: number;
  length: number;
  cards: Card[];
}

export function classify(cards: Card[]): Pattern | null {
  const sorted = sortCards(cards);
  if (sorted.length === 0) return null;

  const groups = new Map<number, Card[]>();
  for (const c of sorted) {
    const list = groups.get(c.r) ?? [];
    list.push(c);
    groups.set(c.r, list);
  }
  const ranks = [...groups.keys()].sort((a, b) => a - b);
  const groupList = [...groups.values()];
  const sizes = groupList.map((g) => g.length);

  if (sorted.length === 2 && ranks.includes(16) && ranks.includes(17)) {
    return { type: "ROCKET", primary: 17, length: 1, cards: sorted };
  }
  if (groupList.length === 1) {
    const rank = ranks[0];
    const count = groupList[0].length;
    const type = count === 1 ? "SINGLE" : count === 2 ? "PAIR" : count === 3 ? "TRIPLE" : count === 4 ? "BOMB" : null;
    return type ? { type, primary: rank, length: 1, cards: sorted } : null;
  }
  if (groupList.length === 2) {
    const four = groupList.find((g) => g.length === 4);
    if (four) {
      const other = groupList.find((g) => g.length !== 4);
      if (!other) return null;
      return other.length === 1
        ? { type: "FOUR_TWO_SINGLE", primary: four[0].r, length: 1, cards: sorted }
        : other.length === 2
        ? { type: "FOUR_TWO_PAIR", primary: four[0].r, length: 1, cards: sorted }
        : null;
    }
    const triple = groupList.find((g) => g.length === 3);
    if (triple) {
      const other = groupList.find((g) => g.length !== 3);
      if (!other) return null;
      return other.length === 1
        ? { type: "TRIPLE_SINGLE", primary: triple[0].r, length: 1, cards: sorted }
        : other.length === 2
        ? { type: "TRIPLE_PAIR", primary: triple[0].r, length: 1, cards: sorted }
        : null;
    }
  }

  const maxRank = Math.max(...ranks);
  if (sizes.every((s) => s === 1) && ranks.length >= 5 && maxRank < 15 && isConsecutive(ranks)) {
    return { type: "STRAIGHT", primary: ranks[0], length: ranks.length, cards: sorted };
  }
  if (sizes.every((s) => s === 2) && ranks.length >= 3 && maxRank < 15 && isConsecutive(ranks)) {
    return { type: "PAIR_STRAIGHT", primary: ranks[0], length: ranks.length, cards: sorted };
  }

  const tripleGroups = groupList.filter((g) => g.length === 3);
  const tripleRanks = tripleGroups.map((g) => g[0].r).sort((a, b) => a - b);
  if (tripleGroups.length === groupList.length && tripleRanks.length >= 2 && tripleRanks[tripleRanks.length - 1] < 15 && isConsecutive(tripleRanks)) {
    return { type: "AIRPLANE", primary: tripleRanks[0], length: tripleRanks.length, cards: sorted };
  }

  const fourGroups = groupList.filter((g) => g.length === 4);
  if (fourGroups.length === 1 && groupList.length === 3 && groupList.filter((g) => g.length === 1).length === 2) {
    return { type: "FOUR_TWO_SINGLE", primary: fourGroups[0][0].r, length: 1, cards: sorted };
  }
  if (fourGroups.length === 1 && groupList.length === 3 && groupList.filter((g) => g.length === 2).length === 2) {
    return { type: "FOUR_TWO_PAIR", primary: fourGroups[0][0].r, length: 1, cards: sorted };
  }
  if (tripleRanks.length >= 2 && tripleRanks[tripleRanks.length - 1] < 15 && isConsecutive(tripleRanks)) {
    if (tripleRanks.length === groupList.filter((g) => g.length === 1).length) {
      return { type: "AIRPLANE_SINGLE", primary: tripleRanks[0], length: tripleRanks.length, cards: sorted };
    }
    if (tripleRanks.length === groupList.filter((g) => g.length === 2).length) {
      return { type: "AIRPLANE_PAIR", primary: tripleRanks[0], length: tripleRanks.length, cards: sorted };
    }
  }
  return null;
}

export function canBeat(pattern: Pattern, last: Pattern): boolean {
  if (pattern.type === "ROCKET") return last.type !== "ROCKET";
  if (last.type === "ROCKET") return false;
  if (pattern.type === "BOMB" && last.type !== "BOMB") return true;
  if (pattern.type === "BOMB" && last.type === "BOMB") return pattern.primary > last.primary;
  return pattern.type === last.type && pattern.length === last.length && pattern.primary > last.primary;
}

export function validatePlay(hand: Card[], lastPlay: Card[], isLeading: boolean, cards: Card[]): string | null {
  if (cards.length === 0) return "请选择要出的牌";
  if (!containsAll(hand, cards)) return "只能出自己手牌中的牌";
  const pattern = classify(cards);
  if (!pattern) return "不是合法牌型";
  if (!isLeading) {
    const lastPattern = classify(lastPlay);
    if (!lastPattern) return "上一手牌型无效";
    if (!canBeat(pattern, lastPattern)) return "管不上上一手";
  }
  return null;
}

export function allPlays(hand: Card[]): Card[][] {
  const cards = sortCards(hand);
  if (cards.length === 0) return [];
  const groups = new Map<number, Card[]>();
  for (const c of cards) {
    const list = groups.get(c.r) ?? [];
    list.push(c);
    groups.set(c.r, list);
  }
  const result = new Set<string>();
  const out: Card[][] = [];
  const add = (play: Card[]) => {
    const key = play.map(cardNotation).join(",");
    if (!result.has(key)) {
      result.add(key);
      out.push(sortCards(play));
    }
  };

  for (const list of groups.values()) {
    for (let n = 1; n <= Math.min(4, list.length); n++) add(list.slice(0, n));
  }
  const small = cards.find((c) => c.r === 16);
  const big = cards.find((c) => c.r === 17);
  if (small && big) add([small, big]);

  const straightRanks = [...groups.keys()].filter((r) => r < 15).sort((a, b) => a - b);
  for (let len = 5; len <= straightRanks.length; len++) {
    for (let start = 0; start <= straightRanks.length - len; start++) {
      const run = straightRanks.slice(start, start + len);
      if (isConsecutive(run)) add(run.map((r) => groups.get(r)![0]));
    }
  }
  const pairRanks = [...groups.entries()].filter(([, g]) => g.length >= 2).map(([r]) => r).filter((r) => r < 15).sort((a, b) => a - b);
  for (let len = 3; len <= pairRanks.length; len++) {
    for (let start = 0; start <= pairRanks.length - len; start++) {
      const run = pairRanks.slice(start, start + len);
      if (isConsecutive(run)) add(run.flatMap((r) => groups.get(r)!.slice(0, 2)));
    }
  }

  const tripleRanks = [...groups.entries()].filter(([, g]) => g.length >= 3).map(([r]) => r).filter((r) => r < 15).sort((a, b) => a - b);
  for (let len = 2; len <= tripleRanks.length; len++) {
    for (let start = 0; start <= tripleRanks.length - len; start++) {
      const run = tripleRanks.slice(start, start + len);
      if (!isConsecutive(run)) continue;
      const tripleCards = run.flatMap((r) => groups.get(r)!.slice(0, 3));
      add(tripleCards);
      const excluded = new Set(run);
      const singleWings = pickWings(groups, excluded, len, false);
      if (singleWings) add([...tripleCards, ...singleWings]);
      const pairWings = pickWings(groups, excluded, len, true);
      if (pairWings) add([...tripleCards, ...pairWings]);
    }
  }

  for (const [rank, list] of groups) {
    if (list.length >= 3) {
      const triple = list.slice(0, 3);
      const other = [...groups.entries()].filter(([r]) => r !== rank).sort((a, b) => a[0] - b[0]);
      const single = other.find(([, g]) => g.length >= 1)?.[1]?.[0];
      if (single) add([...triple, single]);
      const pair = other.find(([, g]) => g.length >= 2)?.[1]?.slice(0, 2);
      if (pair) add([...triple, ...pair]);
    }
    if (list.length >= 4) {
      const bomb = list.slice(0, 4);
      const other = [...groups.entries()].filter(([r]) => r !== rank).sort((a, b) => a[0] - b[0]);
      const singles = other.slice(0, 2).flatMap(([, g]) => g.slice(0, 1));
      if (singles.length === 2) add([...bomb, ...singles]);
      const pairs = other.filter(([, g]) => g.length >= 2).slice(0, 2).flatMap(([, g]) => g.slice(0, 2));
      if (pairs.length === 4) add([...bomb, ...pairs]);
    }
  }

  return out.sort((a, b) => a.length - b.length || (classify(a)?.primary ?? 0) - (classify(b)?.primary ?? 0));
}

export function beats(hand: Card[], last: Card[]): Card[][] {
  const lastPattern = classify(last);
  if (!lastPattern) return [];
  return allPlays(hand).filter((play) => {
    const pattern = classify(play);
    return pattern !== null && canBeat(pattern, lastPattern);
  });
}

export function hint(hand: Card[], last: Card[]): Card[] | null {
  const plays = last.length === 0 ? allPlays(hand) : beats(hand, last);
  if (plays.length === 0) return null;
  return plays.sort((a, b) => a.length - b.length || (classify(a)?.primary ?? 0) - (classify(b)?.primary ?? 0))[0];
}

function isConsecutive(ranks: number[]): boolean {
  for (let i = 1; i < ranks.length; i++) {
    if (ranks[i] !== ranks[i - 1] + 1) return false;
  }
  return true;
}

function pickWings(
  groups: Map<number, Card[]>,
  excluded: Set<number>,
  count: number,
  pair: boolean
): Card[] | null {
  const eligible = [...groups.entries()]
    .filter(([r]) => !excluded.has(r) && r !== 16 && r !== 17)
    .filter(([, g]) => g.length >= (pair ? 2 : 1))
    .sort((a, b) => a[0] - b[0])
    .slice(0, count);
  if (eligible.length < count) return null;
  return eligible.flatMap(([, g]) => g.slice(0, pair ? 2 : 1));
}
