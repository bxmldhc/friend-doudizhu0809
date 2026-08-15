import { RoomManager } from "./rooms.ts";
import { kv } from "./db.ts";
import { getStats } from "./store.ts";

class FakeSocket {
  readyState = 1;
  messages: Record<string, unknown>[] = [];

  send(raw: string) {
    this.messages.push(JSON.parse(raw));
  }

  close() {
    this.readyState = 3;
  }
}

function socket(value: FakeSocket): WebSocket {
  return value as unknown as WebSocket;
}

function lastMessage(ws: FakeSocket): Record<string, unknown> {
  const message = ws.messages.at(-1);
  if (!message) throw new Error("没有收到服务器消息");
  return message;
}

function roomCode(ws: FakeSocket): string {
  return String(lastMessage(ws).room);
}

function stateMessages(ws: FakeSocket): Record<string, unknown>[] {
  return ws.messages.filter((message) => message.type === "STATE");
}

function latestState(ws: FakeSocket): Record<string, unknown> {
  const message = stateMessages(ws).at(-1);
  if (!message) throw new Error("没有收到 STATE 消息");
  return message;
}

function latestRoom(ws: FakeSocket): Record<string, unknown> {
  const message = ws.messages.filter((item) => item.type === "ROOM").at(-1);
  if (!message) throw new Error("没有收到 ROOM 消息");
  return message;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function finishCurrentGame(manager: RoomManager, code: string) {
  const internal = manager as unknown as {
    rooms: Map<string, {
      engine: {
        state: Record<string, unknown>;
      } | null;
    }>;
    afterEngineChange: (room: unknown) => void;
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

Deno.test("2 个 AI：1 名真人可以开始", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 2);

  const room = lastMessage(host);
  if (room.aiCount !== 2 || room.requiredHumans !== 1) {
    throw new Error("房间配置没有保存为 2 个 AI / 1 名真人");
  }

  manager.start(socket(host));
  if (lastMessage(host).type !== "STATE") {
    throw new Error("1 名真人配置应该允许开始");
  }
});

Deno.test("只有认证账号会写入累计战绩，旧版游客仅保留场内分", async () => {
  const guestId = `guest_${crypto.randomUUID().replaceAll("-", "")}`;
  const accountId = `account_${crypto.randomUUID().replaceAll("-", "")}`;
  await kv.delete(["players", guestId]);
  await kv.delete(["players", accountId]);

  try {
    const guestManager = new RoomManager({ aiDelayMs: 0 });
    const guest = new FakeSocket();
    const guestCode = guestManager.create(
      socket(guest),
      "旧版游客",
      "MEDIUM",
      2,
      guestId,
      false,
    );
    guestManager.start(socket(guest));
    finishCurrentGame(guestManager, guestCode);

    const accountManager = new RoomManager({ aiDelayMs: 0 });
    const account = new FakeSocket();
    const accountCode = accountManager.create(
      socket(account),
      "认证玩家",
      "MEDIUM",
      2,
      accountId,
      true,
    );
    accountManager.start(socket(account));
    finishCurrentGame(accountManager, accountCode);

    await wait(30);
    if (await getStats(guestId)) {
      throw new Error("未认证游客不应写入累计账号战绩");
    }
    const stats = await getStats(accountId);
    if (!stats || stats.games !== 1 || stats.totalScore !== 2) {
      throw new Error("认证账号的累计战绩没有正确保存");
    }
  } finally {
    await kv.delete(["players", guestId]);
    await kv.delete(["players", accountId]);
  }
});

Deno.test("1 个 AI：需要 2 名真人，人数不足时拒绝开始", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 1);
  const code = roomCode(host);

  manager.start(socket(host));
  const rejected = lastMessage(host);
  if (
    rejected.type !== "ERROR" ||
    !String(rejected.message).includes("还需要加入 1 名真人")
  ) {
    throw new Error("人数不足时没有返回正确错误");
  }

  manager.join(socket(friend), code, "朋友");
  manager.start(socket(host));
  if (lastMessage(host).type !== "STATE") {
    throw new Error("2 名真人配置应该允许开始");
  }
});

Deno.test("0 个 AI：需要 3 名真人，人数不足时拒绝开始", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);

  manager.join(socket(friend), code, "朋友1");
  manager.start(socket(host));
  if (lastMessage(host).type !== "ERROR") {
    throw new Error("3 名真人配置在 2 人时不应开始");
  }

  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));
  if (lastMessage(host).type !== "STATE") {
    throw new Error("3 名真人配置应该允许开始");
  }
});

Deno.test("AI 配置限制在 0 到 2，且房间不会超过所需真人数", () => {
  const manager = new RoomManager();
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 99);
  const code = roomCode(host);

  const joinResult = manager.join(socket(friend), code, "朋友");
  if (joinResult.ok) {
    throw new Error("2 个 AI 配置不应再加入第二名真人");
  }
});

Deno.test("等待区断线释放为空位，游戏中断线保留座位并由 AI 接管", async () => {
  const manager = new RoomManager();
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  const sockets = [host, friend, anotherFriend];

  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");

  manager.start(socket(host));
  const initialStateMessage = latestState(host);
  const initialState = initialStateMessage.state as Record<string, unknown>;
  const initialSeats = initialState.seats as Record<string, unknown>[];
  const activeSeat = initialState.turnIndex as number;
  const activeName = String(initialSeats[activeSeat].name);
  const disconnected = sockets[activeSeat];
  const observer = sockets.find((candidate) => candidate !== disconnected)!;

  manager.disconnect(socket(disconnected));

  const awayStateMessage = latestState(observer);
  const awayState = awayStateMessage.state as Record<string, unknown>;
  const awaySeats = awayState.seats as Record<string, unknown>[];
  const awaySeat = awaySeats[activeSeat];
  if (
    awaySeat.name !== activeName || awaySeat.isHuman !== false ||
    awaySeat.isAway !== true
  ) {
    throw new Error("游戏中断线没有保留昵称/座位或没有标记为暂离");
  }

  await wait(1100);
  const aiStateMessage = latestState(observer);
  const aiState = aiStateMessage.state as Record<string, unknown>;
  if (aiState.turnIndex === activeSeat) {
    throw new Error("断线玩家轮次没有交给服务器 AI 接管");
  }

  const replacement = new FakeSocket();
  const joinResult = manager.join(socket(replacement), code, "新玩家");
  if (joinResult.ok || joinResult.message !== "游戏已经开始，不能加入") {
    throw new Error("进行中的暂离座位不应被普通加入占用");
  }

  manager.disconnect(socket(friend));
  manager.disconnect(socket(anotherFriend));
});

Deno.test("游戏中断线后可凭恢复凭证回到原座位", async () => {
  const manager = new RoomManager({ aiDelayMs: 20 });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  const token = String(lastMessage(host).resumeToken);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));

  manager.disconnect(socket(host));
  const resumed = new FakeSocket();
  const result = manager.reconnect(socket(resumed), code, token);
  if (!result.ok) throw new Error(result.message ?? "恢复失败");

  const room = latestRoom(resumed);
  if (
    room.type !== "ROOM" || room.yourSeat !== 0 || room.resumeToken !== token
  ) {
    throw new Error("恢复后没有回到原座位或恢复凭证不一致");
  }
  const state = latestState(resumed).state as Record<string, unknown>;
  const seats = state.seats as Record<string, unknown>[];
  if (seats[0].isHuman !== true || seats[0].isAway !== false) {
    throw new Error("恢复后座位仍处于暂离状态");
  }
});

Deno.test("真人操作超时由服务器执行默认动作并刷新截止时间", async () => {
  const manager = new RoomManager({ actionTimeoutMs: 20, aiDelayMs: 100 });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));
  const initial = latestState(host);
  const initialState = initial.state as Record<string, unknown>;
  const initialTurn = initialState.turnIndex;
  if (typeof initialState.actionDeadlineAt !== "number") {
    throw new Error("真人回合没有下发服务器操作截止时间");
  }

  await wait(50);
  const after = latestState(host).state as Record<string, unknown>;
  if (after.turnIndex === initialTurn) {
    throw new Error("超时后当前操作玩家没有变化");
  }
  if (after.actionDeadlineAt === initialState.actionDeadlineAt) {
    throw new Error("超时后没有推进牌局状态");
  }
  if (!String(after.message).includes("超时")) {
    throw new Error("超时动作没有在服务器状态中明确标记");
  }
});

Deno.test("等待区房主退出后随机移交，新的房主可以开始游戏", () => {
  const manager = new RoomManager();
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");

  manager.disconnect(socket(host));

  const friendRoom = latestRoom(friend);
  const anotherRoom = latestRoom(anotherFriend);
  const hostSeats = [friendRoom, anotherRoom]
    .map((room) => Number(room.hostSeat))
    .filter((seat, index, all) => all.indexOf(seat) === index);
  if (hostSeats.length !== 1 || ![1, 2].includes(hostSeats[0])) {
    throw new Error("房主退出后没有把房主身份移交给仍在线玩家");
  }

  const replacement = new FakeSocket();
  const joinResult = manager.join(socket(replacement), code, "新玩家");
  if (!joinResult.ok) {
    throw new Error(joinResult.message ?? "房主退出后无法补位");
  }
  const newHost = hostSeats[0] === 1 ? friend : anotherFriend;
  manager.start(socket(newHost));
  if (lastMessage(newHost).type !== "STATE") {
    throw new Error("新房主无法请求开始游戏");
  }
});

Deno.test("游戏中房主退出不销毁房间，玩家身份和排行榜分数继续保留", () => {
  const manager = new RoomManager();
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  const hostRoomBefore = latestRoom(host);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));

  manager.disconnect(socket(host));
  const state = latestState(friend).state as Record<string, unknown>;
  const leaderboard = state.leaderboard as Record<string, unknown>[];
  const formerHost = leaderboard.find((entry) => entry.name === "房主");
  if (
    !formerHost || formerHost.isActive !== true || formerHost.isAway !== true
  ) {
    throw new Error("游戏中退出的房主没有作为暂离玩家保留在排行榜");
  }
  if (latestRoom(friend).hostSeat === 0) {
    throw new Error("游戏中房主退出后没有移交房主身份");
  }
  void hostRoomBefore;
});

Deno.test("所有真人断线后立即销毁房间和恢复凭证", () => {
  const manager = new RoomManager();
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  const token = String(latestRoom(host).resumeToken);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));

  manager.disconnect(socket(host));
  manager.disconnect(socket(friend));
  manager.disconnect(socket(anotherFriend));

  const resumed = new FakeSocket();
  const reconnectResult = manager.reconnect(socket(resumed), code, token);
  if (reconnectResult.ok || reconnectResult.message !== "房间不存在") {
    throw new Error("全员断线后房间仍可恢复，未按规则销毁");
  }

  const replacement = new FakeSocket();
  const joinResult = manager.join(socket(replacement), code, "后来玩家");
  if (joinResult.ok || joinResult.message !== "房间不存在") {
    throw new Error("全员断线后旧房号仍可加入");
  }
});

Deno.test("结算后所有真人必须准备，AI 默认准备，房主才能开始下一局", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 1);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友");
  manager.start(socket(host));
  finishCurrentGame(manager, code);

  const finished = latestState(host).state as Record<string, unknown>;
  const initialReady = finished.readySeats as boolean[];
  if (initialReady[0] || initialReady[1] || !initialReady[2]) {
    throw new Error("结算后的真人/AI 默认准备状态不正确");
  }

  manager.restart(socket(host));
  const rejected = lastMessage(host);
  if (
    rejected.type !== "ERROR" ||
    !String(rejected.message).includes("所有玩家准备")
  ) {
    throw new Error("真人未全部准备时房主仍能开始下一局");
  }

  manager.setReady(socket(host), true);
  manager.setReady(socket(friend), true);
  const allReady = latestState(host).state as Record<string, unknown>;
  if (!(allReady.readySeats as boolean[]).every(Boolean)) {
    throw new Error("真人准备状态没有同步给房间");
  }

  manager.restart(socket(host));
  const restarted = latestState(host).state as Record<string, unknown>;
  if (restarted.phase !== "BIDDING" || restarted.gameNumber !== 2) {
    throw new Error("全员准备后房主未能开始下一局");
  }
});

Deno.test("非房主不能开始下一局，进行中也不能提前准备", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 0);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友1");
  manager.join(socket(anotherFriend), code, "朋友2");
  manager.start(socket(host));

  manager.setReady(socket(friend), true);
  const earlyReady = lastMessage(friend);
  if (
    earlyReady.type !== "ERROR" ||
    !String(earlyReady.message).includes("本局结束后")
  ) {
    throw new Error("进行中准备没有被服务器拒绝");
  }

  finishCurrentGame(manager, code);
  manager.setReady(socket(host), true);
  manager.setReady(socket(friend), true);
  manager.setReady(socket(anotherFriend), true);
  manager.restart(socket(friend));
  const rejected = lastMessage(friend);
  if (
    rejected.type !== "ERROR" || !String(rejected.message).includes("只有房主")
  ) {
    throw new Error("非房主可以开始下一局");
  }
});

Deno.test("结算后离开会释放旧凭证，原玩家可作为普通玩家重新加入", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 1);
  const code = roomCode(host);
  manager.join(socket(friend), code, "朋友");
  const oldToken = String(latestRoom(friend).resumeToken);
  manager.start(socket(host));
  finishCurrentGame(manager, code);

  manager.leave(socket(friend));
  const staleReconnect = new FakeSocket();
  const reconnectResult = manager.reconnect(
    socket(staleReconnect),
    code,
    oldToken,
  );
  if (
    reconnectResult.ok ||
    !String(reconnectResult.message).includes("恢复凭证无效")
  ) {
    throw new Error("结算后离开仍可使用旧凭证认领座位");
  }

  const rejoined = new FakeSocket();
  const joinResult = manager.join(socket(rejoined), code, "朋友");
  if (!joinResult.ok || latestRoom(rejoined).yourSeat !== 1) {
    throw new Error(joinResult.message ?? "结算后无法作为普通玩家重新加入");
  }
});
Deno.test("state events use unique revisions for confirmed player actions", () => {
  const manager = new RoomManager({ persistStats: false });
  const players = [new FakeSocket(), new FakeSocket(), new FakeSocket()];
  manager.create(socket(players[0]), "host", "MEDIUM", 0);
  const code = roomCode(players[0]);
  manager.join(socket(players[1]), code, "friend-1");
  manager.join(socket(players[2]), code, "friend-2");
  manager.start(socket(players[0]));

  const started = latestState(players[0]).state as Record<string, unknown>;
  const startedEvent = started.lastEvent as Record<string, unknown>;
  const startedRevision = Number(started.revision);
  if (
    startedRevision < 1 ||
    startedEvent.type !== "ROUND_STARTED" ||
    startedEvent.id !== `${code}:1:${startedRevision}`
  ) {
    throw new Error(
      "round start event was not serialized with a stable revision",
    );
  }

  const turnIndex = Number(started.turnIndex);
  manager.action(
    socket(players[turnIndex]),
    turnIndex,
    { kind: "CALL", want: false },
  );

  const acted = latestState(players[0]).state as Record<string, unknown>;
  const actedEvent = acted.lastEvent as Record<string, unknown>;
  if (
    Number(acted.revision) !== startedRevision + 1 ||
    actedEvent.id === startedEvent.id ||
    actedEvent.type !== "NOT_CALL" ||
    actedEvent.seat !== turnIndex ||
    actedEvent.automatic !== false
  ) {
    throw new Error("confirmed action did not advance the authoritative event");
  }
});
