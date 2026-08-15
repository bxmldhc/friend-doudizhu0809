import { Difficulty, DoudizhuEngine, EngineState } from "./engine.ts";
import { AiAction, decide } from "./ai.ts";
import {
  RemoteAction,
  RoomLeaderboardEntry,
  RoomRoundSummary,
  serializeState,
  ServerGameEvent,
} from "./protocol.ts";
import { recordGame } from "./store.ts";
import { parseCards } from "./cards.ts";
import { resolveSession } from "./auth.ts";
import { Pattern } from "./hand.ts";

interface Room {
  code: string;
  matchId: string;
  startedAt: number;
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
  difficulty: Difficulty;
  aiCount: number;
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
  gameNumber: number;
  difficulty: Difficulty;
  aiCount: number;
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

interface RoomOperationResult {
  ok: boolean;
  code?: string;
  message?: string;
}

const AI_NAMES = ["小智", "阿强", "老K"];
const PLAYER_ID_PATTERN = /^[A-Za-z0-9_-]{8,80}$/;

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
    } = {},
  ) {}

  create(
    ws: WebSocket,
    name: string,
    difficulty: Difficulty,
    requestedAiCount: number,
    requestedPlayerId = "",
    isAuthenticated = false,
  ): string {
    const aiCount = Math.max(
      0,
      Math.min(
        2,
        Number.isFinite(requestedAiCount) ? Math.floor(requestedAiCount) : 2,
      ),
    );
    let code = "";
    do {
      code = String(Math.floor(100000 + Math.random() * 900000));
    } while (this.rooms.has(code));
    const requiredHumans = 3 - aiCount;
    const host = this.createParticipant(
      name,
      false,
      requestedPlayerId,
      isAuthenticated,
    );
    const participants = new Map<string, RoomParticipant>([[host.id, host]]);
    const playerIds: (string | null)[] = [host.id, null, null];
    const names = [name, "等待加入", "等待加入"];
    for (let seat = requiredHumans; seat < 3; seat++) {
      const ai = this.createParticipant(AI_NAMES[seat], true);
      participants.set(ai.id, ai);
      playerIds[seat] = ai.id;
      names[seat] = ai.name;
    }
    const room: Room = {
      code,
      matchId: crypto.randomUUID(),
      startedAt: Date.now(),
      sockets: [ws, null, null],
      resumeTokens: [crypto.randomUUID(), null, null],
      playerIds,
      names,
      hostSeat: 0,
      participants,
      roundHistory: [],
      lastRecordedGameNumber: 0,
      readySeats: [0, 1, 2].map((seat) => seat >= requiredHumans),
      engine: null,
      difficulty,
      aiCount,
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
    if (room.engine && room.engine.state.phase !== "FINISHED") {
      return {
        ok: false,
        code: "GAME_ALREADY_STARTED",
        message: "游戏已经开始，不能加入",
      };
    }
    const humanCount = room.sockets.filter((s) => s !== null).length;
    if (humanCount >= 3 - room.aiCount) {
      return {
        ok: false,
        code: "ROOM_FULL",
        message: "当前房间真人人数已满",
      };
    }
    const requiredHumans = 3 - room.aiCount;
    const seat = room.sockets.findIndex((s, i) =>
      i < requiredHumans && s === null
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
    this.socketRoom.set(ws, code);
    this.sendRoom(room, seat, ws);
    this.broadcastPlayers(room);
    if (room.engine) {
      this.syncSeatIdentity(room);
      this.publishState(room);
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
    this.socketRoom.set(ws, code);
    if (room.engine?.state.phase === "FINISHED") {
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
    if (!room.engine) {
      room.engine = new DoudizhuEngine({
        humanCount: 3 - room.aiCount,
        difficulty: room.difficulty,
      });
    }
    room.engine.startNewGame();
    this.markEvent(room, "ROUND_STARTED", null, false);
    this.syncSeatIdentity(room);
    this.publishState(room);
  }

  restart(ws: WebSocket) {
    const room = this.getRoomBySocket(ws);
    if (!room || !room.engine) {
      sendError(ws, "INVALID_ACTION", "当前房间没有可重开的牌局");
      return;
    }
    if (room.sockets[room.hostSeat] !== ws) {
      sendError(ws, "NOT_HOST", "只有房主可以重开");
      return;
    }
    if (room.engine.state.phase !== "FINISHED") {
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
    room.engine.startNewGame();
    this.markEvent(room, "ROUND_STARTED", null, false);
    this.resetReadySeats(room);
    this.syncSeatIdentity(room);
    this.publishState(room);
  }

  setReady(ws: WebSocket, ready: boolean) {
    const room = this.getRoomBySocket(ws);
    if (!room || !room.engine) {
      sendError(ws, "INVALID_ACTION", "当前房间没有可准备的牌局");
      return;
    }
    if (room.engine.state.phase !== "FINISHED") {
      sendError(ws, "INVALID_ACTION", "只有本局结束后才能准备");
      return;
    }
    const seat = room.sockets.findIndex((socket) => socket === ws);
    if (seat < 0 || !room.playerIds[seat]) return;
    room.readySeats[seat] = ready;
    this.broadcastState(room);
  }

  action(ws: WebSocket, seat: number, action: RemoteAction) {
    const room = this.getRoomBySocket(ws);
    if (!room || !room.engine) {
      sendError(ws, "INVALID_ACTION", "当前房间没有进行中的牌局");
      return;
    }
    if (!Number.isInteger(seat) || room.sockets[seat] !== ws) {
      sendError(ws, "INVALID_ACTION", "不能操作其他玩家的座位");
      return;
    }
    if (room.engine.state.turnIndex !== seat) {
      sendError(ws, "INVALID_ACTION", "当前还没有轮到你操作");
      return;
    }
    this.applyAction(room, seat, action);
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
      if (room.engine && room.engine.state.phase !== "FINISHED") {
        room.engine.setSeatHuman(seat, false);
        room.engine.setSeatAway(seat, true);
        // Keep the active seat and nickname visible while the server AI takes over.
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
        hostChanged || !room.engine || room.engine.state.phase === "FINISHED"
      ) {
        this.broadcastPlayers(room);
      }
      if (room.engine) this.publishState(room);
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
        this.create(
          ws,
          identity.name,
          String(msg.difficulty ?? "MEDIUM") as Difficulty,
          Number(msg.aiCount ?? 2),
          identity.playerId,
          identity.isAuthenticated,
        );
        this.bindSocketIdentity(ws, msg, identity.playerId, identity.isAuthenticated);
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
      case "LEAVE":
        if (!await this.requireSocketIdentity(ws)) break;
        this.leave(ws);
        break;
      case "ACTION":
        if (!await this.requireSocketIdentity(ws)) break;
        this.action(ws, Number(msg.seat), msg.action as RemoteAction);
        break;
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
        }
        : null;
    }
    // Keep old APKs usable during the migration. New clients always send authToken.
    return {
      playerId: String(msg.playerId ?? ""),
      name: String(msg.name ?? "玩家"),
      isAuthenticated: false,
    };
  }

  private async resolveAuthenticatedMessageIdentity(
    msg: Record<string, unknown>,
  ): Promise<{ playerId: string; name: string } | null> {
    const authToken = String(msg.authToken ?? "").trim();
    if (!authToken) return null;
    const account = await resolveSession(authToken);
    return account
      ? { playerId: account.accountId, name: account.nickname }
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

  private sendRoom(room: Room, seat: number, ws: WebSocket) {
    send(ws, {
      type: "ROOM",
      room: room.code,
      yourSeat: seat,
      players: room.names,
      hostSeat: room.hostSeat,
      resumeToken: room.resumeTokens[seat],
      aiCount: room.aiCount,
      requiredHumans: 3 - room.aiCount,
    });
  }

  private broadcastPlayers(room: Room) {
    room.sockets.forEach((ws, seat) => {
      if (ws) this.sendRoom(room, seat, ws);
    });
  }

  private broadcastState(room: Room) {
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
    const engine = room.engine;
    if (!engine) return;
    if (engine.state.phase !== "FINISHED" && engine.isHumanTurn()) {
      const version = room.turnVersion;
      const timeoutMs = this.options.actionTimeoutMs ?? 20_000;
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
    const requiredHumans = 3 - room.aiCount;
    engine.state = {
      ...engine.state,
      seats: engine.state.seats.map((seat, index) => {
        const playerId = room.playerIds[index];
        const participant = playerId ? room.participants.get(playerId) : null;
        if (!participant) return seat;
        const isHumanSeat = index < requiredHumans;
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
  ) {
    room.revision += 1;
    room.lastEvent = {
      id: `${room.code}:${room.engine?.state.gameNumber ?? 0}:${room.revision}`,
      type,
      seat,
      automatic,
      at: Date.now(),
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
      .map((socket, seat) => socket ? seat : -1)
      .filter((seat) => seat >= 0);
    if (candidates.length === 0) return -1;
    return candidates[Math.floor(Math.random() * candidates.length)];
  }

  private validateStartPlayers(room: Room, ws: WebSocket): boolean {
    const humanCount = room.sockets.filter((socket) => socket !== null).length;
    const requiredHumans = 3 - room.aiCount;
    if (humanCount < requiredHumans) {
      sendError(
        ws,
        "NOT_READY",
        `还需要加入 ${requiredHumans - humanCount} 名真人玩家`,
      );
      return false;
    }
    if (humanCount > requiredHumans) {
      sendError(ws, "NOT_READY", "房间真人数量已超过当前 AI 配置");
      return false;
    }
    return true;
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
    const requiredHumans = 3 - room.aiCount;
    const seats = [0, 1, 2].map((seat): AdminRoomSeatSnapshot => {
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
          participant && !isAi && room.engine &&
            room.engine.state.phase !== "FINISHED" && !isConnected,
        ),
        isHost: seat === room.hostSeat,
      };
    });
    return {
      roomCode: room.code,
      createdAt: room.startedAt,
      phase: room.engine?.state.phase ?? "WAITING",
      gameNumber: room.engine?.state.gameNumber ?? 0,
      difficulty: room.difficulty,
      aiCount: room.aiCount,
      connectedHumanCount: room.sockets
        .slice(0, requiredHumans)
        .filter((socket) => socket !== null).length,
      occupiedHumanCount: room.playerIds
        .slice(0, requiredHumans)
        .filter((playerId) => playerId !== null).length,
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

  private destroyRoom(room: Room) {
    this.cancelTurnTimer(room);
    this.rooms.delete(room.code);
  }

  private resetReadySeats(room: Room) {
    room.readySeats = [0, 1, 2].map((seat) => {
      const participant = room.playerIds[seat]
        ? room.participants.get(room.playerIds[seat]!)
        : null;
      if (!participant || participant.isAi || room.sockets[seat] === null) {
        return true;
      }
      return false;
    });
  }
}
