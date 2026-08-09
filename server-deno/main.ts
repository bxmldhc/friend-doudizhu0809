import { RoomManager } from "./game/rooms.ts";

const manager = new RoomManager();
const port = Number(Deno.env.get("PORT") ?? 18080);

Deno.serve({ port }, (req) => {
  const url = new URL(req.url);
  if (url.pathname === "/ws") {
    const { socket, response } = Deno.upgradeWebSocket(req);
    socket.onmessage = (event) => {
      try {
        manager.handleMessage(socket, String(event.data));
      } catch (err) {
        socket.send(JSON.stringify({
          type: "ERROR",
          message: err instanceof Error ? err.message : String(err),
        }));
      }
    };
    socket.onclose = () => manager.disconnect(socket);
    socket.onerror = () => {};
    return response;
  }
  return new Response("Doudizhu server is running", { status: 200 });
});

console.log(`Doudizhu server listening on :${port}`);
