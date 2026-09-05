import { Difficulty, DoudizhuEngine, EngineState } from "./engine.ts";
import { AiAction, decide } from "./ai.ts";
import {
  GameType,
  RemoteAction,
  RoomLeaderboardEntry,
  RoomRoundSummary,
  serializeGomokuState,
  serializeState,
  ServerGameEvent,
} from "./protocol.ts";
import { recordGame } from "./store.ts";
import { parseCards } from "./cards.ts";
import { resolveSession } from "./auth.ts";
import { Pattern } from "./hand.ts";
import { GomokuEngine } from "./gomoku.ts";
import { chooseGomokuAiMove } from "./gomoku_ai.ts";
import { LoveLetterEngine, LoveLetterAction, serializeLoveLetterState } from "./love_letter.ts";
import type { RoomInvitationEligibility } from "../api.ts";

interface Room {
  code: string;
  matchId: string;
  startedAt: number;
  lastActivityAt: number;
  noOpenConnectionSince: number | null;
  sockets: (WebSocket | null)[];
  resumeTokens: (string | null)[];
  playerIds: (string | null)[];
  names: string[];
  hostSeat: number;
  participants: Map<string, RoomParticipant>;
  roundHistory: RoomRoundSummary[];
  lastRecordedGameNumber: number;
  readySeats: boolean[];
  engine: DoudizhuEngine | null;
  gomoku: GomokuEngine | null;
  loveLetter: LoveLetterEngine | null;
  gameType: GameType;
  difficulty: Difficulty;
  aiCount: number;
  actionTimeoutMs: number;
  quickMessageAt: number[];
  throwItemAt: number[];
  aiRunning: boolean;
  actionDeadlineAt: number | null;
  turnTimer: ReturnType<typeof setTimeout> | null;
  turnVersion: number;
  revision: number;
  lastEvent: ServerGameEvent | null;
}

interface RoomParticipant {
  id: string;
  name: string;
  score: number;
  isAi: boolean;
  isAuthenticated: boolean;
  joinedAt: number;
}

export interface AdminRoomSeatSnapshot {
  seat: number;
  name: string;
  isAi: boolean;
  isConnected: boolean;
  isAway: boolean;
  isHost: boolean;
}

export interface AdminRoomSnapshot {
  roomCode: string;
  createdAt: number;
  phase: "WAITING" | "BIDDING" | "PLAYING" | "FINISHED";
  gameType: GameType;
  gameNumber: number;
  difficulty: Difficulty;
  aiCount: number;
  actionTimeoutSeconds: number;
  connectedHumanCount: number;
  occupiedHumanCount: number;
  hostSeat: number;
  hostName: string | null;
  seats: AdminRoomSeatSnapshot[];
}

export interface AdminRoomOverview {
  roomCount: number;
  activeGameCount: number;
  connectedHumanCount: number;
  occupiedHumanCount: number;
  aiCount: number;
}

export interface AdminOnlineAccountSnapshot {
  nickname: string;
  online: true;
  location: "ROOM";
  gameType: GameType;
  roomCode: string;
  lastActiveAt: number;
}

interface RoomOperationResult {
  ok: boolean;
  code?: string;
  message?: string;
}

const AI_NAMES = ["小智", "阿强", "老K"];
const GOMOKU_AI_NAME = "棋灵";
const LOVE_LETTER_AI_NAME = "信使";
const PLAYER_ID_PATTERN = /^[A-Za-z0-9_-]{8,80}$/;
export const DEFAULT_STALE_ROOM_GRACE_MS = 60_000;
const DEFAULT_ACTION_TIMEOUT_SECONDS = 20;
const ALLOWED_ACTION_TIMEOUT_SECONDS = new Set([15, 20, 30, 45]);
const QUICK_MESSAGE_COOLDOWN_MS = 2_000;
const THROW_ITEM_COOLDOWN_MS = 8_000;
const THROW_ITEMS = new Set(["TOMATO", "EGG", "ROSE"]);
const QUICK_MESSAGE_IDS = new Set([
  "WAIT_A_MOMENT",
  "READY_TO_START",
  "PLAY_AGAIN",
  "NETWORK_SLOW",
  "BE_RIGHT_BACK",
]);

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function send(ws: WebSocket, obj: unknown) {
  if (ws.readyState === 1) {
    ws.send(JSON.stringify(obj));
  }
}

function sendError(ws: WebSocket, code: string, message: string) {
  send(ws, { type: "ERROR", code, message });
}

export class RoomManager {
  private rooms = new Map<string, Room>();
  private socketRoom = new WeakMap<WebSocket, string>();
  private socketAuthTokens = new WeakMap<WebSocket, string>();
  private socketAccountIds = new WeakMap<WebSocket, string>();

  constructor(
    private options: {
      actionTimeoutMs?: number;
      aiDelayMs?: number;
      persistStats?: boolean;
      staleRoomGraceMs?: number;
    } = {},
  ) {}

  create(
    ws: WebSocket,
    name: string,
    _difficulty: Difficulty,
    _requestedAiCount: number,
    requestedPlayerId = "",
    isAuthenticated = false,
    requestedActionTimeoutSeconds = DEFAULT_ACTION_TIMEOUT_SECONDS,
    requestedGameType: GameType = "DOUDIZHU",
  ): string {
    const gameType = requestedGameType === "LOVE_LETTER" ? "LOVE_LETTER" : requestedGameType;
    // 等候区始终为朋友保留所有座位，只有开局时才补足困难 AI。
    const aiCount = 0;
    const difficulty: Difficulty = "HARD";
    let code = "";
    do {
      code = String(Math.floor(100000 + Math.random() * 900000));
    } while (this.rooms.has(code));
    const host = this.createParticipant(
      name,
      false,
      requestedPlayerId,
      isAuthenticated,
    );
    const participants = new Map<string, RoomParticipant>([[host.id, host]]);
    const seatCount = gameType === "GOMOKU" ? 2 : gameType === "LOVE_LETTER" ? 4 : 3;
    const playerIds: (string | null)[] = Array(seatCount).fill(null);
    playerIds[0] = host.id;
    const names = Array(seatCount).fill("等待加入");
    names[0] = name;
    const createdAt = Date.now();
    const actionTimeoutSeconds = gameType === "GOMOKU"
      ? DEFAULT_ACTION_TIMEOUT_SECONDS
      : ALLOWED_ACTION_TIMEOUT_SECONDS.has(requestedActionTimeoutSeconds)
      ? requestedActionTimeoutSeconds
      : DEFAULT_ACTION_TIMEOUT_SECONDS;
    const room: Room = {
      code,
      matchId: crypto.randomUUID(),
      startedAt: createdAt,
      lastActivityAt: createdAt,
      noOpenConnectionSince: null,
      sockets: [ws, null, null],
      resumeTokens: [crypto.randomUUID(), null, null],
      playerIds,
      names,
      hostSeat: 0,
      participants,
      roundHistory: [],
      lastRecordedGameNumber: 0,
      readySeats: Array(seatCount).fill(true),
      engine: null,
      gomoku: null,
      loveLetter: null,
      gameType,
      difficulty,
      aiCount,
      actionTimeoutMs: actionTimeoutSeconds * 1_000,
      quickMessageAt: Array(seatCount).fill(0),
      throwItemAt: Array(seatCount).fill(0),
      aiRunning: false,
      actionDeadlineAt: null,
      turnTimer: null,
      turnVersion: 0,
      revision: 0,
      lastEvent: null,
    };
    this.rooms.set(code, room);
    this.socketRoom.set(ws, code);
    this.sendRoom(room, 0, ws);
    return code;
  }

  getRoomInvitationEligibility(input: {
    roomCode: string;
    inviterAccountId: string;
    targetAccountId: string;
  }): RoomInvitationEligibility {
    const room = this.rooms.get(input.roomCode.trim());
    if (!room) {
      return {
        ok: false,
        code: "ROOM_NOT_FOUND",
        message: "房间不存在或已结束",
      };
    }
    if (room.engine || room.gomoku || room.loveLetter) {
      return {
        ok: false,
        code: "GAME_ALREADY_STARTED",
        message: "对局已经开始，暂时不能邀请新玩家",
      };
    }
    const inviterSeat = room.playerIds.findIndex((playerId) =>
      playerId === input.inviterAccountId
    );
    const inviter = inviterSeat >= 0
      ? room.participants.get(input.inviterAccountId)
      : null;
    if (!inviter || inviter.isAi || !inviter.isAuthenticated) {
      return {
        ok: false,
        code: "INVITER_NOT_IN_ROOM",
        message: "邀请人当前不在这个等待区",
      };
    }
    if (room.playerIds.includes(input.targetAccountId)) {
      return {
        ok: false,
        code: "TARGET_ALREADY_IN_ROOM",
        message: "这位好友已经在房间中",
      };
    }
    const hasEmptySeat = room.playerIds
      .slice(0, this.seatCount(room))
      .some((playerId) => playerId === null);
    if (!hasEmptySeat) {
      return {
        ok: false,
        code: "ROOM_FULL",
        message: "等待区已满，无法继续邀请",
      };
    }
    return { ok: true, gameType: room.gameType };
  }

  join(
    ws: WebSocket,
    code: string,
    name: string,
    requestedPlayerId = "",
    isAuthenticated = false,
  ): RoomOperationResult {
    const room = this.rooms.get(code);
    if (!room) {
      return { ok: false, code: "ROOM_NOT_FOUND", message: "房间不存在" };
    }
    if (this.isRoundInProgress(room)) {
      return {
        ok: false,
        code: "GAME_ALREADY_STARTED",
        message: "游戏已经开始，不能加入",
      };
    }
    const seat = room.sockets.findIndex((s, i) =>
      i < this.seatCount(room) && s === null && room.playerIds[i] === null
    );
    if (seat < 0) {
      return { ok: false, code: "ROOM_FULL", message: "房间已满" };
    }
    const stablePlayerId = this.normalizePlayerId(requestedPlayerId);
    if (
      stablePlayerId &&
      room.playerIds.some((playerId, index) =>
        playerId === stablePlayerId && room.sockets[index] !== null
      )
    ) {
      return {
        ok: false,
        code: "INVALID_ACTION",
        message: "该玩家已经在房间中",
      };
    }
    const participant = stablePlayerId
      ? room.participants.get(stablePlayerId) ??
        this.createParticipant(name, false, stablePlayerId, isAuthenticated)
      : this.createParticipant(name, false, "", isAuthenticated);
    participant.name = name;
    participant.isAuthenticated ||= isAuthenticated;
    room.participants.set(participant.id, participant);
    room.sockets[seat] = ws;
    room.resumeTokens[seat] = crypto.randomUUID();
    room.playerIds[seat] = participant.id;
    room.names[seat] = name;
    room.readySeats[seat] = false;
    this.touchRoom(room);
    this.socketRoom.set(ws, code);
    this.sendRoom(room, seat, ws);
    this.broadcastPlayers(room);
    if (room.engine) {
      this.syncSeatIdentity(room);
      this.publishState(room);
    } else if (room.gomoku || room.loveLetter) {
      this.broadcastState(room);
    }
    return { ok: true };
  }

  reconnect(
    ws: WebSocket,
    code: string,
    token: string,
    expectedPlayerId = "",
  ): RoomOperationResult {
    const room = this.rooms.get(code);
    if (!room) {
      return { ok: false, code: "ROOM_NOT_FOUND", message: "房间不存在" };
    }
    const seat = room.resumeTokens.findIndex((value) =>
      value === token && value !== null
    );
    if (seat < 0) {
      return {
        ok: false,
        code: "RESUME_DENIED",
        message: "恢复凭证无效，无法认领原座位",
      };
    }
    if (
      expectedPlayerId &&
      room.playerIds[seat] !== expectedPlayerId
    ) {
      return {
        ok: false,
        code: "RESUME_DENIED",
        message: "当前账号不能认领该座位",
      };
    }

    const previousSocket = room.sockets[seat];
    if (previousSocket && previousSocket !== ws) {
      this.socketRoom.delete(previousSocket);
      this.socketAuthTokens.delete(previousSocket);
      this.socketAccountIds.delete(previousSocket);
      try {
        previousSocket.close(4001, "座位已在另一台设备恢复");
      } catch {
        // Test doubles and already-closed sockets may not implement close.
      }
    }
    room.sockets[seat] = ws;
    this.touchRoom(room);
    this.socketRoom.set(ws, code);
    if (this.isRoundFinished(room)) {
      room.readySeats[seat] = false;
    }
    if (room.engine) {
      room.engine.setSeatHuman(seat, true);
      room.engine.setSeatAway(seat, false);
    }
    this.sendRoom(room, seat, ws);
    if (room.engine) {
      if (
        room.engine.state.turnIndex === seat &&
        room.engine.state.phase !== "FINISHED"
      ) {
        this.publishState(room);
      } else {
        this.broadcastState(room);
      }
    } else if (room.gomoku || room.loveLetter) {
      this.broadcastState(room);
    } else {
      this.broadcastPlayers(room);
    }
    return { ok: true };
  }

  start(ws: WebSocket) {
    const room = this.getRoomBySocket(ws);
    if (!room) {
      sendError(ws, "INVALID_ACTION", "当前连接不在房间中");
      return;
    }
    if (room.sockets[room.hostSeat] !== ws) {
      sendError(ws, "NOT_HOST", "只有房主可以开始");
      return;
    }
    if (!this.validateStartPlayers(room, ws)) return;
    if (!room.engine && !room.gomoku && !room.loveLetter) {
      this.fillMissingSeatsWithAi(room);
    }
    if (room.gameType === "LOVE_LETTER") {
      room.loveLetter = new LoveLetterEngine(this.seatCount(room));
      room.loveLetter.startNewGame();
      this.touchRoom(room);
      this.markEvent(room, "ROUND_STARTED", null, false);
      this.publishState(room);
      return;
    }
    if (room.gameType === "GOMOKU") {
      if (!room.gomoku) room.gomoku = new GomokuEngine();
      room.gomoku.startNewGame();
      this.touchRoom(room);
      this.markEvent(room, "ROUND_STARTED", null, false);
      this.publishState(room);
      return;
    }
    if (!room.engine) {
      room.engine = new DoudizhuEngine({
        humanCount: this.seatCount(room) - room.aiCount,
        difficulty: room.difficulty,
      });
    }
    room.engine.startNewGame();
    this.touchRoom(room);
    this.markEvent(room, "ROUND_STARTED", null, false);
    this.syncSeatIdentity(room);
    this.publishState(room);
  }

  restart(ws: WebSocket) {
    const room = this.getRoomBySocket(ws);
    if (!room || (room.gameType === "GOMOKU" ? !room.gomoku : room.gameType === "LOVE_LETTER" ? !room.loveLetter : !room.engine)) {
      sendError(ws, "INVALID_ACTION", "当前房间没有可重开的牌局");
      return;
    }
    if (room.sockets[room.hostSeat] !== ws) {
      sendError(ws, "NOT_HOST", "只有房主可以重开");
      return;
    }
    if (!this.isRoundFinished(room)) {
      sendError(ws, "INVALID_ACTION", "当前牌局尚未结束");
      return;
    }
    if (!this.validateStartPlayers(room, ws)) return;
    if (
      !room.readySeats.every((ready, seat) => {
        const participant = room.playerIds[seat]
          ? room.participants.get(room.playerIds[seat]!)
          : null;
        return !participant || participant.isAi ||
          room.sockets[seat] === null || ready;
      })
    ) {
      sendError(ws, "NOT_READY", "请等待所有玩家准备");
      return;
    }
    if (room.gameType === "GOMOKU") {
      room.gomoku!.startNewGame();
    } else if (room.gameType === "LOVE_LETTER") {
      room.loveLetter!.startNewGame();
    } else {
      room.engine!.startNewGame();
    }
    this.touchRoom(room);
    this.markEvent(room, "ROUND_STARTED", null, false);
    this.resetReadySeats(room);
    if (room.engine) this.syncSeatIdentity(room);
    this.publishState(room);
  }

  setReady(ws: WebSocket, ready: boolean) {
    const room = this.getRoomBySocket(ws);
    if (!room || (room.gameType === "GOMOKU" ? !room.gomoku : room.gameType === "LOVE_LETTER" ? !room.loveLetter : !room.engine)) {
      sendError(ws, "INVALID_ACTION", "当前房间没有可准备的牌局");
      return;
    }
    if (!this.isRoundFinished(room)) {
      sendError(ws, "INVALID_ACTION", "只有本局结束后才能准备");
      return;
    }
    const seat = room.sockets.findIndex((socket) => socket === ws);
    if (seat < 0 || !room.playerIds[seat]) return;
    room.readySeats[seat] = ready;
    this.touchRoom(room);
    this.broadcastState(room);
  }

  disband(ws: WebSocket) {
    const room = this.getRoomBySocket(ws);
    if (!room) {
      sendError(ws, "INVALID_ACTION", "当前连接不在房间中");
      return;
    }
    if (room.sockets[room.hostSeat] !== ws) {
      sendError(ws, "NOT_HOST", "只有房主可以解散房间");
      return;
    }
    if (this.isRoundInProgress(room)) {
      sendError(ws, "GAME_IN_PROGRESS", "牌局进行中不能解散房间");
      return;
    }
    const sockets = room.sockets.filter(
      (socket): socket is WebSocket => socket !== null,
    );
    for (const socket of sockets) {
      send(socket, {
        type: "ROOM_DISBANDED",
        room: room.code,
        reason: "房主已解散房间",
      });
    }
    this.destroyRoom(room);
    for (const socket of sockets) {
      try {
        socket.close(4000, "room disbanded");
      } catch {
        // Test doubles may not implement a full WebSocket close.
      }
    }
  }

  sendQuickMessage(ws: WebSocket, requestedMessageId: string) {
    const room = this.getRoomBySocket(ws);
    if (!room) {
      sendError(ws, "INVALID_ACTION", "当前连接不在房间中");
      return;
    }
    const seat = room.sockets.findIndex((socket) => socket === ws);
    if (seat < 0) return;
    const messageId = requestedMessageId.trim().toUpperCase();
    if (!QUICK_MESSAGE_IDS.has(messageId)) {
      sendError(ws, "INVALID_QUICK_MESSAGE", "不支持这条快捷消息");
      return;
    }
    const now = Date.now();
    if (now - room.quickMessageAt[seat] < QUICK_MESSAGE_COOLDOWN_MS) {
      sendError(ws, "MESSAGE_RATE_LIMITED", "发送太快，请稍后再试");
      return;
    }
    room.quickMessageAt[seat] = now;
    this.touchRoom(room);
    for (const socket of room.sockets) {
      if (socket) {
        send(socket, {
          type: "QUICK_MESSAGE",
          seat,
          messageId,
          at: now,
        });
      }
    }
  }

  sendThrowItem(ws: WebSocket, targetSeat: number, requestedItem: string) {
    const room = this.getRoomBySocket(ws);
    if (!room || room.gameType !== "DOUDIZHU" || !room.engine ||
      room.engine.state.phase === "FINISHED") {
      sendError(ws, "INVALID_ACTION", "只能在斗地主对局中投掷");
      return;
    }
    const fromSeat = room.sockets.findIndex((socket) => socket === ws);
    const item = requestedItem.trim().toUpperCase();
    if (
      fromSeat < 0 ||
      targetSeat < 0 ||
      targetSeat >= this.seatCount(room) ||
      targetSeat === fromSeat ||
      !THROW_ITEMS.has(item)
    ) {
      sendError(ws, "INVALID_THROW_ITEM", "投掷目标或道具无效");
      return;
    }
    const sender = room.playerIds[fromSeat]
      ? room.participants.get(room.playerIds[fromSeat]!)
      : null;
    const target = room.playerIds[targetSeat]
      ? room.participants.get(room.playerIds[targetSeat]!)
      : null;
    if (!sender || sender.isAi || !target) {
      sendError(ws, "INVALID_THROW_ITEM", "当前无法投掷给该玩家");
      return;
    }
    const now = Date.now();
    if (now - room.throwItemAt[fromSeat] < THROW_ITEM_COOLDOWN_MS) {
      sendError(ws, "THROW_ITEM_RATE_LIMITED", "投掷太快，请稍后再试");
      return;
    }
    room.throwItemAt[fromSeat] = now;
    this.touchRoom(room);
    for (const socket of room.sockets) {
      if (socket) {
        send(socket, {
          type: "ITEM_THROWN",
          fromSeat,
          targetSeat,
          item,
          at: now,
        });
      }
    }
  }

  action(ws: WebSocket, seat: number, action: RemoteAction) {
    const room = this.getRoomBySocket(ws);
    if (!room || (room.gameType === "GOMOKU" ? !room.gomoku : room.gameType === "LOVE_LETTER" ? !room.loveLetter : !room.engine)) {
      sendError(ws, "INVALID_ACTION", "当前房间没有进行中的牌局");
      return;
    }
    if (!Number.isInteger(seat) || room.sockets[seat] !== ws) {
      sendError(ws, "INVALID_ACTION", "不能操作其他玩家的座位");
      return;
    }
    const turnIndex = room.gameType === "GOMOKU"
      ? room.gomoku!.state.turnIndex
      : room.gameType === "LOVE_LETTER"
      ? room.loveLetter!.state.turnIndex
      : room.engine!.state.turnIndex;
    if (turnIndex !== seat) {
      sendError(ws, "INVALID_ACTION", "当前还没有轮到你操作");
      return;
    }
    if (room.gameType === "GOMOKU") {
      if (action.kind !== "MOVE") {
        sendError(ws, "INVALID_ACTION", "Gomoku only accepts MOVE actions");
        return;
      }
      this.applyGomokuMove(room, seat, action.row, action.col);
      return;
    }
    if (room.gameType === "LOVE_LETTER") {
      if (action.kind !== "LOVE_LETTER_PLAY") {
        sendError(ws, "INVALID_ACTION", "情书操作无效");
        return;
      }
      try {
        room.loveLetter!.play(seat, action as LoveLetterAction);
        this.touchRoom(room);
        this.markEvent(room, "LOVE_LETTER_PLAY", seat, false);
        if (room.loveLetter?.state.phase === "FINISHED") this.recordFinishedLoveLetterRound(room);
        this.publishState(room);
      } catch (err) {
        sendError(ws, "INVALID_ACTION", err instanceof Error ? err.message : String(err));
      }
      return;
    }
    this.applyAction(room, seat, action);
  }

  cleanupStaleRooms(now = Date.now()): number {
    const graceMs = Math.max(
      0,
      this.options.staleRoomGraceMs ?? DEFAULT_STALE_ROOM_GRACE_MS,
    );
    let cleaned = 0;
    for (const room of this.rooms.values()) {
      if (this.hasOpenHumanConnection(room)) {
        room.noOpenConnectionSince = null;
        continue;
      }
      if (room.noOpenConnectionSince === null) {
        room.noOpenConnectionSince = now;
        continue;
      }
      const staleSince = Math.max(
        room.lastActivityAt,
        room.noOpenConnectionSince,
      );
      if (now - staleSince < graceMs) continue;
      this.destroyRoom(room);
      cleaned += 1;
    }
    return cleaned;
  }

  getAdminRoomSnapshots(): AdminRoomSnapshot[] {
    return [...this.rooms.values()]
      .map((room) => this.toAdminRoomSnapshot(room))
      .sort((left, right) => right.createdAt - left.createdAt);
  }

  getAdminOverview(): AdminRoomOverview {
    const rooms = this.getAdminRoomSnapshots();
    return {
      roomCount: rooms.length,
      activeGameCount:
        rooms.filter((room) =>
          room.phase === "BIDDING" || room.phase === "PLAYING"
        ).length,
      connectedHumanCount: rooms.reduce(
        (sum, room) => sum + room.connectedHumanCount,
        0,
      ),
      occupiedHumanCount: rooms.reduce(
        (sum, room) => sum + room.occupiedHumanCount,
        0,
      ),
      aiCount: rooms.reduce((sum, room) => sum + room.aiCount, 0),
    };
  }

  getAdminOnlineAccounts(): AdminOnlineAccountSnapshot[] {
    const accounts = new Map<string, AdminOnlineAccountSnapshot>();

    for (const room of this.rooms.values()) {
      for (let seat = 0; seat < this.seatCount(room); seat++) {
        const socket = room.sockets[seat];
        const playerId = room.playerIds[seat];
        if (
          !socket ||
          socket.readyState !== WebSocket.OPEN ||
          !playerId ||
          this.socketAccountIds.get(socket) !== playerId
        ) {
          continue;
        }

        const participant = room.participants.get(playerId);
        if (!participant || participant.isAi || !participant.isAuthenticated) {
          continue;
        }

        const snapshot: AdminOnlineAccountSnapshot = {
          nickname: participant.name,
          online: true,
          location: "ROOM",
          gameType: room.gameType,
          roomCode: room.code,
          lastActiveAt: room.lastActivityAt,
        };
        const existing = accounts.get(playerId);
        if (!existing || snapshot.lastActiveAt > existing.lastActiveAt) {
          accounts.set(playerId, snapshot);
        }
      }
    }

    return [...accounts.values()].sort((left, right) =>
      right.lastActiveAt - left.lastActiveAt ||
      left.nickname.localeCompare(right.nickname, "zh-CN")
    );
  }

  disconnect(ws: WebSocket) {
    const code = this.socketRoom.get(ws);
    if (!code) return;
    const room = this.rooms.get(code);
    if (!room) return;
    const seat = room.sockets.findIndex((s) => s === ws);
    if (seat >= 0) {
      room.sockets[seat] = null;
      const hostChanged = seat === room.hostSeat;
      if (hostChanged) {
        room.hostSeat = this.pickNewHost(room);
      }
      if (this.isRoundInProgress(room)) {
        if (room.engine) {
          room.engine.setSeatHuman(seat, false);
          room.engine.setSeatAway(seat, true);
        }
        // Active game seats retain their identity and resume token.
      } else {
        room.names[seat] = "等待加入";
        room.resumeTokens[seat] = null;
        room.playerIds[seat] = null;
        room.readySeats[seat] = true;
      }
      this.socketRoom.delete(ws);
      this.socketAuthTokens.delete(ws);
      this.socketAccountIds.delete(ws);
      if (room.sockets.every((socket) => socket === null)) {
        this.destroyRoom(room);
        return;
      }
      if (
        hostChanged || !this.isRoundInProgress(room)
      ) {
        this.broadcastPlayers(room);
      }
      if (room.engine || room.gomoku || room.loveLetter) this.publishState(room);
    }
  }

  leave(ws: WebSocket) {
    this.disconnect(ws);
  }

  async handleMessage(ws: WebSocket, raw: string) {
    const msg = JSON.parse(raw);
    switch (msg.type) {
      case "CREATE": {
        const identity = await this.resolveMessageIdentity(msg);
        if (!identity) {
          send(ws, {
            type: "ERROR",
            code: "AUTH_REQUIRED",
            message: "登录已失效，请重新登录",
          });
          break;
        }
        if (identity.mustChangePassword) {
          sendError(ws, "PASSWORD_CHANGE_REQUIRED", "请先修改临时密码");
          break;
        }
        this.create(
          ws,
          identity.name,
          String(msg.difficulty ?? "MEDIUM") as Difficulty,
          Number(msg.aiCount ?? 2),
          identity.playerId,
          identity.isAuthenticated,
          Number(
            msg.actionTimeoutSeconds ?? DEFAULT_ACTION_TIMEOUT_SECONDS,
          ),
          this.resolveGameType(msg),
        );
        this.bindSocketIdentity(
          ws,
          msg,
          identity.playerId,
          identity.isAuthenticated,
        );
        break;
      }
      case "JOIN": {
        const identity = await this.resolveMessageIdentity(msg);
        if (!identity) {
          send(ws, {
            type: "ERROR",
            code: "AUTH_REQUIRED",
            message: "登录已失效，请重新登录",
          });
          break;
        }
        if (identity.mustChangePassword) {
          sendError(ws, "PASSWORD_CHANGE_REQUIRED", "请先修改临时密码");
          break;
        }
        const result = this.join(
          ws,
          String(msg.room),
          identity.name,
          identity.playerId,
          identity.isAuthenticated,
        );
        if (!result.ok) {
          sendError(
            ws,
            result.code ?? "INVALID_ACTION",
            result.message ?? "加入房间失败",
          );
        } else {
          this.bindSocketIdentity(
            ws,
            msg,
            identity.playerId,
            identity.isAuthenticated,
          );
        }
        break;
      }
      case "RECONNECT": {
        const identity = await this.resolveAuthenticatedMessageIdentity(msg);
        if (!identity) {
          sendError(ws, "AUTH_REQUIRED", "登录已失效，请重新登录");
          break;
        }
        if (identity.mustChangePassword) {
          sendError(ws, "PASSWORD_CHANGE_REQUIRED", "请先修改临时密码");
          break;
        }
        const result = this.reconnect(
          ws,
          String(msg.room),
          String(msg.token ?? ""),
          identity.playerId,
        );
        if (!result.ok) {
          sendError(
            ws,
            result.code ?? "RESUME_DENIED",
            result.message ?? "恢复房间失败",
          );
        } else {
          this.bindSocketIdentity(ws, msg, identity.playerId, true);
        }
        break;
      }
      case "START":
        if (!await this.requireSocketIdentity(ws)) break;
        this.start(ws);
        break;
      case "RESTART":
        if (!await this.requireSocketIdentity(ws)) break;
        this.restart(ws);
        break;
      case "READY":
        if (!await this.requireSocketIdentity(ws)) break;
        this.setReady(ws, Boolean(msg.ready));
        break;
      case "DISBAND":
        if (!await this.requireSocketIdentity(ws)) break;
        this.disband(ws);
        break;
      case "QUICK_MESSAGE":
        if (!await this.requireSocketIdentity(ws)) break;
        this.sendQuickMessage(ws, String(msg.messageId ?? ""));
        break;
      case "THROW_ITEM":
        if (!await this.requireSocketIdentity(ws)) break;
        this.sendThrowItem(
          ws,
          Number(msg.targetSeat),
          String(msg.item ?? ""),
        );
        break;
      case "LEAVE":
        if (!await this.requireSocketIdentity(ws)) break;
        this.leave(ws);
        break;
      case "ACTION":
        if (!await this.requireSocketIdentity(ws)) break;
        this.action(ws, Number(msg.seat), msg.action as RemoteAction);
        break;
      case "GOMOKU_MOVE": {
        if (!await this.requireSocketIdentity(ws)) break;
        const room = this.getRoomBySocket(ws);
        const seat = room?.sockets.findIndex((socket) => socket === ws) ?? -1;
        this.action(ws, seat, {
          kind: "MOVE",
          row: Number(msg.row),
          col: Number(msg.col),
        });
        break;
      }
      case "PING":
        send(ws, { type: "PONG" });
        break;
    }
  }

  private async resolveMessageIdentity(
    msg: Record<string, unknown>,
  ): Promise<
    {
      playerId: string;
      name: string;
      isAuthenticated: boolean;
      mustChangePassword: boolean;
    } | null
  > {
    const authToken = String(msg.authToken ?? "").trim();
    if (authToken) {
      const account = await resolveSession(authToken);
      return account
        ? {
          playerId: account.accountId,
          name: account.nickname,
          isAuthenticated: true,
          mustChangePassword: account.mustChangePassword,
        }
        : null;
    }
    // Keep old APKs usable during the migration. New clients always send authToken.
    return {
      playerId: String(msg.playerId ?? ""),
      name: String(msg.name ?? "玩家"),
      isAuthenticated: false,
      mustChangePassword: false,
    };
  }

  private resolveGameType(message: Record<string, unknown>): GameType {
    const value = String(message.gameType ?? message.game_mode ?? "")
      .trim()
      .toUpperCase();
    if (value === "GOMOKU" || value === "WUZIQI" || value === "FIVE_IN_A_ROW") return "GOMOKU";
    if (value === "LOVE_LETTER" || value === "LOVELETTER" || value === "情书") return "LOVE_LETTER";
    return "DOUDIZHU";
  }

  private async resolveAuthenticatedMessageIdentity(
    msg: Record<string, unknown>,
  ): Promise<
    { playerId: string; name: string; mustChangePassword: boolean } | null
  > {
    const authToken = String(msg.authToken ?? "").trim();
    if (!authToken) return null;
    const account = await resolveSession(authToken);
    return account
      ? {
        playerId: account.accountId,
        name: account.nickname,
        mustChangePassword: account.mustChangePassword,
      }
      : null;
  }

  private bindSocketIdentity(
    ws: WebSocket,
    msg: Record<string, unknown>,
    accountId: string,
    isAuthenticated: boolean,
  ) {
    if (!isAuthenticated) return;
    const authToken = String(msg.authToken ?? "").trim();
    if (!authToken) return;
    this.socketAuthTokens.set(ws, authToken);
    this.socketAccountIds.set(ws, accountId);
  }

  private async requireSocketIdentity(ws: WebSocket): Promise<boolean> {
    const authToken = this.socketAuthTokens.get(ws);
    if (!authToken) {
      // Old APKs may still use an anonymous room connection during migration.
      return true;
    }
    const expectedAccountId = this.socketAccountIds.get(ws);
    const account = await resolveSession(authToken);
    if (account && account.accountId === expectedAccountId) return true;

    sendError(ws, "AUTH_REQUIRED", "登录已失效，请重新登录");
    this.disconnect(ws);
    return false;
  }

  private getRoomBySocket(ws: WebSocket): Room | null {
    const code = this.socketRoom.get(ws);
    return code ? this.rooms.get(code) ?? null : null;
  }

  private touchRoom(room: Room) {
    room.lastActivityAt = Date.now();
    room.noOpenConnectionSince = null;
  }

  private hasOpenHumanConnection(room: Room): boolean {
    return room.sockets
      .slice(0, this.seatCount(room))
      .some((socket, seat) => {
        const playerId = room.playerIds[seat];
        const participant = playerId ? room.participants.get(playerId) : null;
        return participant?.isAi !== true && socket?.readyState === WebSocket.OPEN;
      });
  }

  private sendRoom(room: Room, seat: number, ws: WebSocket) {
    send(ws, {
      type: "ROOM",
      room: room.code,
      yourSeat: seat,
      players: room.names.slice(0, this.seatCount(room)),
      hostSeat: room.hostSeat,
      resumeToken: room.resumeTokens[seat],
      aiCount: room.aiCount,
      requiredHumans: this.requiredHumans(room),
      gameType: room.gameType,
      game_mode: room.gameType === "GOMOKU"
        ? "gomoku"
        : room.gameType === "LOVE_LETTER" ? "love-letter" : "doudizhu",
      difficulty: room.difficulty,
      actionTimeoutSeconds: room.actionTimeoutMs / 1_000,
    });
  }

  private broadcastPlayers(room: Room) {
    room.sockets.slice(0, this.seatCount(room)).forEach((ws, seat) => {
      if (ws) this.sendRoom(room, seat, ws);
    });
  }

  private broadcastState(room: Room) {
    if (room.loveLetter) {
      const seatCount = this.seatCount(room);
      room.sockets.slice(0, seatCount).forEach((ws, seat) => {
        if (ws) send(ws, {
          type: "STATE",
          state: {
            ...serializeLoveLetterState(room.loveLetter!.state, seat, room, room.actionDeadlineAt),
            actionDeadlineAt: room.actionDeadlineAt,
            leaderboard: this.buildLeaderboard(room),
            roundHistory: room.roundHistory,
            readySeats: room.readySeats,
            revision: room.revision,
            lastEvent: room.lastEvent,
          },
        });
      });
      return;
    }
    if (room.gomoku) {
      const seatCount = this.seatCount(room);
      const connectedSeats = room.sockets
        .slice(0, seatCount)
        .map((socket) => socket !== null);
      const aiSeats = room.playerIds.slice(0, seatCount).map(
        (playerId) => playerId
          ? room.participants.get(playerId)?.isAi === true
          : false,
      );
      const scores = room.playerIds.slice(0, seatCount).map(
        (playerId) => playerId ? room.participants.get(playerId)?.score ?? 0 : 0,
      );
      room.sockets.slice(0, seatCount).forEach((ws) => {
        if (ws) {
          send(ws, {
            type: "STATE",
            state: serializeGomokuState(
              room.gomoku!.state,
              room.names,
              connectedSeats,
              aiSeats,
              scores,
              room.playerIds,
              {
                leaderboard: this.buildLeaderboard(room),
                roundHistory: room.roundHistory,
                readySeats: room.readySeats,
                revision: room.revision,
                lastEvent: room.lastEvent,
              },
              room.actionDeadlineAt,
            ),
          });
        }
      });
      return;
    }
    if (!room.engine) return;
    room.sockets.forEach((ws, seat) => {
      if (ws) {
        send(ws, {
          type: "STATE",
          state: serializeState(
            room.engine!.state,
            seat,
            room.actionDeadlineAt,
            {
              leaderboard: this.buildLeaderboard(room),
              roundHistory: room.roundHistory,
              readySeats: room.readySeats,
              revision: room.revision,
              lastEvent: room.lastEvent,
            },
          ),
        });
      }
    });
  }

  private applyAction(room: Room, seat: number, action: RemoteAction) {
    const engine = room.engine;
    if (!engine) return;
    if (engine.state.turnIndex !== seat) return;
    try {
      const eventType = this.actionEventType(engine.state, action);
      let playPattern: Pattern | undefined;
      if (action.kind === "CALL") {
        engine.submitCall(action.want);
      } else if (action.kind === "PLAY") {
        playPattern = engine.submitPlay(parseCards(action.cards));
      } else {
        engine.submitPass();
      }
      this.touchRoom(room);
      this.markEvent(room, eventType, seat, false, playPattern);
      this.afterEngineChange(room);
    } catch (err) {
      const ws = room.sockets[seat];
      if (ws) {
        sendError(
          ws,
          "INVALID_ACTION",
          err instanceof Error ? err.message : String(err),
        );
      }
    }
  }

  private applyGomokuMove(
    room: Room,
    seat: number,
    row: number,
    col: number,
    automatic = false,
  ) {
    const gomoku = room.gomoku;
    if (!gomoku) return;
    try {
      gomoku.submitMove(seat, row, col);
      this.touchRoom(room);
      this.markEvent(room, "GOMOKU_MOVE", seat, automatic, undefined, row, col);
      if (gomoku.state.phase === "FINISHED") {
        this.recordFinishedGomokuRound(room);
      }
      this.publishState(room);
    } catch (err) {
      const ws = room.sockets[seat];
      if (ws) {
        sendError(
          ws,
          "INVALID_ACTION",
          err instanceof Error ? err.message : String(err),
        );
      }
    }
  }

  private afterEngineChange(room: Room) {
    if (room.engine?.state.phase === "FINISHED") {
      this.recordFinishedRound(room);
    } else {
      this.syncSeatIdentity(room);
    }
    this.publishState(room);
  }

  private publishState(room: Room) {
    this.cancelTurnTimer(room);
    if (room.loveLetter) {
      if (room.loveLetter.state.phase !== "FINISHED") {
        const version = room.turnVersion;
        const timeoutMs = this.options.actionTimeoutMs ?? room.actionTimeoutMs;
        room.actionDeadlineAt = Date.now() + timeoutMs;
        const timer = setTimeout(() => this.handleLoveLetterTurnTimeout(room, version), timeoutMs);
        room.turnTimer = timer;
        Deno.unrefTimer(timer);
      }
      this.broadcastState(room);
      this.scheduleLoveLetterAi(room);
      return;
    }
    if (room.gomoku) {
      if (room.gomoku.state.phase !== "FINISHED") {
        const version = room.turnVersion;
        const timeoutMs = this.options.actionTimeoutMs ?? room.actionTimeoutMs;
        room.actionDeadlineAt = Date.now() + timeoutMs;
        const timer = setTimeout(
          () => this.handleGomokuTurnTimeout(room, version),
          timeoutMs,
        );
        room.turnTimer = timer;
        Deno.unrefTimer(timer);
      }
      this.broadcastState(room);
      this.scheduleGomokuAi(room);
      return;
    }
    const engine = room.engine;
    if (!engine) return;
    if (engine.state.phase !== "FINISHED" && engine.isHumanTurn()) {
      const version = room.turnVersion;
      const timeoutMs = this.options.actionTimeoutMs ?? room.actionTimeoutMs;
      room.actionDeadlineAt = Date.now() + timeoutMs;
      const timer = setTimeout(
        () => this.handleTurnTimeout(room, version),
        timeoutMs,
      );
      room.turnTimer = timer;
      Deno.unrefTimer(timer);
    }
    this.broadcastState(room);
    if (engine.isAiTurn()) this.scheduleAi(room);
  }

  private handleGomokuTurnTimeout(room: Room, version: number) {
    if (
      room.turnVersion !== version ||
      !room.gomoku ||
      room.gomoku.state.phase !== "PLAYING"
    ) return;
    const seat = room.gomoku.state.turnIndex;
    const playerId = room.playerIds[seat];
    if (playerId && room.participants.get(playerId)?.isAi === true) return;
    const move = chooseGomokuAiMove(room.gomoku.state, seat);
    if (move) {
      this.applyGomokuMove(room, seat, move.row, move.col, true);
    }
  }

  private handleLoveLetterTurnTimeout(room: Room, version: number) {
    if (
      room.turnVersion !== version ||
      !room.loveLetter ||
      room.loveLetter.state.phase !== "PLAYING"
    ) return;
    const seat = room.loveLetter.state.turnIndex;
    const playerId = room.playerIds[seat];
    if (playerId && room.participants.get(playerId)?.isAi === true) return;
    const action = this.chooseLoveLetterAiAction(room);
    if (!action) return;
    try {
      room.loveLetter.play(seat, action);
      this.touchRoom(room);
      this.markEvent(room, "LOVE_LETTER_PLAY", seat, true);
      this.publishState(room);
    } catch (error) {
      console.error("情书超时操作失败", error);
    }
  }

  private scheduleLoveLetterAi(room: Room) {
    const engine = room.loveLetter;
    if (!engine || engine.state.phase !== "PLAYING" || room.aiRunning) return;
    const seat = engine.state.turnIndex;
    const playerId = room.playerIds[seat];
    if (!playerId || room.participants.get(playerId)?.isAi !== true) return;
    room.aiRunning = true;
    const timer = setTimeout(() => {
      try {
        if (room.loveLetter?.state.phase !== "PLAYING" || room.loveLetter.state.turnIndex !== seat) return;
        const action = this.chooseLoveLetterAiAction(room);
        if (!action) return;
        room.loveLetter.play(seat, action);
        this.touchRoom(room);
        this.markEvent(room, "LOVE_LETTER_PLAY", seat, true);
        if (room.loveLetter) this.recordFinishedLoveLetterRound(room);
        this.publishState(room);
      } catch (error) {
        console.error("情书 AI 出牌失败", error);
      } finally {
        room.aiRunning = false;
      }
    }, this.options.aiDelayMs ?? 700);
    Deno.unrefTimer(timer);
  }

  private chooseLoveLetterAiAction(room: Room): LoveLetterAction | null {
    const engine = room.loveLetter;
    if (!engine) return null;
    const seat = engine.state.turnIndex;
    const player = engine.state.players[seat];
    if (!player?.hand.length) return null;
    const forced = player.hand.find((card) => card.name === "COUNTESS" && player.hand.some((item) => item.name === "KING" || item.name === "PRINCE"));
    const card = forced ?? [...player.hand].sort((left, right) => right.value - left.value)[0];
    const target = engine.state.players.find((candidate) => candidate.seat !== seat && candidate.alive && !candidate.protected);
    const action: LoveLetterAction = { cardId: card.id };
    if (["GUARD", "PRIEST", "BARON", "KING"].includes(card.name) && target) action.targetSeat = target.seat;
    if (card.name === "GUARD") action.guessValue = 2;
    return action;
  }

  private recordFinishedLoveLetterRound(room: Room) {
    const state = room.loveLetter?.state;
    if (!state || room.lastRecordedGameNumber === state.gameNumber) return;
    room.lastRecordedGameNumber = state.gameNumber;
    const winner = state.winnerIndex;
    const scores = room.playerIds.slice(0, this.seatCount(room)).flatMap((playerId, seat) => {
      if (!playerId) return [];
      const participant = room.participants.get(playerId);
      if (!participant) return [];
      const delta = winner === seat ? 1 : 0;
      participant.score += delta;
      return [{ playerId, name: participant.name, delta, totalScore: participant.score, isAi: participant.isAi }];
    });
    room.roundHistory.push({
      gameNumber: state.gameNumber,
      winnerPlayerId: winner === null ? null : room.playerIds[winner],
      landlordPlayerId: null,
      multiplier: 1,
      scores,
    });
    this.resetReadySeats(room);
    if (this.options.persistStats !== false) {
      void recordGame(scores.map((score) => ({
        playerId: score.playerId,
        name: score.name,
        delta: score.delta,
        isAi: score.isAi || !room.participants.get(score.playerId)?.isAuthenticated,
      })), {
        matchId: room.matchId,
        roomCode: room.code,
        startedAt: room.startedAt,
        gameType: "LOVE_LETTER",
        round: {
          gameNumber: state.gameNumber,
          finishedAt: Date.now(),
          winnerPlayerId: winner === null ? null : room.playerIds[winner],
          landlordPlayerId: null,
          multiplier: 1,
          players: scores.map((score) => ({ playerId: score.playerId, name: score.name, delta: score.delta, isAi: score.isAi })),
        },
      }).catch((error) => console.error("记录情书战绩失败", error));
    }
  }

  private scheduleGomokuAi(room: Room) {
    const gomoku = room.gomoku;
    if (!gomoku || gomoku.state.phase !== "PLAYING" || room.aiRunning) return;
    const aiSeat = gomoku.state.turnIndex;
    const playerId = room.playerIds[aiSeat];
    if (!playerId || room.participants.get(playerId)?.isAi !== true) return;

    room.aiRunning = true;
    const timer = setTimeout(() => {
      try {
        if (
          room.gomoku?.state.phase !== "PLAYING" ||
          room.gomoku.state.turnIndex !== aiSeat
        ) {
          return;
        }
        const move = chooseGomokuAiMove(room.gomoku.state, aiSeat);
        if (move) {
          this.applyGomokuMove(room, aiSeat, move.row, move.col, true);
        }
      } finally {
        room.aiRunning = false;
      }
    }, this.options.aiDelayMs ?? 650);
    Deno.unrefTimer(timer);
  }

  private cancelTurnTimer(room: Room) {
    room.turnVersion += 1;
    room.actionDeadlineAt = null;
    if (room.turnTimer !== null) {
      clearTimeout(room.turnTimer);
      room.turnTimer = null;
    }
  }

  private handleTurnTimeout(room: Room, version: number) {
    if (room.turnVersion !== version || !room.engine?.isHumanTurn()) return;
    const engine = room.engine;
    const timedOutPlayer = engine.state.seats[engine.state.turnIndex].name;
    let timeoutAction = "";
    try {
      if (engine.state.phase === "BIDDING") {
        const eventType = this.actionEventType(engine.state, {
          kind: "CALL",
          want: false,
        });
        const seat = engine.state.turnIndex;
        engine.submitCall(false);
        this.markEvent(room, eventType, seat, true);
        timeoutAction = "默认不抢";
      } else if (engine.canPass()) {
        const seat = engine.state.turnIndex;
        engine.submitPass();
        this.markEvent(room, "PASS", seat, true);
        timeoutAction = "默认不出";
      } else {
        const seat = engine.state.turnIndex;
        const action = decide(engine.state, room.difficulty);
        if (action.type !== "play") {
          throw new Error("服务器未找到可代出的合法牌");
        }
        const playPattern = engine.submitPlay(action.cards);
        this.markEvent(room, "PLAY", seat, true, playPattern);
        timeoutAction = "由服务器代出";
      }
      engine.state = {
        ...engine.state,
        message: `${timedOutPlayer} 超时，${timeoutAction}`,
      };
      this.afterEngineChange(room);
    } catch (err) {
      const ws = room.sockets[engine.state.turnIndex];
      if (ws) {
        sendError(
          ws,
          "INVALID_ACTION",
          err instanceof Error ? err.message : String(err),
        );
      }
      this.publishState(room);
    }
  }

  private async scheduleAi(room: Room) {
    if (room.aiRunning) return;
    const engine = room.engine;
    if (!engine || !engine.isAiTurn()) return;
    room.aiRunning = true;
    try {
      while (engine.isAiTurn() && engine.state.phase !== "FINISHED") {
        await sleep(this.options.aiDelayMs ?? 900);
        if (!room.engine || !engine.isAiTurn()) return;
        const action: AiAction = decide(engine.state, room.difficulty);
        const seat = engine.state.turnIndex;
        if (action.type === "call") {
          const eventType = this.actionEventType(engine.state, {
            kind: "CALL",
            want: action.want,
          });
          engine.submitCall(action.want);
          this.markEvent(room, eventType, seat, true);
        } else if (action.type === "play") {
          const playPattern = engine.submitPlay(action.cards);
          this.markEvent(room, "PLAY", seat, true, playPattern);
        } else {
          engine.submitPass();
          this.markEvent(room, "PASS", seat, true);
        }
        this.afterEngineChange(room);
      }
    } finally {
      room.aiRunning = false;
    }
  }

  private syncSeatIdentity(room: Room) {
    const engine = room.engine;
    if (!engine) return;
    engine.state = {
      ...engine.state,
      seats: engine.state.seats.map((seat, index) => {
        const playerId = room.playerIds[index];
        const participant = playerId ? room.participants.get(playerId) : null;
        if (!participant) return seat;
        const isHumanSeat = !participant.isAi;
        const connected = isHumanSeat && room.sockets[index] !== null;
        return {
          ...seat,
          name: participant.name,
          isHuman: isHumanSeat ? connected : false,
          isAway: isHumanSeat ? !connected : false,
          score: participant.score,
        };
      }),
    };
  }

  private actionEventType(
    state: EngineState,
    action: RemoteAction,
  ): string {
    if (action.kind === "PLAY") return "PLAY";
    if (action.kind === "PASS") return "PASS";
    if (action.kind === "MOVE") return "GOMOKU_MOVE";
    if (action.kind !== "CALL") return "PLAY";
    if (!action.want) {
      return state.bidCandidateIndex === null ? "NOT_CALL" : "NOT_ROB";
    }
    return state.bidCandidateIndex === null ? "CALL" : "ROB";
  }

  private markEvent(
    room: Room,
    type: string,
    seat: number | null,
    automatic: boolean,
    playPattern?: Pattern,
    row?: number,
    col?: number,
  ) {
    room.revision += 1;
    room.lastEvent = {
      id: `${room.code}:${this.gameNumber(room)}:${room.revision}`,
      type,
      seat,
      automatic,
      at: Date.now(),
      ...(typeof row === "number" && typeof col === "number"
        ? { row, col }
        : {}),
      ...(type === "PLAY" && playPattern
        ? {
          comboType: playPattern.type,
          primaryRank: playPattern.primary,
          comboLength: playPattern.length,
        }
        : {}),
    };
  }

  private createParticipant(
    name: string,
    isAi: boolean,
    requestedId = "",
    isAuthenticated = false,
  ): RoomParticipant {
    return {
      id: isAi
        ? crypto.randomUUID()
        : this.normalizePlayerId(requestedId) ?? crypto.randomUUID(),
      name,
      score: 0,
      isAi,
      isAuthenticated: !isAi && isAuthenticated,
      joinedAt: Date.now(),
    };
  }

  private normalizePlayerId(requestedId: string): string | null {
    const value = requestedId.trim();
    return PLAYER_ID_PATTERN.test(value) ? value : null;
  }

  private pickNewHost(room: Room): number {
    const candidates = room.sockets
      .slice(0, this.seatCount(room))
      .map((socket, seat) => {
        const playerId = room.playerIds[seat];
        const participant = playerId ? room.participants.get(playerId) : null;
        return socket && participant?.isAi !== true ? seat : -1;
      })
      .filter((seat) => seat >= 0);
    if (candidates.length === 0) return -1;
    return candidates[Math.floor(Math.random() * candidates.length)];
  }

  private validateStartPlayers(room: Room, ws: WebSocket): boolean {
    const humanCount = room.playerIds
      .slice(0, this.seatCount(room))
      .filter((playerId) => {
        const participant = playerId ? room.participants.get(playerId) : null;
        return participant?.isAi === false;
      }).length;
    if (humanCount < 1) {
      sendError(ws, "NOT_READY", "至少需要一名真人玩家才能开始");
      return false;
    }
    return true;
  }

  private fillMissingSeatsWithAi(room: Room) {
    const seatCount = this.seatCount(room);
    for (let seat = 0; seat < seatCount; seat++) {
      if (room.playerIds[seat] !== null) continue;
      const name = room.gameType === "GOMOKU"
        ? GOMOKU_AI_NAME
        : AI_NAMES[seat] ?? `AI ${seat + 1}`;
      const ai = this.createParticipant(name, true);
      room.participants.set(ai.id, ai);
      room.playerIds[seat] = ai.id;
      room.names[seat] = ai.name;
      room.resumeTokens[seat] = null;
      room.sockets[seat] = null;
      room.readySeats[seat] = true;
    }
    room.aiCount = room.playerIds
      .slice(0, seatCount)
      .filter((playerId) => playerId !== null &&
        room.participants.get(playerId)?.isAi === true).length;
  }

  private buildLeaderboard(room: Room): RoomLeaderboardEntry[] {
    return [...room.participants.values()]
      .map((participant) => {
        const seatIndex = room.playerIds.indexOf(participant.id);
        return {
          playerId: participant.id,
          name: participant.name,
          score: participant.score,
          isAi: participant.isAi,
          seatIndex: seatIndex >= 0 ? seatIndex : null,
          isAway: seatIndex >= 0 && !participant.isAi &&
            room.sockets[seatIndex] === null,
          isActive: seatIndex >= 0,
        };
      })
      .sort((left, right) =>
        right.score - left.score ||
        (room.participants.get(left.playerId)?.joinedAt ?? 0) -
          (room.participants.get(right.playerId)?.joinedAt ?? 0)
      );
  }

  private toAdminRoomSnapshot(room: Room): AdminRoomSnapshot {
    const seatCount = this.seatCount(room);
    const seats = Array.from(
      { length: seatCount },
      (_, seat): AdminRoomSeatSnapshot => {
      const playerId = room.playerIds[seat];
      const participant = playerId ? room.participants.get(playerId) : null;
      const isAi = participant?.isAi === true;
      const isConnected = !isAi && room.sockets[seat] !== null;
      return {
        seat,
        name: participant?.name ?? "等待加入",
        isAi,
        isConnected,
        isAway: Boolean(
          participant && !isAi && this.isRoundInProgress(room) && !isConnected,
        ),
        isHost: seat === room.hostSeat,
      };
      },
    );
    return {
      roomCode: room.code,
      createdAt: room.startedAt,
      phase: this.phase(room),
      gameType: room.gameType,
      gameNumber: this.gameNumber(room),
      difficulty: room.difficulty,
      aiCount: room.aiCount,
      actionTimeoutSeconds: room.actionTimeoutMs / 1_000,
      connectedHumanCount: room.sockets
        .slice(0, seatCount)
        .filter((socket, seat) => {
          const playerId = room.playerIds[seat];
          return socket !== null &&
            room.participants.get(playerId ?? "")?.isAi !== true;
        }).length,
      occupiedHumanCount: room.playerIds
        .slice(0, seatCount)
        .filter((playerId) =>
          playerId !== null &&
          room.participants.get(playerId)?.isAi !== true
        ).length,
      hostSeat: room.hostSeat,
      hostName: room.hostSeat >= 0 ? seats[room.hostSeat]?.name ?? null : null,
      seats,
    };
  }

  private recordFinishedRound(room: Room) {
    const state = room.engine?.state;
    if (!state || room.lastRecordedGameNumber === state.gameNumber) return;
    room.lastRecordedGameNumber = state.gameNumber;

    const scores = state.seats.flatMap((seat) => {
      const playerId = room.playerIds[seat.index];
      if (!playerId) return [];
      const participant = room.participants.get(playerId);
      if (!participant) return [];
      participant.score = seat.score;
      return [{
        playerId,
        name: participant.name,
        delta: state.scoreDelta[seat.index] ?? 0,
        totalScore: participant.score,
        isAi: participant.isAi,
      }];
    });
    room.roundHistory.push({
      gameNumber: state.gameNumber,
      winnerPlayerId: state.winnerIndex === null
        ? null
        : room.playerIds[state.winnerIndex],
      landlordPlayerId: state.landlordIndex === null
        ? null
        : room.playerIds[state.landlordIndex],
      multiplier: state.multiplier,
      scores,
    });
    this.resetReadySeats(room);

    if (this.options.persistStats !== false) {
      const playerScores = scores.map((score) => ({
        playerId: score.playerId,
        name: score.name,
        delta: score.delta,
        isAi: score.isAi || !room.participants.get(score.playerId)
          ?.isAuthenticated,
      }));
      void recordGame(playerScores, {
        matchId: room.matchId,
        roomCode: room.code,
        startedAt: room.startedAt,
        gameType: "DOUDIZHU",
        round: {
          gameNumber: state.gameNumber,
          finishedAt: Date.now(),
          winnerPlayerId: state.winnerIndex === null
            ? null
            : room.playerIds[state.winnerIndex],
          landlordPlayerId: state.landlordIndex === null
            ? null
            : room.playerIds[state.landlordIndex],
          multiplier: state.multiplier,
          players: scores.map((score) => ({
            playerId: score.playerId,
            name: score.name,
            delta: score.delta,
            isAi: score.isAi,
          })),
        },
      }).catch((error) => console.error("记录玩家累计分和最近战绩失败", error));
    }
  }

  private recordFinishedGomokuRound(room: Room) {
    const state = room.gomoku?.state;
    if (!state || room.lastRecordedGameNumber === state.gameNumber) return;
    room.lastRecordedGameNumber = state.gameNumber;

    const scores = [0, 1].flatMap((seat) => {
      const playerId = room.playerIds[seat];
      if (!playerId) return [];
      const participant = room.participants.get(playerId);
      if (!participant) return [];
      const delta = state.winnerIndex === null
        ? 0
        : state.winnerIndex === seat ? 1 : -1;
      participant.score += delta;
      return [{
        playerId,
        name: participant.name,
        delta,
        totalScore: participant.score,
        isAi: participant.isAi,
      }];
    });
    room.roundHistory.push({
      gameNumber: state.gameNumber,
      winnerPlayerId: state.winnerIndex === null
        ? null
        : room.playerIds[state.winnerIndex],
      landlordPlayerId: null,
      multiplier: 1,
      scores,
    });
    this.resetReadySeats(room);
    if (this.options.persistStats !== false) {
      void recordGame(scores.map((score) => ({
        playerId: score.playerId,
        name: score.name,
        delta: score.delta,
        isAi: score.isAi || !room.participants.get(score.playerId)
          ?.isAuthenticated,
      })), {
        matchId: room.matchId,
        roomCode: room.code,
        startedAt: room.startedAt,
        gameType: "GOMOKU",
        round: {
          gameNumber: state.gameNumber,
          finishedAt: Date.now(),
          winnerPlayerId: state.winnerIndex === null
            ? null
            : room.playerIds[state.winnerIndex],
          landlordPlayerId: null,
          multiplier: 1,
          players: scores.map((score) => ({
            playerId: score.playerId,
            name: score.name,
            delta: score.delta,
            isAi: score.isAi,
          })),
        },
      }).catch((error) => console.error("记录五子棋统计失败", error));
    }
  }

  private destroyRoom(room: Room) {
    this.cancelTurnTimer(room);
    for (const socket of room.sockets) {
      if (!socket) continue;
      this.socketRoom.delete(socket);
      this.socketAuthTokens.delete(socket);
      this.socketAccountIds.delete(socket);
    }
    this.rooms.delete(room.code);
  }

  private resetReadySeats(room: Room) {
    room.readySeats = Array.from({ length: this.seatCount(room) }, (_, seat) => {
      const participant = room.playerIds[seat]
        ? room.participants.get(room.playerIds[seat]!)
        : null;
      if (!participant || participant.isAi || room.sockets[seat] === null) {
        return true;
      }
      return false;
    });
  }

  private requiredHumans(room: Room): number {
    // 旧客户端仍读取这个字段；现在它表示等候区最多可加入的真人座位数。
    return this.seatCount(room);
  }

  private seatCount(room: Room): number {
    return this.seatCountForGame(room.gameType);
  }

  private seatCountForGame(gameType: GameType): number {
    if (gameType === "GOMOKU") return 2;
    if (gameType === "LOVE_LETTER") return 4;
    return 3;
  }

  private isRoundInProgress(room: Room): boolean {
    if (room.gameType === "GOMOKU") return room.gomoku !== null && room.gomoku.state.phase === "PLAYING";
    if (room.gameType === "LOVE_LETTER") return room.loveLetter !== null && room.loveLetter.state.phase === "PLAYING";
    return room.engine !== null && room.engine.state.phase !== "FINISHED";
  }

  private isRoundFinished(room: Room): boolean {
    if (room.gameType === "GOMOKU") return room.gomoku?.state.phase === "FINISHED";
    if (room.gameType === "LOVE_LETTER") return room.loveLetter?.state.phase === "FINISHED";
    return room.engine?.state.phase === "FINISHED";
  }

  private gameNumber(room: Room): number {
    return room.loveLetter?.state.gameNumber ?? room.gomoku?.state.gameNumber ?? room.engine?.state.gameNumber ?? 0;
  }

  private phase(room: Room): AdminRoomSnapshot["phase"] {
    return room.loveLetter?.state.phase ?? room.gomoku?.state.phase ?? room.engine?.state.phase ?? "WAITING";
  }
}
