import { RoomManager } from "./rooms.ts";

class FakeSocket {
  readyState = WebSocket.OPEN;
  messages: Record<string, unknown>[] = [];
  send(raw: string) { this.messages.push(JSON.parse(raw)); }
  close() { this.readyState = WebSocket.CLOSED; }
}

function socket(value: FakeSocket): WebSocket { return value as unknown as WebSocket; }
function latest(client: FakeSocket, type: string): Record<string, unknown> {
  const value = client.messages.filter((message) => message.type === type).at(-1);
  if (!value) throw new Error(`缺少 ${type}`);
  return value;
}

Deno.test("情书房间支持四席、开局补 AI，并隐藏对手手牌", () => {
  const manager = new RoomManager({ persistStats: false, aiDelayMs: 99_999 });
  const host = new FakeSocket();
  const guest = new FakeSocket();
  const code = manager.create(socket(host), "房主", "HARD", 0, "", false, 20, "LOVE_LETTER");
  const created = latest(host, "ROOM");
  if (created.gameType !== "LOVE_LETTER" || (created.players as string[]).length !== 4) {
    throw new Error("情书等待区不是四人配置");
  }
  if (!manager.join(socket(guest), code, "好友").ok) throw new Error("好友无法加入情书房间");
  manager.start(socket(host));
  const state = latest(host, "STATE").state as Record<string, unknown>;
  if (state.gameType !== "LOVE_LETTER" || state.phase !== "PLAYING") {
    throw new Error("情书没有进入进行中状态");
  }
  const players = state.players as Array<Record<string, unknown>>;
  if (players.length !== 4 || players.filter((player) => player.alive === true).length !== 4) {
    throw new Error("情书没有正确补齐座位");
  }
  const hostSeat = Number(created.yourSeat);
  const opponent = players.find((player) => Number(player.seat) !== hostSeat)!;
  const hand = opponent.hand as Array<Record<string, unknown>>;
  if (hand.some((card) => "value" in card || "name" in card)) {
    throw new Error("情书 STATE 泄露了对手手牌");
  }
});

Deno.test("情书创建房间按选择保留二至四个座位", () => {
  for (const count of [2, 3, 4]) {
    const manager = new RoomManager({ persistStats: false });
    const host = new FakeSocket();
    const code = manager.create(socket(host), "房主", "HARD", 0, "", false, 20, "LOVE_LETTER", count);
    const created = latest(host, "ROOM");
    if (created.gameType !== "LOVE_LETTER" || (created.players as string[]).length !== count) {
      throw new Error(`情书没有按 ${count} 人创建等待区`);
    }
    if (Number(created.requiredHumans) !== count) throw new Error("情书人数配置未返回");
    manager.disband(socket(host));
  }
});
