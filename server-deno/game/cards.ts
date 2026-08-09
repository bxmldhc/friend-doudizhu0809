export type Suit = "S" | "H" | "C" | "D" | "J";

export interface Card {
  r: number;
  s: Suit;
}

const RANK_LABELS: Record<number, string> = {
  3: "3",
  4: "4",
  5: "5",
  6: "6",
  7: "7",
  8: "8",
  9: "9",
  10: "10",
  11: "J",
  12: "Q",
  13: "K",
  14: "A",
  15: "2",
  16: "小王",
  17: "大王",
};

export function rankLabel(r: number): string {
  return RANK_LABELS[r] ?? String(r);
}

export function rankFromLabel(label: string): number {
  switch (label.toUpperCase()) {
    case "3": return 3;
    case "4": return 4;
    case "5": return 5;
    case "6": return 6;
    case "7": return 7;
    case "8": return 8;
    case "9": return 9;
    case "10":
    case "T": return 10;
    case "J": return 11;
    case "Q": return 12;
    case "K": return 13;
    case "A": return 14;
    case "2": return 15;
    default: throw new Error(`未知牌面: ${label}`);
  }
}

export function parseCard(notation: string): Card {
  if (notation === "X") return { r: 16, s: "J" };
  if (notation === "B") return { r: 17, s: "J" };
  const rankPart = notation.slice(0, -1);
  const suitPart = notation.slice(-1) as Suit;
  return { r: rankFromLabel(rankPart), s: suitPart };
}

export function parseCards(notations: string[]): Card[] {
  return notations.map(parseCard);
}

export function cardNotation(card: Card): string {
  if (card.r === 16) return "X";
  if (card.r === 17) return "B";
  return `${rankLabel(card.r)}${card.s}`;
}

export function sortCards(cards: Card[]): Card[] {
  return [...cards].sort((a, b) => a.r - b.r || a.s.localeCompare(b.s));
}

export function sameCard(a: Card, b: Card): boolean {
  return a.r === b.r && a.s === b.s;
}

export function containsAll(hand: Card[], wanted: Card[]): boolean {
  const handCounts = new Map<number, number>();
  const wantedCounts = new Map<number, number>();
  for (const c of hand) handCounts.set(c.r, (handCounts.get(c.r) ?? 0) + 1);
  for (const c of wanted) wantedCounts.set(c.r, (wantedCounts.get(c.r) ?? 0) + 1);
  for (const [r, count] of wantedCounts) {
    if ((handCounts.get(r) ?? 0) < count) return false;
  }
  return true;
}

export function removeCards(hand: Card[], cards: Card[]): Card[] {
  const remaining = [...hand];
  for (const card of cards) {
    const index = remaining.findIndex((x) => sameCard(x, card));
    if (index < 0) throw new Error("手牌中不存在这张牌");
    remaining.splice(index, 1);
  }
  return remaining;
}

export const DECK: Card[] = [
  ...["S", "H", "C", "D"].flatMap((suit) =>
    [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15].map((r) => ({
      r,
      s: suit as Suit,
    }))
  ),
  { r: 16, s: "J" },
  { r: 17, s: "J" },
];
