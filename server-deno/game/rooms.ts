import { DoudizhuEngine, Difficulty, EngineState } from "./engine.ts";
import { decide, AiAction } from "./ai.ts";
import {
  RemoteAction,
  RoomLeaderboardEntry,
  RoomRoundSummary,
  serializeState,
} from "./protocol.ts";
import { recordGame } from "./store.ts";
import { parseCards } from "./cards.ts";

interface Room {
  code: string;
  sockets: (WebSocket | null)[];
  resumeTokens: (string | null)[];
  playerIds: (string | null)[];
  names: string[];
  hostSeat: number;
  participants: Map<string, RoomParticipant>;
  roundHistory: RoomRoundSummary[];
  lastRecordedGameNumber: number;
  engine: DoudizhuEngine | null;
  difficulty: Difficulty;
  aiCount: number;
  aiRunning: boolean;
  actionDeadlineAt: number | null;
  turnTimer: ReturnType<typeof setTimeout> | null;
  turnVersion: number;
}

interface RoomParticipant {
  id: string;
  name: string;
  score: number;
  isAi: boolean;
  joinedAt: number;
}

const AI_NAMES = ["小智", "阿强", "老K"];

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function send(ws: WebSocket, obj: unknown) {
  if (ws.readyState === 1) {
    ws.send(JSON.stringify(obj));
  }
}

export class RoomManager {
  private rooms = new Map<string, Room>();
  private socketRoom = new WeakMap<WebSocket, string>();

  constructor(
    private options: { actionTimeoutMs?: number; aiDelayMs?: number } = {},
  ) {}

  create(ws: WebSocket, name: string, difficulty: Difficulty, requestedAiCount: number): string {
    const aiCount = Math.max(
      0,
      Math.min(2, Number.isFinite(requestedAiCount) ? Math.floor(requestedAiCount) : 2)
    );
    let code = "";
    do {
      code = String(Math.floor(100000 + Math.random() * 900000));
    } while (this.rooms.has(code));
    const requiredHumans = 3 - aiCount;
    const host = this.createParticipant(name, false);
    const participants = new Map<string, RoomParticipant>([[host.id, host]]);
    const playerIds: (string | null)[] = [host.id, null, null];
    const names = [name, "等待加入", "等待加入"];
    for (let seat = requiredHumans; seat < 3; seat++) {
      const ai = this.createParticipant(AI_NAMES[seat], true);
      participants.set(ai.id, ai);
      playerIds[seat] = ai.id;
    }
    const room: Room = {
      code,
      sockets: [ws, null, null],
      resumeTokens: [crypto.randomUUID(), null, null],
      playerIds,
      names,
      hostSeat: 0,
      participants,
      roundHistory: [],
      lastRecordedGameNumber: 0,
      engine: null,
      difficulty,
      aiCount,
      aiRunning: false,
      actionDeadlineAt: null,
      turnTimer: null,
      turnVersion: 0,
    };
    this.rooms.set(code, room);
    this.socketRoom.set(ws, code);
    this.sendRoom(room, 0, ws);
    return code;
  }

  join(ws: WebSocket, code: string, name: string): { ok: boolean; message?: string } {
    const room = this.rooms.get(code);
    if (!room) return { ok: false, message: "房间不存在" };
    if (room.engine && room.engine.state.phase !== "FINISHED") {
      return { ok: false, message: "游戏已经开始，不能加入" };
    }
    const humanCount = room.sockets.filter((s) => s !== null).length;
    if (humanCount >= 3 - room.aiCount) {
      return { ok: false, message: "当前房间真人人数已满" };
    }
    const requiredHumans = 3 - room.aiCount;
    const seat = room.sockets.findIndex((s, i) => i < requiredHumans && s === null);
    if (seat < 0) return { ok: false, message: "房间已满" };
    const participant = this.createParticipant(name, false);
    room.participants.set(participant.id, participant);
    room.sockets[seat] = ws;
    room.resumeTokens[seat] = crypto.randomUUID();
    room.playerIds[seat] = participant.id;
    room.names[seat] = name;
    this.socketRoom.set(ws, code);
    this.sendRoom(room, seat, ws);
    this.broadcastPlayers(room);
    return { ok: true };
  }

  reconnect(ws: WebSocket, code: string, token: string): { ok: boolean; message?: string } {
    const room = this.rooms.get(code);
    if (!room) return { ok: false, message: "房间不存在" };
    const seat = room.resumeTokens.findIndex((value) => value === token && value !== null);
    if (seat < 0) return { ok: false, message: "恢复凭证无效，无法认领原座位" };

    const previousSocket = room.sockets[seat];
    if (previousSocket && previousSocket !== ws) {
      this.socketRoom.delete(previousSocket);
      try {
        previousSocket.close(4001, "座位已在另一台设备恢复");
      } catch {
        // Test doubles and already-closed sockets may not implement close.
      }
    }
    room.sockets[seat] = ws;
    this.socketRoom.set(ws, code);
    if (room.engine) {
      room.engine.setSeatHuman(seat, true);
      room.engine.setSeatAway(seat, false);
    }
    this.sendRoom(room, seat, ws);
    if (room.engine) {
      if (room.engine.state.turnIndex === seat && room.engine.state.phase !== "FINISHED") {
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
    if (!room) return;
    if (room.sockets[room.hostSeat] !== ws) {
      send(ws, { type: "ERROR", message: "只有房主可以开始" });
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
    this.syncSeatIdentity(room);
    this.publishState(room);
  }

  restart(ws: WebSocket) {
    const room = this.getRoomBySocket(ws);
    if (!room || !room.engine) return;
    if (room.sockets[room.hostSeat] !== ws) {
      send(ws, { type: "ERROR", message: "只有房主可以重开" });
      return;
    }
    if (!this.validateStartPlayers(room, ws)) return;
    room.engine.startNewGame();
    this.syncSeatIdentity(room);
    this.publishState(room);
  }

  action(ws: WebSocket, seat: number, action: RemoteAction) {
    const room = this.getRoomBySocket(ws);
    if (!room || !room.engine) return;
    if (room.sockets[seat] !== ws) return;
    this.applyAction(room, seat, action);
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
      }
      this.socketRoom.delete(ws);
      if (room.sockets.every((socket) => socket === null)) {
        this.destroyRoom(room);
        return;
      }
      if (hostChanged || !room.engine || room.engine.state.phase === "FINISHED") {
        this.broadcastPlayers(room);
      }
      if (room.engine) this.publishState(room);
    }
  }

  handleMessage(ws: WebSocket, raw: string) {
    const msg = JSON.parse(raw);
    switch (msg.type) {
      case "CREATE":
        this.create(
          ws,
          String(msg.name ?? "玩家1"),
          String(msg.difficulty ?? "MEDIUM") as Difficulty,
          Number(msg.aiCount ?? 2)
        );
        break;
      case "JOIN": {
        const result = this.join(ws, String(msg.room), String(msg.name ?? "玩家"));
        if (!result.ok) send(ws, { type: "ERROR", message: result.message });
        break;
      }
      case "RECONNECT": {
        const result = this.reconnect(ws, String(msg.room), String(msg.token ?? ""));
        if (!result.ok) {
          send(ws, {
            type: "ERROR",
            code: "RESUME_DENIED",
            message: result.message,
          });
        }
        break;
      }
      case "START":
        this.start(ws);
        break;
      case "RESTART":
        this.restart(ws);
        break;
      case "ACTION":
        this.action(ws, Number(msg.seat), msg.action as RemoteAction);
        break;
      case "PING":
        send(ws, { type: "PONG" });
        break;
    }
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
      if (action.kind === "CALL") {
        engine.submitCall(action.want);
      } else if (action.kind === "PLAY") {
        engine.submitPlay(parseCards(action.cards));
      } else {
        engine.submitPass();
      }
      this.afterEngineChange(room);
    } catch (err) {
      const ws = room.sockets[seat];
      if (ws) send(ws, { type: "ERROR", message: err instanceof Error ? err.message : String(err) });
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
      const timer = setTimeout(() => this.handleTurnTimeout(room, version), timeoutMs);
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
        engine.submitCall(false);
        timeoutAction = "默认不抢";
      } else if (engine.canPass()) {
        engine.submitPass();
        timeoutAction = "默认不出";
      } else {
        const action = decide(engine.state, room.difficulty);
        if (action.type !== "play") throw new Error("服务器未找到可代出的合法牌");
        engine.submitPlay(action.cards);
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
        send(ws, {
          type: "ERROR",
          message: err instanceof Error ? err.message : String(err),
        });
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
        if (action.type === "call") {
          engine.submitCall(action.want);
        } else if (action.type === "play") {
          engine.submitPlay(action.cards);
        } else {
          engine.submitPass();
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

  private createParticipant(name: string, isAi: boolean): RoomParticipant {
    return {
      id: crypto.randomUUID(),
      name,
      score: 0,
      isAi,
      joinedAt: Date.now(),
    };
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
      send(ws, {
        type: "ERROR",
        message: `还需要加入 ${requiredHumans - humanCount} 名真人玩家`,
      });
      return false;
    }
    if (humanCount > requiredHumans) {
      send(ws, {
        type: "ERROR",
        message: "房间真人数量已超过当前 AI 配置",
      });
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
          isAway: seatIndex >= 0 && !participant.isAi && room.sockets[seatIndex] === null,
          isActive: seatIndex >= 0,
        };
      })
      .sort((left, right) => right.score - left.score ||
        (room.participants.get(left.playerId)?.joinedAt ?? 0) -
          (room.participants.get(right.playerId)?.joinedAt ?? 0));
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
      winnerPlayerId: state.winnerIndex === null ? null : room.playerIds[state.winnerIndex],
      landlordPlayerId: state.landlordIndex === null ? null : room.playerIds[state.landlordIndex],
      multiplier: state.multiplier,
      scores,
    });

    recordGame(
      state.seats.map((seat) => seat.name),
      state.winnerIndex ?? 0,
      state.landlordIndex ?? 0,
      state.scoreDelta,
    );
  }

  private destroyRoom(room: Room) {
    this.cancelTurnTimer(room);
    this.rooms.delete(room.code);
  }
}
