import { Card, cardNotation, parseCards } from "./cards.ts";
import {
  EngineState,
  Phase,
  PublicPlayRecord,
  SeatAction,
  SeatState,
} from "./engine.ts";
import { Difficulty } from "./engine.ts";
import { ComboType } from "./hand.ts";
import { GomokuState } from "./gomoku.ts";

export type GameType = "DOUDIZHU" | "GOMOKU";

export type RemoteAction =
  | { kind: "CALL"; want: boolean }
  | { kind: "PLAY"; cards: string[] }
  | { kind: "PASS" }
  | { kind: "MOVE"; row: number; col: number };

export type ClientMessage =
  | {
    type: "CREATE";
    name: string;
    difficulty: Difficulty;
    aiCount?: number;
    actionTimeoutSeconds?: number;
    gameType?: string;
    game_mode?: string;
    playerId?: string;
    authToken?: string;
  }
  | {
    type: "JOIN";
    room: string;
    name: string;
    gameType?: string;
    game_mode?: string;
    playerId?: string;
    authToken?: string;
  }
  | { type: "RECONNECT"; room: string; token: string; authToken?: string }
  | { type: "START" }
  | { type: "RESTART" }
  | { type: "READY"; ready: boolean }
  | { type: "DISBAND" }
  | { type: "QUICK_MESSAGE"; messageId: string }
  | {
    type: "THROW_ITEM";
    targetSeat: number;
    item: "TOMATO" | "EGG" | "ROSE";
  }
  | { type: "LEAVE" }
  | { type: "ACTION"; seat: number; action: RemoteAction }
  | { type: "GOMOKU_MOVE"; row: number; col: number }
  | { type: "PING" };

export interface RoomLeaderboardEntry {
  playerId: string;
  name: string;
  score: number;
  isAi: boolean;
  seatIndex: number | null;
  isAway: boolean;
  isActive: boolean;
}

export interface RoomRoundScore {
  playerId: string;
  name: string;
  delta: number;
  totalScore: number;
  isAi: boolean;
}

export interface RoomRoundSummary {
  gameNumber: number;
  winnerPlayerId: string | null;
  landlordPlayerId: string | null;
  multiplier: number;
  scores: RoomRoundScore[];
}

export interface ServerGameEvent {
  id: string;
  type: string;
  seat: number | null;
  automatic: boolean;
  at: number;
  row?: number;
  col?: number;
  comboType?: ComboType;
  primaryRank?: number;
  comboLength?: number;
}

export interface RoomStateContext {
  leaderboard: RoomLeaderboardEntry[];
  roundHistory: RoomRoundSummary[];
  readySeats: boolean[];
  revision?: number;
  lastEvent?: ServerGameEvent | null;
}

export function parseClientMessage(raw: string): ClientMessage {
  const obj = JSON.parse(raw);
  if (!obj || typeof obj !== "object" || typeof obj.type !== "string") {
    throw new Error("无法识别的消息");
  }
  return obj as ClientMessage;
}

export function serializeState(
  state: EngineState,
  viewerSeat: number | null = null,
  actionDeadlineAt: number | null = null,
  roomContext: RoomStateContext | null = null,
): Record<string, unknown> {
  return {
    gameType: "DOUDIZHU",
    game_mode: "doudizhu",
    phase: state.phase,
    seats: state.seats.map((s) => ({
      index: s.index,
      name: s.name,
      isHuman: s.isHuman,
      isAway: s.isAway === true,
      // Opponent cards stay hidden during play and are revealed after the round ends.
      hand: viewerSeat === s.index || state.phase === "FINISHED"
        ? s.hand.map(cardNotation)
        : [],
      handCount: s.hand.length,
      isLandlord: s.isLandlord,
      score: s.score,
    })),
    turnIndex: state.turnIndex,
    landlordIndex: state.landlordIndex,
    bidStartIndex: state.bidStartIndex,
    bidCandidateIndex: state.bidCandidateIndex,
    bidCount: state.bidCount,
    bidMultiplier: state.bidMultiplier,
    actedCount: state.actedCount,
    lastPlay: state.lastPlay.map(cardNotation),
    lastPlayerIndex: state.lastPlayerIndex,
    lastPlaysBySeat: state.lastPlaysBySeat.map((play) =>
      play.map(cardNotation)
    ),
    lastActionsBySeat: state.lastActionsBySeat,
    passes: state.passes,
    // Bottom cards stay hidden while bidding and are revealed only after the landlord is confirmed.
    bottomCards: state.phase === "BIDDING"
      ? []
      : state.bottomCards.map(cardNotation),
    multiplier: state.multiplier,
    seenRanks: state.seenRanks,
    playHistory: (state.playHistory ?? []).map((entry) => ({
      ...entry,
      cards: entry.cards.map(cardNotation),
    })),
    winnerIndex: state.winnerIndex,
    scoreDelta: state.scoreDelta,
    message: state.message,
    gameNumber: state.gameNumber,
    actionDeadlineAt,
    leaderboard: roomContext?.leaderboard ?? [],
    roundHistory: roomContext?.roundHistory ?? [],
    readySeats: roomContext?.readySeats ?? [true, true, true],
    revision: roomContext?.revision ?? 0,
    lastEvent: roomContext?.lastEvent ?? null,
  };
}

export function deserializeState(obj: Record<string, unknown>): EngineState {
  const seats = (obj.seats as Record<string, unknown>[]).map((s, index) => ({
    index: s.index as number,
    name: s.name as string,
    isHuman: s.isHuman as boolean,
    // Older server snapshots did not include isAway.
    isAway: s.isAway === true,
    hand: parseCards(Array.isArray(s.hand) ? s.hand as string[] : []),
    isLandlord: s.isLandlord as boolean,
    score: s.score as number,
  }));
  const rawLastPlays = Array.isArray(obj.lastPlaysBySeat)
    ? obj.lastPlaysBySeat
    : [];
  const lastPlaysBySeat = [0, 1, 2].map((index) => {
    const play = rawLastPlays[index];
    return Array.isArray(play) ? parseCards(play as string[]) : [];
  });
  const rawLastActions = Array.isArray(obj.lastActionsBySeat)
    ? obj.lastActionsBySeat
    : [];
  const lastActionsBySeat = [0, 1, 2].map((index) => {
    const action = rawLastActions[index];
    return typeof action === "string" ? action as SeatAction : null;
  });
  const rawPlayHistory = Array.isArray(obj.playHistory)
    ? obj.playHistory
    : [];
  const playHistory: PublicPlayRecord[] = rawPlayHistory.flatMap(
    (value): PublicPlayRecord[] => {
    if (!value || typeof value !== "object") return [];
    const entry = value as Record<string, unknown>;
    const seat = typeof entry.seat === "number" ? entry.seat : -1;
    const action: "PLAY" | "PASS" | null =
      entry.action === "PLAY" || entry.action === "PASS"
        ? entry.action
        : null;
    if (seat < 0 || seat > 2 || !action) return [];
    return [{
      seat,
      action,
      cards: parseCards(
        Array.isArray(entry.cards) ? entry.cards as string[] : [],
      ),
      ...(typeof entry.patternType === "string"
        ? { patternType: entry.patternType as ComboType }
        : {}),
      ...(typeof entry.primary === "number" ? { primary: entry.primary } : {}),
      ...(typeof entry.length === "number" ? { length: entry.length } : {}),
    }];
    },
  );
  return {
    phase: obj.phase as Phase,
    seats,
    turnIndex: obj.turnIndex as number,
    landlordIndex: obj.landlordIndex as number | null,
    bidStartIndex: typeof obj.bidStartIndex === "number"
      ? obj.bidStartIndex
      : obj.turnIndex as number,
    bidCandidateIndex: obj.bidCandidateIndex as number | null,
    bidCount: obj.bidCount as number,
    bidMultiplier: typeof obj.bidMultiplier === "number"
      ? obj.bidMultiplier
      : 1,
    actedCount: obj.actedCount as number,
    lastPlay: parseCards(
      Array.isArray(obj.lastPlay) ? obj.lastPlay as string[] : [],
    ),
    lastPlayerIndex: obj.lastPlayerIndex as number | null,
    lastPlaysBySeat,
    lastActionsBySeat,
    passes: obj.passes as number,
    bottomCards: parseCards(
      Array.isArray(obj.bottomCards) ? obj.bottomCards as string[] : [],
    ),
    multiplier: obj.multiplier as number,
    seenRanks: parseSeenRanks(obj.seenRanks),
    playHistory,
    winnerIndex: obj.winnerIndex as number | null,
    scoreDelta: obj.scoreDelta as number[],
    message: obj.message as string,
    gameNumber: obj.gameNumber as number,
  };
}

export function serializeGomokuState(
  state: GomokuState,
  names: string[],
  connectedSeats: boolean[],
  aiSeats: boolean[] = [],
  scores: number[] = [],
  playerIds: Array<string | null> = [],
  roomContext: RoomStateContext | null = null,
  actionDeadlineAt: number | null = null,
): Record<string, unknown> {
  return {
    gameType: "GOMOKU",
    game_mode: "gomoku",
    phase: state.phase,
    boardSize: state.board.length,
    board: state.board,
    seats: [0, 1].map((index) => ({
      index,
      name: names[index],
      isHuman: !aiSeats[index],
      isAi: aiSeats[index] === true,
      isAway: !aiSeats[index] && !connectedSeats[index],
      stone: index === 0 ? "BLACK" : "WHITE",
      score: scores[index] ?? 0,
      playerId: playerIds[index] ?? "",
    })),
    turnIndex: state.turnIndex,
    winnerIndex: state.winnerIndex,
    winningLine: state.winningLine,
    moveCount: state.moveCount,
    message: state.message,
    gameNumber: state.gameNumber,
    actionDeadlineAt,
    leaderboard: roomContext?.leaderboard ?? [],
    roundHistory: roomContext?.roundHistory ?? [],
    readySeats: (roomContext?.readySeats ?? [false, false]).slice(0, 2),
    revision: roomContext?.revision ?? 0,
    lastEvent: roomContext?.lastEvent ?? null,
  };
}

function parseSeenRanks(value: unknown): Record<number, number> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const result: Record<number, number> = {};
  for (const [rank, count] of Object.entries(value)) {
    const parsedRank = Number(rank);
    if (
      Number.isInteger(parsedRank) &&
      parsedRank >= 3 &&
      parsedRank <= 17 &&
      typeof count === "number" &&
      Number.isInteger(count) &&
      count > 0
    ) {
      result[parsedRank] = count;
    }
  }
  return result;
}
