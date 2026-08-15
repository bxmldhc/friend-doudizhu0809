import { Card, containsAll, DECK, removeCards, sortCards } from "./cards.ts";
import {
  classify,
  ComboType,
  Pattern,
  validatePlay as validatePlayCards,
} from "./hand.ts";
import { hint } from "./hand.ts";

export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type Phase = "BIDDING" | "PLAYING" | "FINISHED";
export type SeatAction =
  | "PLAY"
  | "PASS"
  | "CALL"
  | "NOT_CALL"
  | "ROB"
  | "NOT_ROB";

export interface GameConfig {
  humanCount: number;
  difficulty: Difficulty;
}

export interface SeatState {
  index: number;
  name: string;
  isHuman: boolean;
  isAway: boolean;
  hand: Card[];
  isLandlord: boolean;
  score: number;
}

export interface EngineState {
  phase: Phase;
  seats: SeatState[];
  turnIndex: number;
  landlordIndex: number | null;
  bidStartIndex: number;
  bidCandidateIndex: number | null;
  bidCount: number;
  bidMultiplier: number;
  actedCount: number;
  lastPlay: Card[];
  lastPlayerIndex: number | null;
  /** Cards played by each seat in the current trick. */
  lastPlaysBySeat: Card[][];
  lastActionsBySeat: (SeatAction | null)[];
  passes: number;
  bottomCards: Card[];
  multiplier: number;
  /** Publicly played card counts by rank. Never includes hidden hands. */
  seenRanks: Record<number, number>;
  winnerIndex: number | null;
  scoreDelta: number[];
  message: string;
  gameNumber: number;
}

function shuffle<T>(arr: T[], rand: () => number): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export class DoudizhuEngine {
  state: EngineState = {
    phase: "BIDDING",
    seats: [],
    turnIndex: 0,
    gameNumber: 0,
    message: "",
    bottomCards: [],
    landlordIndex: null,
    bidStartIndex: 0,
    bidCandidateIndex: null,
    bidCount: 0,
    bidMultiplier: 1,
    actedCount: 0,
    lastPlay: [],
    lastPlayerIndex: null,
    lastPlaysBySeat: [[], [], []],
    lastActionsBySeat: [null, null, null],
    passes: 0,
    multiplier: 1,
    seenRanks: {},
    winnerIndex: null,
    scoreDelta: [],
  };

  constructor(
    private config: GameConfig,
    private rand: () => number = Math.random,
  ) {}

  startNewGame() {
    const previousScores = this.state.seats.length === 3
      ? this.state.seats.map((s) => s.score)
      : [0, 0, 0];
    const deck = shuffle(DECK, this.rand);
    const hands = [0, 1, 2].map((i) =>
      sortCards(deck.slice(i * 17, i * 17 + 17))
    );
    const bottom = sortCards(deck.slice(51, 54));
    const humanNames = ["玩家1", "玩家2", "玩家3"];
    const aiNames = ["小智", "阿强", "老K"];
    const seats: SeatState[] = [0, 1, 2].map((i) => ({
      index: i,
      name: i < this.config.humanCount ? humanNames[i] : aiNames[i],
      isHuman: i < this.config.humanCount,
      isAway: false,
      hand: hands[i],
      isLandlord: false,
      score: previousScores[i],
    }));
    const bidStartIndex = Math.floor(this.rand() * 3);
    this.state = {
      phase: "BIDDING",
      seats,
      turnIndex: bidStartIndex,
      bottomCards: bottom,
      landlordIndex: null,
      bidStartIndex,
      bidCandidateIndex: null,
      bidCount: 0,
      bidMultiplier: 1,
      actedCount: 0,
      lastPlay: [],
      lastPlayerIndex: null,
      lastPlaysBySeat: [[], [], []],
      lastActionsBySeat: [null, null, null],
      passes: 0,
      multiplier: 1,
      seenRanks: {},
      winnerIndex: null,
      scoreDelta: [],
      message: "等待叫地主",
      gameNumber: this.state.gameNumber + 1,
    };
  }

  submitCall(want: boolean) {
    if (this.state.phase !== "BIDDING") throw new Error("当前不在叫地主阶段");
    const seat = this.state.seats[this.state.turnIndex];
    const candidate = want
      ? this.state.turnIndex
      : this.state.bidCandidateIndex;
    const bidCount = this.state.bidCount + (want ? 1 : 0);
    const action: SeatAction = !want
      ? (this.state.bidCandidateIndex === null ? "NOT_CALL" : "NOT_ROB")
      : (this.state.bidCandidateIndex === null ? "CALL" : "ROB");
    const bidMultiplier = action === "ROB"
      ? this.state.bidMultiplier * 2
      : this.state.bidMultiplier;
    const acted = this.state.actedCount + 1;
    const message = action === "NOT_CALL"
      ? `${seat.name} 不叫`
      : action === "NOT_ROB"
      ? `${seat.name} 不抢`
      : action === "CALL"
      ? `${seat.name} 叫地主`
      : `${seat.name} 抢地主`;

    if (acted < 3) {
      this.state = {
        ...this.state,
        turnIndex: nextSeat(this.state.turnIndex),
        bidCandidateIndex: candidate,
        bidCount,
        bidMultiplier,
        actedCount: acted,
        lastActionsBySeat: this.state.lastActionsBySeat.map((value, index) =>
          index === this.state.turnIndex ? action : value
        ),
        message,
      };
      return;
    }

    if (acted === 3) {
      if (candidate === null) {
        this.startNewGame();
        return;
      }
      // If only the opening player called, confirm immediately. Any challenge
      // from seat two or three gives the opening player one final choice.
      if (candidate === this.state.bidStartIndex) {
        this.state = {
          ...this.state,
          bidCandidateIndex: candidate,
          bidCount,
          bidMultiplier,
          actedCount: acted,
          lastActionsBySeat: this.state.lastActionsBySeat.map((value, index) =>
            index === this.state.turnIndex ? action : value
          ),
          message,
        };
        this.confirmLandlord(candidate, bidCount, bidMultiplier);
        return;
      }
      this.state = {
        ...this.state,
        turnIndex: this.state.bidStartIndex,
        bidCandidateIndex: candidate,
        bidCount,
        bidMultiplier,
        actedCount: acted,
        lastActionsBySeat: this.state.lastActionsBySeat.map((value, index) =>
          index === this.state.turnIndex ? action : value
        ),
        message: `轮到 ${
          this.state.seats[this.state.bidStartIndex].name
        } 最终抢地主`,
      };
      return;
    }

    const landlord = want
      ? this.state.bidStartIndex
      : this.state.bidCandidateIndex;
    if (landlord === null) throw new Error("地主候选状态无效");
    this.confirmLandlord(landlord, bidCount, bidMultiplier);
  }

  submitPass() {
    if (this.state.phase !== "PLAYING") throw new Error("当前不能不出");
    if (!this.canPass()) throw new Error("本轮不能不出");
    const next = nextSeat(this.state.turnIndex);
    const message = `${this.state.seats[this.state.turnIndex].name} 不出`;
    const lastActionsBySeat = this.state.lastActionsBySeat.map((value, index) =>
      index === this.state.turnIndex ? "PASS" : value
    );
    if (this.state.passes + 1 >= 2) {
      this.state = {
        ...this.state,
        turnIndex: next,
        passes: 0,
        lastPlay: [],
        lastPlayerIndex: null,
        lastActionsBySeat,
        message: `轮到 ${this.state.seats[next].name} 出牌`,
      };
    } else {
      this.state = {
        ...this.state,
        turnIndex: next,
        passes: this.state.passes + 1,
        lastActionsBySeat,
        message,
      };
    }
  }

  submitPlay(cards: Card[]): Pattern {
    if (this.state.phase !== "PLAYING") throw new Error("现在不能出牌");
    const error = this.validatePlay(cards);
    if (error) throw new Error(error);
    const seat = this.state.seats[this.state.turnIndex];
    const pattern = classify(cards)!;
    const newHand = removeCards(seat.hand, cards);
    const isBomb = pattern.type === "BOMB" || pattern.type === "ROCKET";
    const multiplier = isBomb
      ? this.state.multiplier * 2
      : this.state.multiplier;
    const playedCards = sortCards(cards);
    const seenRanks = { ...this.state.seenRanks };
    for (const card of playedCards) {
      seenRanks[card.r] = (seenRanks[card.r] ?? 0) + 1;
    }
    const lastPlaysBySeat = this.state.lastPlaysBySeat.map((play, index) =>
      index === this.state.turnIndex ? playedCards : play
    );
    const lastActionsBySeat = this.state.lastActionsBySeat.map((value, index) =>
      index === this.state.turnIndex ? "PLAY" : value
    );

    if (newHand.length === 0) {
      this.state = {
        ...this.state,
        seats: this.state.seats.map((
          s,
          i,
        ) => (i === this.state.turnIndex ? { ...s, hand: newHand } : s)),
        lastPlay: playedCards,
        lastPlayerIndex: this.state.turnIndex,
        lastPlaysBySeat,
        lastActionsBySeat,
        seenRanks,
        multiplier,
      };
      this.finishGame(this.state.turnIndex, multiplier);
      return pattern;
    }
    this.state = {
      ...this.state,
      seats: this.state.seats.map((
        s,
        i,
      ) => (i === this.state.turnIndex ? { ...s, hand: newHand } : s)),
      turnIndex: nextSeat(this.state.turnIndex),
      lastPlay: playedCards,
      lastPlayerIndex: this.state.turnIndex,
      lastPlaysBySeat,
      lastActionsBySeat,
      seenRanks,
      passes: 0,
      multiplier,
      message: `${seat.name} 出牌`,
    };
    return pattern;
  }

  validatePlay(cards: Card[]): string | null {
    if (this.state.phase !== "PLAYING") return "现在不能出牌";
    const seat = this.state.seats[this.state.turnIndex];
    return validatePlayCards(
      seat.hand,
      this.state.lastPlay,
      this.isLeadingTurn(),
      cards,
    );
  }

  hintCards(): Card[] | null {
    if (this.state.phase !== "PLAYING") return null;
    const hand = this.state.seats[this.state.turnIndex].hand;
    const last = this.isLeadingTurn() ? [] : this.state.lastPlay;
    return hint(hand, last);
  }

  isHumanTurn(): boolean {
    const seat = this.state.seats[this.state.turnIndex];
    return this.state.phase !== "FINISHED" && seat.isHuman && !seat.isAway;
  }

  isAiTurn(): boolean {
    const seat = this.state.seats[this.state.turnIndex];
    return this.state.phase !== "FINISHED" && (!seat.isHuman || seat.isAway);
  }

  isLeadingTurn(): boolean {
    return this.state.lastPlay.length === 0;
  }

  canPass(): boolean {
    return this.state.lastPlay.length > 0 &&
      this.state.lastPlayerIndex !== this.state.turnIndex;
  }

  setSeatHuman(index: number, isHuman: boolean) {
    this.state = {
      ...this.state,
      seats: this.state.seats.map((
        s,
        i,
      ) => (i === index ? { ...s, isHuman } : s)),
    };
  }

  setSeatAway(index: number, isAway: boolean) {
    this.state = {
      ...this.state,
      seats: this.state.seats.map((
        s,
        i,
      ) => (i === index ? { ...s, isAway } : s)),
    };
  }

  private confirmLandlord(
    index: number,
    bidCount: number,
    bidMultiplier: number,
  ) {
    const bottom = this.state.bottomCards;
    const newSeats = this.state.seats.map((s, i) =>
      i === index
        ? { ...s, hand: sortCards([...s.hand, ...bottom]), isLandlord: true }
        : { ...s, isLandlord: false }
    );
    this.state = {
      ...this.state,
      phase: "PLAYING",
      seats: newSeats,
      turnIndex: index,
      landlordIndex: index,
      bidCandidateIndex: null,
      bidCount: Math.max(1, bidCount),
      bidMultiplier,
      actedCount: 0,
      lastPlay: [],
      lastPlayerIndex: null,
      lastPlaysBySeat: [[], [], []],
      lastActionsBySeat: [null, null, null],
      passes: 0,
      multiplier: bidMultiplier,
      seenRanks: {},
      message: `${newSeats[index].name} 成为地主`,
    };
  }

  private finishGame(winnerIndex: number, multiplier: number) {
    const landlordIndex = this.state.landlordIndex!;
    const base = Math.max(1, multiplier);
    const landlordWon = winnerIndex === landlordIndex;
    const delta = [0, 1, 2].map((i) => {
      if (i === landlordIndex) return landlordWon ? 2 * base : -2 * base;
      return landlordWon ? -base : base;
    });
    this.state = {
      ...this.state,
      phase: "FINISHED",
      seats: this.state.seats.map((s, i) => ({
        ...s,
        score: s.score + delta[i],
      })),
      winnerIndex,
      scoreDelta: delta,
      multiplier,
      message: `${this.state.seats[winnerIndex].name} 获胜`,
    };
  }
}

/** 0 -> 2 -> 1 -> 0，从牌桌下方观察为逆时针。 */
export function nextSeat(index: number): number {
  return (index + 2) % 3;
}
