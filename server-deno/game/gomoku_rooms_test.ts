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

function socket(value: FakeSocket): WebSocket {
  return value as unknown as WebSocket;
}

function latest(
  client: FakeSocket,
  type: string,
): Record<string, unknown> {
  const message = client.messages.filter((item) => item.type === type).at(-1);
  if (!message) throw new Error(`Expected ${type} message`);
  return message;
}

function roomCode(client: FakeSocket): string {
  return String(latest(client, "ROOM").room);
}

function gomokuState(client: FakeSocket): Record<string, unknown> {
  return latest(client, "STATE").state as Record<string, unknown>;
}

function move(
  manager: RoomManager,
  client: FakeSocket,
  seat: number,
  row: number,
  col: number,
) {
  manager.action(socket(client), seat, { kind: "MOVE", row, col });
}

Deno.test("五子棋房间使用两人配置，并通过统一 STATE 广播服务端棋盘", async () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const guest = new FakeSocket();
  const third = new FakeSocket();
  const code = manager.create(
    socket(host),
    "host",
    "MEDIUM",
    0,
    "",
    false,
    20,
    "GOMOKU",
  );

  const created = latest(host, "ROOM");
  if (
    created.gameType !== "GOMOKU" ||
    created.game_mode !== "gomoku" ||
    created.requiredHumans !== 2 ||
    created.aiCount !== 0
  ) {
    throw new Error("Gomoku ROOM metadata is incorrect");
  }
  if (!manager.join(socket(guest), code, "guest").ok) {
    throw new Error("Second Gomoku player could not join");
  }
  if (manager.join(socket(third), code, "third").code !== "ROOM_FULL") {
    throw new Error("A Gomoku room accepted a third player");
  }
  manager.start(socket(host));
  const initial = gomokuState(host);
  const board = initial.board as number[][];
  if (
    initial.gameType !== "GOMOKU" ||
    initial.game_mode !== "gomoku" ||
    initial.phase !== "PLAYING" ||
    initial.boardSize !== 15 ||
    board.length !== 15 ||
    board.some((row) => row.length !== 15)
  ) {
    throw new Error("Gomoku STATE was not serialized correctly");
  }

  await manager.handleMessage(
    socket(host),
    JSON.stringify({ type: "GOMOKU_MOVE", row: 7, col: 7 }),
  );
  const afterMove = gomokuState(guest);
  const event = afterMove.lastEvent as Record<string, unknown>;
  if (
    (afterMove.board as number[][])[7][7] !== 1 ||
    event.type !== "GOMOKU_MOVE" ||
    event.seat !== 0 ||
    event.row !== 7 ||
    event.col !== 7
  ) {
    throw new Error("GOMOKU_MOVE was not routed through the server referee");
  }

  await manager.handleMessage(
    socket(host),
    JSON.stringify({ type: "GOMOKU_MOVE", row: 7, col: 8 }),
  );
  if (latest(host, "ERROR").code !== "INVALID_ACTION") {
    throw new Error("Out-of-turn move was not rejected");
  }
});

Deno.test("Gomoku room starts with one human and fills one AI", async () => {
  const manager = new RoomManager({ persistStats: false, aiDelayMs: 1 });
  const host = new FakeSocket();
  manager.create(
    socket(host),
    "host",
    "MEDIUM",
    1,
    "",
    false,
    20,
    "GOMOKU",
  );

  const created = latest(host, "ROOM");
  if (created.requiredHumans !== 2 || created.aiCount !== 0) {
    throw new Error("Gomoku 等待区没有保留真人空位");
  }

  manager.start(socket(host));
  const initial = gomokuState(host);
  const seats = initial.seats as Record<string, unknown>[];
  if (
    initial.phase !== "PLAYING" ||
    seats.length !== 2 ||
    seats[1].name !== "棋灵" ||
    seats[1].isAi !== true
  ) {
    throw new Error("Gomoku AI room did not start as a two-seat match");
  }

  move(manager, host, 0, 7, 7);
  await new Promise((resolve) => setTimeout(resolve, 20));
  const afterAiMove = gomokuState(host);
  const event = afterAiMove.lastEvent as Record<string, unknown>;
  if (
    afterAiMove.moveCount !== 2 ||
    event.type !== "GOMOKU_MOVE" ||
    event.seat !== 1 ||
    event.automatic !== true
  ) {
    throw new Error("Gomoku AI did not respond with a server-authoritative move");
  }
});

Deno.test("Gomoku always uses a 20 second deadline and times out with an automatic legal move", async () => {
  const manager = new RoomManager({
    persistStats: false,
    actionTimeoutMs: 20,
    aiDelayMs: 1,
  });
  const host = new FakeSocket();
  const guest = new FakeSocket();
  const code = manager.create(
    socket(host),
    "host",
    "MEDIUM",
    0,
    "",
    false,
    45,
    "GOMOKU",
  );
  manager.join(socket(guest), code, "guest");

  const internal = manager as unknown as {
    rooms: Map<string, { actionTimeoutMs: number }>;
  };
  if (internal.rooms.get(code)?.actionTimeoutMs !== 20_000) {
    throw new Error("Gomoku room did not force the 20 second turn setting");
  }

  manager.start(socket(host));
  const initial = gomokuState(host);
  if (typeof initial.actionDeadlineAt !== "number") {
    throw new Error("Gomoku state did not include an action deadline");
  }

  await new Promise((resolve) => setTimeout(resolve, 50));
  const afterTimeout = gomokuState(host);
  const event = afterTimeout.lastEvent as Record<string, unknown>;
  if (
    afterTimeout.moveCount !== 1 ||
    event.type !== "GOMOKU_MOVE" ||
    event.seat !== 0 ||
    event.automatic !== true
  ) {
    throw new Error("Timed out Gomoku player did not receive an automatic move");
  }
});

Deno.test("五子棋由服务端确认五连、结算准备和房主重开", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const guest = new FakeSocket();
  const code = manager.create(
    socket(host),
    "host",
    "MEDIUM",
    0,
    "",
    false,
    20,
    "GOMOKU",
  );
  manager.join(socket(guest), code, "guest");
  manager.start(socket(host));

  for (let col = 0; col < 4; col++) {
    move(manager, host, 0, 7, col);
    move(manager, guest, 1, 8, col);
  }
  move(manager, host, 0, 7, 4);

  const finished = gomokuState(host);
  if (
    finished.phase !== "FINISHED" ||
    finished.winnerIndex !== 0 ||
    (finished.winningLine as unknown[]).length !== 5 ||
    JSON.stringify(finished.readySeats) !== JSON.stringify([false, false])
  ) {
    throw new Error("Gomoku win or ready state was not confirmed by server");
  }
  if ((finished.roundHistory as unknown[]).length !== 1) {
    throw new Error("Finished Gomoku round was not retained in room history");
  }

  manager.restart(socket(host));
  if (latest(host, "ERROR").code !== "NOT_READY") {
    throw new Error("Host restarted Gomoku before every player was ready");
  }
  manager.setReady(socket(host), true);
  manager.setReady(socket(guest), true);
  manager.restart(socket(host));
  const restarted = gomokuState(host);
  if (
    restarted.phase !== "PLAYING" ||
    restarted.gameNumber !== 2 ||
    restarted.moveCount !== 0
  ) {
    throw new Error("Prepared Gomoku rematch did not restart authoritatively");
  }
});

Deno.test("五子棋断线后可凭既有重连凭证恢复完整棋盘状态", () => {
  const manager = new RoomManager({ persistStats: false });
  const host = new FakeSocket();
  const guest = new FakeSocket();
  const replacement = new FakeSocket();
  const code = manager.create(
    socket(host),
    "host",
    "MEDIUM",
    0,
    "",
    false,
    20,
    "GOMOKU",
  );
  manager.join(socket(guest), code, "guest");
  const token = String(latest(guest, "ROOM").resumeToken);
  manager.start(socket(host));
  move(manager, host, 0, 7, 7);

  manager.disconnect(socket(guest));
  const disconnected = gomokuState(host);
  const seats = disconnected.seats as Record<string, unknown>[];
  if (seats[1].isAway !== true) {
    throw new Error("Disconnected Gomoku seat was not marked away");
  }

  const result = manager.reconnect(socket(replacement), code, token);
  if (!result.ok) throw new Error("Gomoku reconnect was rejected");
  const restored = gomokuState(replacement);
  if (
    (restored.board as number[][])[7][7] !== 1 ||
    (restored.seats as Record<string, unknown>[])[1].isAway !== false
  ) {
    throw new Error("Reconnect did not restore authoritative Gomoku state");
  }
});
