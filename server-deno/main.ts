import { RoomManager } from "./game/rooms.ts";
import { handleApiRequest } from "./api.ts";
import { handleInviteRequest } from "./invite.ts";
import { handleUpdateRequest } from "./updates.ts";

const manager = new RoomManager();
const port = Number(Deno.env.get("PORT") ?? 18080);

Deno.serve({ port }, async (req) => {
  const url = new URL(req.url);
  if (req.method === "OPTIONS" && url.pathname.startsWith("/api/")) {
    return new Response(null, {
      status: 204,
      headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-headers": "authorization, content-type",
        "access-control-allow-methods": "GET, POST, OPTIONS",
      },
    });
  }
  const updateResponse = await handleUpdateRequest(req);
  if (updateResponse) return updateResponse;
  const inviteResponse = await handleInviteRequest(req);
  if (inviteResponse) return inviteResponse;
  const apiResponse = await handleApiRequest(req);
  if (apiResponse) return apiResponse;
  if (url.pathname === "/ws") {
    const { socket, response } = Deno.upgradeWebSocket(req);
    socket.onmessage = async (event) => {
      try {
        await manager.handleMessage(socket, String(event.data));
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
