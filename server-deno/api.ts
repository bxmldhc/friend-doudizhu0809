import {
  AuthError,
  loginAccount,
  logoutSession,
  registerAccount,
  resolveSession,
} from "./game/auth.ts";
import { getStats } from "./game/store.ts";

export async function handleApiRequest(req: Request): Promise<Response | null> {
  const url = new URL(req.url);
  if (!url.pathname.startsWith("/api/")) return null;

  try {
    if (req.method === "POST" && url.pathname === "/api/auth/register") {
      const body = await readCredentials(req);
      return json({
        ok: true,
        data: await registerAccount(body.nickname, body.password),
      });
    }
    if (req.method === "POST" && url.pathname === "/api/auth/login") {
      const body = await readCredentials(req);
      return json({
        ok: true,
        data: await loginAccount(body.nickname, body.password),
      });
    }
    if (req.method === "GET" && url.pathname === "/api/auth/me") {
      const { account } = await requireAccount(req);
      return json({ ok: true, data: { account } });
    }
    if (req.method === "POST" && url.pathname === "/api/auth/logout") {
      const token = bearerToken(req);
      if (token) await logoutSession(token);
      return json({ ok: true, data: {} });
    }
    if (req.method === "GET" && url.pathname === "/api/stats/me") {
      const { account } = await requireAccount(req);
      const stored = await getStats(account.accountId);
      return json({
        ok: true,
        data: {
          playerId: account.accountId,
          name: account.nickname,
          totalScore: stored?.totalScore ?? 0,
          games: stored?.games ?? 0,
          wins: stored?.wins ?? 0,
        },
      });
    }
    return json(
      { ok: false, error: { code: "NOT_FOUND", message: "接口不存在" } },
      404,
    );
  } catch (error) {
    if (error instanceof AuthError) {
      return json(
        { ok: false, error: { code: error.code, message: error.message } },
        error.status,
      );
    }
    if (error instanceof SyntaxError) {
      return json(
        {
          ok: false,
          error: { code: "INVALID_JSON", message: "请求内容格式错误" },
        },
        400,
      );
    }
    console.error("API request failed", error);
    return json(
      {
        ok: false,
        error: { code: "INTERNAL_ERROR", message: "服务器暂时不可用" },
      },
      500,
    );
  }
}

async function readCredentials(req: Request): Promise<{
  nickname: string;
  password: string;
}> {
  const body = await req.json() as Record<string, unknown>;
  return {
    nickname: String(body.nickname ?? ""),
    password: String(body.password ?? ""),
  };
}

async function requireAccount(req: Request) {
  const token = bearerToken(req);
  const account = token ? await resolveSession(token) : null;
  if (!account) {
    throw new AuthError("登录已失效，请重新登录", "AUTH_REQUIRED", 401);
  }
  return { token, account };
}

function bearerToken(req: Request): string {
  const authorization = req.headers.get("authorization") ?? "";
  return authorization.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: {
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS",
    },
  });
}
