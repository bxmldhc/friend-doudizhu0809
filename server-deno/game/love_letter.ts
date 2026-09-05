export type LoveLetterPhase = "WAITING" | "PLAYING" | "FINISHED";
export type LoveLetterCardName =
  | "GUARD"
  | "PRIEST"
  | "BARON"
  | "HANDMAID"
  | "PRINCE"
  | "KING"
  | "COUNTESS"
  | "PRINCESS";

export interface LoveLetterCard {
  id: string;
  value: number;
  name: LoveLetterCardName;
}

export interface LoveLetterPlayer {
  seat: number;
  alive: boolean;
  protected: boolean;
  hand: LoveLetterCard[];
  played: LoveLetterCard[];
  tokens: number;
}

export interface LoveLetterState {
  phase: LoveLetterPhase;
  players: LoveLetterPlayer[];
  deck: LoveLetterCard[];
  removedCard: LoveLetterCard | null;
  turnIndex: number;
  gameNumber: number;
  message: string;
  winnerIndex: number | null;
  lastAction: {
    seat: number;
    card: LoveLetterCardName;
    targetSeat: number | null;
    guessValue: number | null;
    result: string;
  } | null;
}

export interface LoveLetterAction {
  cardId: string;
  targetSeat?: number;
  guessValue?: number;
}

const CARD_DEFS: Array<[LoveLetterCardName, number, number]> = [
  ["GUARD", 1, 5],
  ["PRIEST", 2, 2],
  ["BARON", 3, 2],
  ["HANDMAID", 4, 2],
  ["PRINCE", 5, 2],
  ["KING", 6, 1],
  ["COUNTESS", 7, 1],
  ["PRINCESS", 8, 1],
];
const CARD_VALUES = new Set(CARD_DEFS.map(([, value]) => value));

export function createLoveLetterDeck(): LoveLetterCard[] {
  return CARD_DEFS.flatMap(([name, value, count]) =>
    Array.from({ length: count }, (_, index) => ({
      id: `${name.toLowerCase()}-${String(index + 1).padStart(2, "0")}`,
      value,
      name,
    }))
  );
}

function shuffle<T>(items: T[]): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

function targetRequired(card: LoveLetterCardName): boolean {
  return ["GUARD", "PRIEST", "BARON", "KING"].includes(card);
}

export class LoveLetterEngine {
  state: LoveLetterState = {
    phase: "WAITING",
    players: [],
    deck: [],
    removedCard: null,
    turnIndex: 0,
    gameNumber: 0,
    message: "",
    winnerIndex: null,
    lastAction: null,
  };

  constructor(private readonly playerCount: number) {
    if (playerCount < 2 || playerCount > 4) {
      throw new Error("情书仅支持 2 至 4 名玩家");
    }
  }

  startNewGame() {
    const deck = shuffle(createLoveLetterDeck());
    const removedCard = deck.pop() ?? null;
    const players = Array.from({ length: this.playerCount }, (_, seat) => ({
      seat,
      alive: true,
      protected: false,
      hand: [deck.pop()!],
      played: [],
      tokens: this.state.players[seat]?.tokens ?? 0,
    }));
    this.state = {
      phase: "PLAYING",
      players,
      deck,
      removedCard,
      turnIndex: (this.state.gameNumber) % this.playerCount,
      gameNumber: this.state.gameNumber + 1,
      message: "摸一张牌，然后选择一张打出",
      winnerIndex: null,
      lastAction: null,
    };
    this.drawFor(this.state.turnIndex);
  }

  play(seat: number, action: LoveLetterAction): void {
    const state = this.state;
    if (state.phase !== "PLAYING") throw new Error("情书本轮尚未开始");
    if (seat !== state.turnIndex) throw new Error("还没有轮到你");
    const player = state.players[seat];
    if (!player.alive) throw new Error("已淘汰玩家不能行动");
    if (player.hand.length !== 2) throw new Error("当前手牌状态错误");
    const cardIndex = player.hand.findIndex((card) => card.id === action.cardId);
    if (cardIndex < 0) throw new Error("这张牌不在你的手牌中");
    const card = player.hand[cardIndex];
    if (card.name !== "COUNTESS" && player.hand.some((item) =>
      item.name === "COUNTESS"
    ) && player.hand.some((item) =>
      item.name === "KING" || item.name === "PRINCE"
    )) {
      throw new Error("手里有王子或国王时必须打出伯爵夫人");
    }
    const target = action.targetSeat === undefined ? null : action.targetSeat;
    if (targetRequired(card.name)) this.validateTarget(seat, target);
    if (card.name === "GUARD") {
      if (!CARD_VALUES.has(action.guessValue ?? -1) || action.guessValue === 1) {
        throw new Error("守卫猜测必须是 2 至 8 的牌点");
      }
    } else if (action.guessValue !== undefined) {
      throw new Error("只有守卫可以填写猜测牌点");
    }

    player.hand.splice(cardIndex, 1);
    player.played.push(card);
    player.protected = false;
    let result = `${card.name} 已打出`;
    switch (card.name) {
      case "GUARD":
        result = this.applyGuard(seat, target!, action.guessValue!);
        break;
      case "PRIEST":
        result = `查看了 ${target! + 1} 号玩家的手牌`;
        break;
      case "BARON":
        result = this.applyBaron(seat, target!);
        break;
      case "HANDMAID":
        player.protected = true;
        result = "本回合受到侍女保护";
        break;
      case "PRINCE":
        result = this.applyPrince(target!);
        break;
      case "KING": {
        const other = state.players[target!];
        [player.hand, other.hand] = [other.hand, player.hand];
        result = `与 ${target! + 1} 号玩家交换手牌`;
        break;
      }
      case "PRINCESS":
        this.eliminate(seat, "弃掉公主");
        result = "弃掉公主，立即淘汰";
        break;
      case "COUNTESS":
        break;
    }
    const winner = this.resolveEndOfRound();
    state.lastAction = {
      seat,
      card: card.name,
      targetSeat: target,
      guessValue: action.guessValue ?? null,
      result,
    };
    if (winner !== null) {
      state.phase = "FINISHED";
      state.winnerIndex = winner;
      state.players[winner].tokens += 1;
      state.message = `${winner + 1} 号玩家赢得本轮`;
      return;
    }
    state.turnIndex = this.nextAliveSeat(seat);
    this.drawFor(state.turnIndex);
    state.message = `${state.turnIndex + 1} 号玩家行动`;
  }

  private validateTarget(seat: number, target: number | null): asserts target is number {
    if (target === null || !Number.isInteger(target) || target < 0 || target >= this.playerCount) {
      throw new Error("请选择有效目标");
    }
    const targetPlayer = this.state.players[target];
    if (target === seat) throw new Error("不能选择自己作为目标");
    if (!targetPlayer.alive) throw new Error("不能选择已淘汰玩家");
    if (targetPlayer.protected) throw new Error("目标受到侍女保护");
  }

  private applyGuard(seat: number, target: number, guess: number): string {
    const targetPlayer = this.state.players[target];
    if (targetPlayer.hand[0]?.value === guess) {
      this.eliminate(target, `守卫猜中 ${guess}`);
      return `猜中 ${target + 1} 号玩家的 ${guess} 点牌`;
    }
    return `没有猜中 ${target + 1} 号玩家`;
  }

  private applyBaron(seat: number, target: number): string {
    const own = this.state.players[seat];
    const other = this.state.players[target];
    const ownValue = own.hand[0]?.value ?? 0;
    const otherValue = other.hand[0]?.value ?? 0;
    if (ownValue > otherValue) this.eliminate(target, "男爵比较点数较低");
    else if (otherValue > ownValue) this.eliminate(seat, "男爵比较点数较低");
    return ownValue === otherValue ? "男爵比较平局" : "男爵比较完成";
  }

  private applyPrince(target: number): string {
    const player = this.state.players[target];
    const discarded = player.hand.pop();
    if (!discarded) throw new Error("目标没有可弃的手牌");
    player.played.push(discarded);
    if (discarded.name === "PRINCESS") {
      this.eliminate(target, "王子令其弃掉公主");
      return `${target + 1} 号玩家弃掉公主并淘汰`;
    }
    const drawn = this.state.deck.pop();
    if (drawn) player.hand.push(drawn);
    return `${target + 1} 号玩家弃牌并补牌`;
  }

  private eliminate(seat: number, reason: string) {
    const player = this.state.players[seat];
    player.alive = false;
    player.protected = false;
    player.hand = [];
    this.state.message = `${seat + 1} 号玩家已淘汰：${reason}`;
  }

  private resolveEndOfRound(): number | null {
    const alive = this.state.players.filter((player) => player.alive);
    if (alive.length === 1) return alive[0].seat;
    if (this.state.deck.length > 0) return null;
    return alive.reduce((best, player) => {
      const playerValue = player.hand[0]?.value ?? 0;
      const bestValue = best.hand[0]?.value ?? 0;
      if (playerValue !== bestValue) return playerValue > bestValue ? player : best;
      const playerDiscarded = player.played.reduce((sum, card) => sum + card.value, 0);
      const bestDiscarded = best.played.reduce((sum, card) => sum + card.value, 0);
      return playerDiscarded > bestDiscarded ? player : best;
    }).seat;
  }

  private drawFor(seat: number) {
    const player = this.state.players[seat];
    if (!player?.alive || player.hand.length >= 2) return;
    // 侍女的保护持续到该玩家下一次回合开始。
    player.protected = false;
    const drawn = this.state.deck.pop();
    if (drawn) player.hand.push(drawn);
  }

  private nextAliveSeat(from: number): number {
    for (let offset = 1; offset <= this.playerCount; offset++) {
      const seat = (from + offset) % this.playerCount;
      if (this.state.players[seat].alive) return seat;
    }
    return from;
  }
}

export function serializeLoveLetterState(
  state: LoveLetterState,
  viewerSeat: number,
  roomContext: { readySeats?: boolean[]; roundHistory?: unknown[]; lastEvent?: unknown } | null = null,
  actionDeadlineAt: number | null = null,
): Record<string, unknown> {
  return {
    gameType: "LOVE_LETTER",
    game_mode: "love-letter",
    phase: state.phase,
    players: state.players.map((player) => ({
      seat: player.seat,
      alive: player.alive,
      protected: player.protected,
      hand: player.seat === viewerSeat || state.phase === "FINISHED"
        ? player.hand
        : player.hand.map(() => ({ hidden: true })),
      handCount: player.hand.length,
      played: player.played,
      tokens: player.tokens,
    })),
    deckCount: state.deck.length,
    turnIndex: state.turnIndex,
    gameNumber: state.gameNumber,
    message: state.message,
    winnerIndex: state.winnerIndex,
    lastAction: state.lastAction,
    actionDeadlineAt,
    readySeats: roomContext?.readySeats ?? [],
    roundHistory: roomContext?.roundHistory ?? [],
    lastEvent: roomContext?.lastEvent ?? null,
  };
}
