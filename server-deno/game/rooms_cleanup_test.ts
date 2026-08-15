import { DoudizhuEngine } from "./engine.ts";
import { RoomManager } from "./rooms.ts";

class FakeSocket {
  readyState = WebSocket.OPEN;
  messages: Record<string, unknown>[] = [];

  send(raw: string) {
    this.messages.push(JSON.parse(raw));
  }

  close() {
    this.readyState = WebSocket.CLOSED;
  }
}

interface TestRoom {
  lastActivityAt: number;
  noOpenConnectionSince: number | null;
  engine: DoudizhuEngine | null;
}

function socket(value: FakeSocket): WebSocket {
  return value as unknown as WebSocket;
}

function roomCode(ws: FakeSocket): string {
  const message = ws.messages.findLast((item) => item.type === "ROOM");
  if (!message) throw new Error("没有收到 ROOM 消息");
  return String(message.room);
}

function resumeToken(ws: FakeSocket): string {
  const message = ws.messages.findLast((item) => item.type === "ROOM");
  if (!message) throw new Error("没有收到 ROOM 消息");
  return String(message.resumeToken);
}

function roomsOf(manager: RoomManager): Map<string, TestRoom> {
  return (manager as unknown as { rooms: Map<string, TestRoom> }).rooms;
}

function roomOf(manager: RoomManager, code: string): TestRoom {
  const room = roomsOf(manager).get(code);
  if (!room) throw new Error("测试房间不存在");
  return room;
}

function finishCurrentGame(manager: RoomManager, code: string) {
  const internal = manager as unknown as {
    rooms: Map<string, TestRoom>;
    afterEngineChange: (room: TestRoom) => void;
  };
  const room = internal.rooms.get(code);
  if (!room?.engine) throw new Error("测试房间尚未开始");
  room.engine.state = {
    ...room.engine.state,
    phase: "FINISHED",
    winnerIndex: 0,
    scoreDelta: [2, -1, -1],
    message: "测试牌局结束",
  };
  internal.afterEngineChange(room);
}

Deno.test("异常关闭的真人连接超过宽限期后才清理房间", () => {
  const manager = new RoomManager({
    persistStats: false,
    staleRoomGraceMs: 1_000,
  });
  const host = new FakeSocket();
  const code = manager.create(socket(host), "房主", "MEDIUM", 2);
  const room = roomOf(manager, code);
  const lastActivityAt = room.lastActivityAt;

  host.readyState = WebSocket.CLOSED;

  if (manager.cleanupStaleRooms(lastActivityAt + 100) !== 0) {
    throw new Error("首次发现异常断线时不应立即清理房间");
  }
  if (manager.cleanupStaleRooms(lastActivityAt + 1_099) !== 0) {
    throw new Error("宽限期内不应清理异常断线房间");
  }
  if (manager.cleanupStaleRooms(lastActivityAt + 1_100) !== 1) {
    throw new Error("超过宽限期后没有清理异常断线房间");
  }
  if (roomsOf(manager).has(code)) {
    throw new Error("清理后房间仍残留在房间表中");
  }
});

Deno.test("仍有 OPEN 真人连接时永不按陈旧活动时间清理", () => {
  const manager = new RoomManager({
    persistStats: false,
    staleRoomGraceMs: 1_000,
  });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const code = manager.create(socket(host), "房主", "MEDIUM", 1);
  manager.join(socket(friend), code, "朋友");
  const room = roomOf(manager, code);

  room.lastActivityAt = 0;
  room.noOpenConnectionSince = 1;
  host.readyState = WebSocket.CLOSED;

  if (manager.cleanupStaleRooms(10_000) !== 0) {
    throw new Error("仍有在线真人玩家的朋友局不应被清理");
  }
  if (!roomsOf(manager).has(code)) {
    throw new Error("仍有在线真人玩家的房间被错误销毁");
  }
});

Deno.test("有效房间活动会刷新最后活动时间", () => {
  const manager = new RoomManager({
    persistStats: false,
    staleRoomGraceMs: 1_000,
  });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  const code = manager.create(socket(host), "房主", "MEDIUM", 0);
  const room = roomOf(manager, code);
  if (room.lastActivityAt <= 0) {
    throw new Error("创建房间时没有记录活动时间");
  }

  room.lastActivityAt = 0;
  manager.join(socket(friend), code, "朋友");
  manager.join(socket(anotherFriend), code, "朋友2");
  if (room.lastActivityAt <= 0) {
    throw new Error("加入房间时没有刷新活动时间");
  }

  const friendToken = resumeToken(friend);
  const reconnectedFriend = new FakeSocket();
  room.lastActivityAt = 0;
  const reconnectResult = manager.reconnect(
    socket(reconnectedFriend),
    code,
    friendToken,
  );
  if (!reconnectResult.ok || room.lastActivityAt <= 0) {
    throw new Error("重连房间时没有刷新活动时间");
  }

  room.lastActivityAt = 0;
  manager.start(socket(host));
  if (room.lastActivityAt <= 0) {
    throw new Error("开始牌局时没有刷新活动时间");
  }

  const engine = room.engine;
  if (!engine) throw new Error("测试牌局没有开始");
  const turnSocket = [host, reconnectedFriend, anotherFriend][
    engine.state.turnIndex
  ];
  room.lastActivityAt = 0;
  manager.action(socket(turnSocket), engine.state.turnIndex, {
    kind: "CALL",
    want: false,
  });
  if (room.lastActivityAt <= 0) {
    throw new Error("有效玩家操作没有刷新活动时间");
  }

  finishCurrentGame(manager, code);
  room.lastActivityAt = 0;
  manager.setReady(socket(host), true);
  if (room.lastActivityAt <= 0) {
    throw new Error("准备操作没有刷新活动时间");
  }
});
