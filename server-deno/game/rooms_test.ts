import { DoudizhuEngine } from "./engine.ts";
import { RoomManager } from "./rooms.ts";
import { kv } from "./db.ts";
import { getStats } from "./store.ts";
import {
  createAccountForAdmin,
  loginAccount,
  registerAccount,
  resetAccountPassword,
} from "./auth.ts";

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
    const accountFriend = new FakeSocket();
    const accountCode = accountManager.create(
      socket(account),
      "认证玩家",
      "MEDIUM",
      1,
      accountId,
      true,
    );
    accountManager.join(
      socket(accountFriend),
      accountCode,
      "认证对手",
      "account-friend",
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

Deno.test("confirmed PLAY event includes authoritative combo metadata", () => {
  const manager = new RoomManager({ persistStats: false });
  const players = [new FakeSocket(), new FakeSocket(), new FakeSocket()];
  manager.create(socket(players[0]), "host", "MEDIUM", 0);
  const code = roomCode(players[0]);
  manager.join(socket(players[1]), code, "friend-1");
  manager.join(socket(players[2]), code, "friend-2");
  manager.start(socket(players[0]));

  const internal = manager as unknown as {
    rooms: Map<string, { engine: DoudizhuEngine | null }>;
  };
  const engine = internal.rooms.get(code)?.engine;
  if (!engine) throw new Error("test room did not start");
  engine.state = {
    ...engine.state,
    phase: "PLAYING",
    turnIndex: 0,
    lastPlay: [],
    lastPlayerIndex: null,
    passes: 0,
    seats: engine.state.seats.map((seat, index) => ({
      ...seat,
      hand: index === 0
        ? [{ r: 3, s: "S" }, { r: 4, s: "S" }]
        : [{ r: 7 + index, s: "S" }],
    })),
  };

  manager.action(socket(players[0]), 0, { kind: "PLAY", cards: ["3S"] });

  const state = latestState(players[0]).state as Record<string, unknown>;
  const event = state.lastEvent as Record<string, unknown>;
  if (
    event.type !== "PLAY" || event.comboType !== "SINGLE" ||
    event.primaryRank !== 3 || event.comboLength !== 1
  ) {
    throw new Error(
      "confirmed PLAY event omitted authoritative combo metadata",
    );
  }
});

Deno.test("管理房间快照只返回脱敏统计和座位状态", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(
    socket(host),
    "房主",
    "HARD",
    1,
    "account_host_123",
    true,
  );
  const code = roomCode(host);
  manager.join(
    socket(friend),
    code,
    "朋友",
    "account_friend_123",
    true,
  );

  const snapshots = manager.getAdminRoomSnapshots();
  if (snapshots.length !== 1) {
    throw new Error("管理房间列表没有返回已创建的房间");
  }
  const snapshot = snapshots[0];
  if (
    snapshot.roomCode !== code ||
    snapshot.phase !== "WAITING" ||
    snapshot.aiCount !== 1 ||
    snapshot.connectedHumanCount !== 2 ||
    snapshot.occupiedHumanCount !== 2 ||
    snapshot.hostName !== "房主"
  ) {
    throw new Error("管理房间快照统计不正确");
  }
  const serialized = JSON.stringify(snapshot).toLocaleLowerCase();
  for (
    const forbidden of [
      "resumetoken",
      "resumetokens",
      "hand",
      "bottomcards",
      "password",
    ]
  ) {
    if (serialized.includes(forbidden)) {
      throw new Error(`管理房间快照泄露了敏感字段 ${forbidden}`);
    }
  }

  const overview = manager.getAdminOverview();
  if (
    overview.roomCount !== 1 ||
    overview.activeGameCount !== 0 ||
    overview.connectedHumanCount !== 2 ||
    overview.occupiedHumanCount !== 2 ||
    overview.aiCount !== 1
  ) {
    throw new Error("管理房间汇总不正确");
  }
});

Deno.test("关键 WebSocket 拒绝场景返回稳定错误码并保留 message", async () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const overflow = new FakeSocket();
  manager.create(socket(host), "房主", "MEDIUM", 1);
  const code = roomCode(host);

  const missingJoin = new FakeSocket();
  await manager.handleMessage(
    socket(missingJoin),
    JSON.stringify({ type: "JOIN", room: "000000", name: "访客" }),
  );
  assertErrorCode(missingJoin, "ROOM_NOT_FOUND");

  manager.join(socket(friend), code, "朋友");
  await manager.handleMessage(
    socket(overflow),
    JSON.stringify({ type: "JOIN", room: code, name: "多余玩家" }),
  );
  assertErrorCode(overflow, "ROOM_FULL");

  manager.start(socket(friend));
  assertErrorCode(friend, "NOT_HOST");

  const secondManager = new RoomManager({ persistStats: false });
  const waitingHost = new FakeSocket();
  secondManager.create(socket(waitingHost), "房主", "MEDIUM", 0);
  secondManager.start(socket(waitingHost));
  assertErrorCode(waitingHost, "NOT_READY");

  manager.start(socket(host));
  const lateJoin = new FakeSocket();
  await manager.handleMessage(
    socket(lateJoin),
    JSON.stringify({ type: "JOIN", room: code, name: "迟到玩家" }),
  );
  assertErrorCode(lateJoin, "GAME_ALREADY_STARTED");

  const state = latestState(host).state as Record<string, unknown>;
  const wrongSeat = Number(state.turnIndex) === 0 ? 1 : 0;
  manager.action(socket(host), wrongSeat, { kind: "PASS" });
  assertErrorCode(host, "INVALID_ACTION");

  const invalidResume = new FakeSocket();
  await manager.handleMessage(
    socket(invalidResume),
    JSON.stringify({ type: "RECONNECT", room: code, token: "invalid" }),
  );
  assertErrorCode(invalidResume, "AUTH_REQUIRED");
});

Deno.test("恢复座位必须使用同一账号的有效登录", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const ownerName = `房主${crypto.randomUUID().slice(0, 6)}`;
  const friendName = `朋友${crypto.randomUUID().slice(0, 6)}`;
  const otherName = `旁观${crypto.randomUUID().slice(0, 6)}`;

  try {
    const owner = await registerAccount(ownerName, "secret12");
    const friend = await registerAccount(friendName, "secret12");
    const other = await registerAccount(otherName, "secret12");
    const manager = new RoomManager({ persistStats: false });
    const hostSocket = new FakeSocket();
    const friendSocket = new FakeSocket();
    await manager.handleMessage(
      socket(hostSocket),
      JSON.stringify({
        type: "CREATE",
        authToken: owner.token,
        difficulty: "MEDIUM",
        aiCount: 1,
      }),
    );
    const room = latestRoom(hostSocket);
    const code = String(room.room);
    const resumeToken = String(room.resumeToken);
    await manager.handleMessage(
      socket(friendSocket),
      JSON.stringify({
        type: "JOIN",
        room: code,
        authToken: friend.token,
      }),
    );
    manager.start(socket(hostSocket));
    manager.disconnect(socket(hostSocket));

    const wrongAccount = new FakeSocket();
    await manager.handleMessage(
      socket(wrongAccount),
      JSON.stringify({
        type: "RECONNECT",
        room: code,
        token: resumeToken,
        authToken: other.token,
      }),
    );
    assertErrorCode(wrongAccount, "RESUME_DENIED");
  } finally {
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

Deno.test("密码重置后已连接账号不能继续操作", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const nickname = `在线${crypto.randomUUID().slice(0, 6)}`;

  try {
    const account = await registerAccount(nickname, "secret12");
    const manager = new RoomManager({ persistStats: false });
    const host = new FakeSocket();
    await manager.handleMessage(
      socket(host),
      JSON.stringify({
        type: "CREATE",
        authToken: account.token,
        difficulty: "MEDIUM",
        aiCount: 2,
      }),
    );
    await resetAccountPassword(account.account.accountId, "changed12");
    await manager.handleMessage(
      socket(host),
      JSON.stringify({ type: "START" }),
    );
    assertErrorCode(host, "AUTH_REQUIRED");
  } finally {
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

Deno.test("管理员在线列表只包含已认证且连接仍打开的真人账号", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const nickname = `在线玩家${crypto.randomUUID().slice(0, 6)}`;
  let accountId = "";
  let token = "";

  try {
    const account = await registerAccount(nickname, "secret12");
    accountId = account.account.accountId;
    token = account.token;
    const manager = new RoomManager({ persistStats: false });
    const authenticated = new FakeSocket();
    const guest = new FakeSocket();

    await manager.handleMessage(
      socket(authenticated),
      JSON.stringify({
        type: "CREATE",
        authToken: token,
        difficulty: "MEDIUM",
        aiCount: 1,
      }),
    );
    const code = roomCode(authenticated);
    manager.join(socket(guest), code, "游客");

    const online = manager.getAdminOnlineAccounts();
    if (online.length !== 1) {
      throw new Error("在线列表不应包含 AI 或未认证游客");
    }
    const snapshot = online[0];
    if (
      snapshot.nickname !== nickname ||
      snapshot.online !== true ||
      snapshot.location !== "ROOM" ||
      snapshot.gameType !== "DOUDIZHU" ||
      snapshot.roomCode !== code ||
      typeof snapshot.lastActiveAt !== "number"
    ) {
      throw new Error("在线账号快照字段不正确");
    }
    if (
      JSON.stringify(snapshot).match(
        /accountId|token|password|ip|resumeToken/i,
      )
    ) {
      throw new Error("在线账号快照泄露了敏感身份字段");
    }

    manager.disconnect(socket(authenticated));
    if (manager.getAdminOnlineAccounts().length !== 0) {
      throw new Error("已断线账号不应继续出现在在线列表");
    }
  } finally {
    if (token) await kv.delete(["sessions", token]);
    if (accountId) {
      await kv.delete(["accounts", accountId]);
      await kv.delete(["accountByNickname", nickname.toLocaleLowerCase("zh-CN")]);
      await kv.delete(["players", accountId]);
      await kv.delete(["recentMatches", accountId]);
    }
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

Deno.test("临时密码账号不能创建、加入或恢复房间", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const nickname = `临时${crypto.randomUUID().slice(0, 6)}`;
  let accountId = "";
  let authToken = "";

  try {
    const account = await createAccountForAdmin(nickname, "secret12");
    accountId = account.accountId;
    const login = await loginAccount(nickname, "secret12");
    authToken = login.token;
    const manager = new RoomManager({ persistStats: false });

    for (
      const message of [
        {
          type: "CREATE",
          authToken,
          difficulty: "MEDIUM",
          aiCount: 2,
        },
        { type: "JOIN", room: "123456", authToken },
        {
          type: "RECONNECT",
          room: "123456",
          token: "invalid-resume-token",
          authToken,
        },
      ]
    ) {
      const ws = new FakeSocket();
      await manager.handleMessage(socket(ws), JSON.stringify(message));
      assertErrorCode(ws, "PASSWORD_CHANGE_REQUIRED");
    }
  } finally {
    if (authToken) await kv.delete(["sessions", authToken]);
    if (accountId) {
      await kv.delete(["accounts", accountId]);
      await kv.delete(["players", accountId]);
      await kv.delete(["recentMatches", accountId]);
    }
    await kv.delete(["accountByNickname", nickname.toLocaleLowerCase()]);
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

function assertErrorCode(ws: FakeSocket, expectedCode: string) {
  const message = lastMessage(ws);
  if (
    message.type !== "ERROR" ||
    message.code !== expectedCode ||
    typeof message.message !== "string" ||
    message.message.length === 0
  ) {
    throw new Error(
      `预期错误码 ${expectedCode} 和兼容 message，实际为 ${
        JSON.stringify(message)
      }`,
    );
  }
}

Deno.test("room keeps selected difficulty and action timeout", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  const anotherFriend = new FakeSocket();
  manager.create(socket(host), "host", "HARD", 0, "", false, 45);

  const room = latestRoom(host);
  if (room.difficulty !== "HARD" || room.actionTimeoutSeconds !== 45) {
    throw new Error("ROOM did not include selected rules");
  }

  const code = roomCode(host);
  manager.join(socket(friend), code, "friend");
  manager.join(socket(anotherFriend), code, "another");
  manager.start(socket(host));
  const state = latestState(host).state as Record<string, unknown>;
  if (typeof state.actionDeadlineAt !== "number") {
    throw new Error("human turn did not include an action deadline");
  }
  const remaining = state.actionDeadlineAt - Date.now();
  if (remaining < 43_000 || remaining > 46_000) {
    throw new Error(`expected a 45 second deadline, got ${remaining}ms`);
  }
});

Deno.test("only host can disband an idle room", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "host", "MEDIUM", 1);
  const code = roomCode(host);
  manager.join(socket(friend), code, "friend");

  manager.disband(socket(friend));
  assertErrorCode(friend, "NOT_HOST");

  manager.disband(socket(host));
  if (
    lastMessage(host).type !== "ROOM_DISBANDED" ||
    lastMessage(friend).type !== "ROOM_DISBANDED"
  ) {
    throw new Error("disband was not broadcast to every connected player");
  }

  const newcomer = new FakeSocket();
  const result = manager.join(socket(newcomer), code, "newcomer");
  if (result.ok || result.code !== "ROOM_NOT_FOUND") {
    throw new Error("disbanded room still exists");
  }
});

Deno.test("host cannot disband during an active round", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  manager.create(socket(host), "host", "MEDIUM", 2);
  manager.start(socket(host));

  manager.disband(socket(host));
  assertErrorCode(host, "GAME_IN_PROGRESS");
});

Deno.test("quick messages are allowlisted and rate limited", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const friend = new FakeSocket();
  manager.create(socket(host), "host", "MEDIUM", 1);
  manager.join(socket(friend), roomCode(host), "friend");

  manager.sendQuickMessage(socket(host), "WAIT_A_MOMENT");
  const first = lastMessage(friend);
  if (
    first.type !== "QUICK_MESSAGE" ||
    first.messageId !== "WAIT_A_MOMENT" ||
    first.seat !== 0
  ) {
    throw new Error("quick message payload is incomplete");
  }

  manager.sendQuickMessage(socket(host), "READY_TO_START");
  assertErrorCode(host, "MESSAGE_RATE_LIMITED");

  manager.sendQuickMessage(socket(friend), "CUSTOM_TEXT");
  assertErrorCode(friend, "INVALID_QUICK_MESSAGE");
});
