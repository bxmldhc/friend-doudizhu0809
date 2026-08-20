import {
  AuthError,
  changeOwnPassword,
  countAccounts,
  createAccountForAdmin,
  getPublicAccount,
  getRegistrationSettings,
  listAccountsForAdmin,
  loginAccount,
  logoutOtherSessions,
  logoutSession,
  registerAccount,
  resetAccountPassword,
  resolveSession,
  setAccountDisabled,
  setRegistrationEnabled,
} from "./game/auth.ts";
import type { AdminRoomOverview, AdminRoomSnapshot } from "./game/rooms.ts";
import {
  getRecentMatches,
  getStats,
  listAdminAudit,
  recordAdminAudit,
} from "./game/store.ts";

export interface AdminRoomReader {
  getAdminOverview(): AdminRoomOverview;
  getAdminRoomSnapshots(): AdminRoomSnapshot[];
}

export interface ApiDependencies {
  roomManager?: AdminRoomReader;
}

export async function handleApiRequest(
  req: Request,
  dependencies: ApiDependencies = {},
): Promise<Response | null> {
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
    if (
      req.method === "POST" &&
      url.pathname === "/api/auth/change-password"
    ) {
      const { token } = await requireAccount(req);
      const body = await readJsonObject(req);
      const session = await changeOwnPassword(
        token,
        String(body.currentPassword ?? ""),
        String(body.newPassword ?? ""),
      );
      return json({
        ok: true,
        data: { ...session, sessionsInvalidated: true },
      });
    }
    if (
      req.method === "POST" &&
      url.pathname === "/api/auth/logout-other-sessions"
    ) {
      const { token } = await requireAccount(req);
      const session = await logoutOtherSessions(token);
      return json({
        ok: true,
        data: { ...session, otherSessionsInvalidated: true },
      });
    }
    if (req.method === "GET" && url.pathname === "/api/stats/me") {
      const { account } = await requireAccount(req);
      const [stored, recentMatches] = await Promise.all([
        getStats(account.accountId),
        getRecentMatches(account.accountId),
      ]);
      return json({
        ok: true,
        data: {
          playerId: account.accountId,
          name: account.nickname,
          totalScore: stored?.totalScore ?? 0,
          games: stored?.games ?? 0,
          wins: stored?.wins ?? 0,
          landlordGames: stored?.landlordGames ?? 0,
          landlordWins: stored?.landlordWins ?? 0,
          farmerGames: stored?.farmerGames ?? 0,
          farmerWins: stored?.farmerWins ?? 0,
          recentMatches,
        },
      });
    }
    if (req.method === "GET" && url.pathname === "/api/admin/overview") {
      await requireAdmin(req);
      const roomManager = requireRoomManager(dependencies);
      const [accountCount, roomOverview] = await Promise.all([
        countAccounts(),
        Promise.resolve(roomManager.getAdminOverview()),
      ]);
      return json({
        ok: true,
        data: {
          accountCount,
          ...roomOverview,
        },
      });
    }
    if (req.method === "GET" && url.pathname === "/api/admin/accounts") {
      await requireAdmin(req);
      const query = url.searchParams.get("query") ?? "";
      const limit = parseLimit(url.searchParams.get("limit"));
      const result = await listAccountsForAdmin(query, limit);
      const accounts = await Promise.all(
        result.accounts.map(async (account) => {
          const stats = await getStats(account.accountId);
          return {
            ...account,
            totalScore: stats?.totalScore ?? 0,
            games: stats?.games ?? 0,
            wins: stats?.wins ?? 0,
          };
        }),
      );
      return json({
        ok: true,
        data: {
          accounts,
          total: result.total,
          limit: result.limit,
          query: result.query,
        },
      });
    }
    if (req.method === "POST" && url.pathname === "/api/admin/accounts") {
      const { account: actor } = await requireAdmin(req);
      const body = await readJsonObject(req);
      const account = await createAccountForAdmin(
        String(body.nickname ?? body.username ?? ""),
        String(body.temporaryPassword ?? body.password ?? ""),
      );
      await recordAdminAudit({
        actorAccountId: actor.accountId,
        actorNickname: actor.nickname,
        action: "ACCOUNT_CREATED" as Parameters<
          typeof recordAdminAudit
        >[0]["action"],
        targetAccountId: account.accountId,
        targetNickname: account.nickname,
      });
      return json({ ok: true, data: { account } }, 201);
    }
    if (req.method === "GET" && url.pathname === "/api/admin/rooms") {
      await requireAdmin(req);
      const roomManager = requireRoomManager(dependencies);
      return json({
        ok: true,
        data: {
          rooms: roomManager.getAdminRoomSnapshots(),
        },
      });
    }
    if (
      req.method === "GET" &&
      url.pathname === "/api/admin/registration"
    ) {
      await requireAdmin(req);
      return json({ ok: true, data: await getRegistrationSettings() });
    }
    if (
      req.method === "POST" &&
      url.pathname === "/api/admin/registration"
    ) {
      const { account: actor } = await requireAdmin(req);
      const body = await readJsonObject(req);
      if (typeof body.enabled !== "boolean") {
        throw new AuthError(
          "enabled 必须是布尔值",
          "INVALID_REGISTRATION_SETTING",
        );
      }
      const settings = await setRegistrationEnabled(body.enabled);
      await recordAdminAudit({
        actorAccountId: actor.accountId,
        actorNickname: actor.nickname,
        action: settings.enabled
          ? "REGISTRATION_ENABLED"
          : "REGISTRATION_DISABLED",
      });
      return json({ ok: true, data: settings });
    }
    if (req.method === "GET" && url.pathname === "/api/admin/audit") {
      await requireAdmin(req);
      const limit = parseLimit(url.searchParams.get("limit"));
      return json({
        ok: true,
        data: { records: await listAdminAudit(limit), limit },
      });
    }
    const accountStatusMatch = url.pathname.match(
      /^\/api\/admin\/accounts\/([^/]+)\/status$/,
    );
    if (req.method === "POST" && accountStatusMatch) {
      const { account: actor } = await requireAdmin(req);
      const accountId = decodePathSegment(accountStatusMatch[1]);
      const body = await readJsonObject(req);
      if (typeof body.disabled !== "boolean") {
        throw new AuthError(
          "disabled 必须是布尔值",
          "INVALID_ACCOUNT_STATUS",
        );
      }
      if (accountId === actor.accountId && body.disabled) {
        throw new AuthError(
          "不能禁用当前管理员账号",
          "CANNOT_DISABLE_SELF",
          409,
        );
      }
      const account = await setAccountDisabled(accountId, body.disabled);
      await recordAdminAudit({
        actorAccountId: actor.accountId,
        actorNickname: actor.nickname,
        action: account.disabled ? "ACCOUNT_DISABLED" : "ACCOUNT_ENABLED",
        targetAccountId: account.accountId,
        targetNickname: account.nickname,
      });
      return json({
        ok: true,
        data: { account, sessionsInvalidated: account.disabled },
      });
    }
    const resetPasswordMatch = url.pathname.match(
      /^\/api\/admin\/accounts\/([^/]+)\/reset-password$/,
    );
    if (req.method === "POST" && resetPasswordMatch) {
      const { account: actor } = await requireAdmin(req);
      const accountId = decodePathSegment(resetPasswordMatch[1]);
      const body = await readJsonObject(req);
      const account = await resetAccountPassword(
        accountId,
        String(body.password ?? body.newPassword ?? ""),
      );
      await recordAdminAudit({
        actorAccountId: actor.accountId,
        actorNickname: actor.nickname,
        action: "PASSWORD_RESET",
        targetAccountId: account.accountId,
        targetNickname: account.nickname,
      });
      return json({
        ok: true,
        data: { account, sessionsInvalidated: true },
      });
    }
    const playerStatsMatch = url.pathname.match(
      /^\/api\/players\/([^/]+)\/stats$/,
    );
    if (req.method === "GET" && playerStatsMatch) {
      await requireAccount(req);
      const playerId = decodePathSegment(playerStatsMatch[1]);
      const account = playerId ? await getPublicAccount(playerId) : null;
      if (!account) {
        return json(
          {
            ok: false,
            error: {
              code: "PLAYER_NOT_FOUND",
              message: "未找到该玩家账号",
            },
          },
          404,
        );
      }
      const stored = await getStats(account.accountId);
      return json({
        ok: true,
        data: {
          playerId: account.accountId,
          name: account.nickname,
          totalScore: stored?.totalScore ?? 0,
          games: stored?.games ?? 0,
          wins: stored?.wins ?? 0,
          landlordGames: stored?.landlordGames ?? 0,
          landlordWins: stored?.landlordWins ?? 0,
          farmerGames: stored?.farmerGames ?? 0,
          farmerWins: stored?.farmerWins ?? 0,
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
  const body = await readJsonObject(req);
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

async function requireAdmin(req: Request) {
  const session = await requireAccount(req);
  if (!session.account.isAdmin) {
    throw new AuthError("需要管理员权限", "ADMIN_REQUIRED", 403);
  }
  return session;
}

function requireRoomManager(dependencies: ApiDependencies): AdminRoomReader {
  if (!dependencies.roomManager) {
    throw new Error("Room manager dependency is not configured");
  }
  return dependencies.roomManager;
}

async function readJsonObject(req: Request): Promise<Record<string, unknown>> {
  const body = await req.json();
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new SyntaxError("Request body must be a JSON object");
  }
  return body as Record<string, unknown>;
}

function parseLimit(value: string | null): number {
  if (value === null || value.trim() === "") return 100;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new AuthError("limit 必须是正整数", "INVALID_LIMIT");
  }
  return Math.min(parsed, 100);
}

function bearerToken(req: Request): string {
  const authorization = req.headers.get("authorization") ?? "";
  return authorization.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
}

function decodePathSegment(value: string): string {
  try {
    return decodeURIComponent(value).trim();
  } catch {
    return "";
  }
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
