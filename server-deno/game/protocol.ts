import { Card, cardNotation, parseCards } from "./cards.ts";
import { EngineState, Phase, SeatState } from "./engine.ts";
import { Difficulty } from "./engine.ts";

export type RemoteAction =
  | { kind: "CALL"; want: boolean }
  | { kind: "PLAY"; cards: string[] }
  | { kind: "PASS" };

export type ClientMessage =
  | { type: "CREATE"; name: string; difficulty: Difficulty; aiCount?: number }
  | { type: "JOIN"; room: string; name: string }
  | { type: "RECONNECT"; room: string; token: string }
  | { type: "START" }
  | { type: "RESTART" }
  | { type: "READY"; ready: boolean }
  | { type: "LEAVE" }
  | { type: "ACTION"; seat: number; action: RemoteAction }
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

export interface RoomStateContext {
  leaderboard: RoomLeaderboardEntry[];
  roundHistory: RoomRoundSummary[];
  readySeats: boolean[];
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
    phase: state.phase,
    seats: state.seats.map((s) => ({
      index: s.index,
      name: s.name,
      isHuman: s.isHuman,
      isAway: s.isAway === true,
      // Only expose the viewer's cards. Opponents remain count-only on clients.
      hand: viewerSeat === s.index ? s.hand.map(cardNotation) : [],
      handCount: s.hand.length,
      isLandlord: s.isLandlord,
      score: s.score,
    })),
    turnIndex: state.turnIndex,
    landlordIndex: state.landlordIndex,
    bidStartIndex: state.bidStartIndex,
    bidCandidateIndex: state.bidCandidateIndex,
    bidCount: state.bidCount,
    actedCount: state.actedCount,
    lastPlay: state.lastPlay.map(cardNotation),
    lastPlayerIndex: state.lastPlayerIndex,
    lastPlaysBySeat: state.lastPlaysBySeat.map((play) => play.map(cardNotation)),
    passes: state.passes,
    // Bottom cards stay hidden while bidding and are revealed only after the landlord is confirmed.
    bottomCards: state.phase === "BIDDING" ? [] : state.bottomCards.map(cardNotation),
    multiplier: state.multiplier,
    winnerIndex: state.winnerIndex,
    scoreDelta: state.scoreDelta,
    message: state.message,
    gameNumber: state.gameNumber,
    actionDeadlineAt,
    leaderboard: roomContext?.leaderboard ?? [],
    roundHistory: roomContext?.roundHistory ?? [],
    readySeats: roomContext?.readySeats ?? [true, true, true],
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
    actedCount: obj.actedCount as number,
    lastPlay: parseCards(Array.isArray(obj.lastPlay) ? obj.lastPlay as string[] : []),
    lastPlayerIndex: obj.lastPlayerIndex as number | null,
    lastPlaysBySeat,
    passes: obj.passes as number,
    bottomCards: parseCards(Array.isArray(obj.bottomCards) ? obj.bottomCards as string[] : []),
    multiplier: obj.multiplier as number,
    winnerIndex: obj.winnerIndex as number | null,
    scoreDelta: obj.scoreDelta as number[],
    message: obj.message as string,
    gameNumber: obj.gameNumber as number,
  };
}
