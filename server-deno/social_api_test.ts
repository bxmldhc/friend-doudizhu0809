import { handleApiRequest } from "./api.ts";
import { clearPresence, listAppOnlineAccounts } from "./game/social.ts";
import { kv } from "./game/db.ts";

interface ApiResponse {
  ok: boolean;
  data?: Record<string, unknown>;
  error?: { code?: string };
}

Deno.test("好友读取兼容接口与应用在线状态保持一致", async (t) => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");

  const suffix = crypto.randomUUID().replaceAll("-", "").slice(0, 8);
  const firstNickname = `好友甲${suffix}`;
  const secondNickname = `好友乙${suffix}`;
  const password = "SocialTest!123";
  const cleanup: Array<{ accountId: string; nickname: string; token: string }> =
    [];
  let friendRequestId = "";

  try {
    const first = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: firstNickname, password },
    });
    const firstSession = readSession(first.body);
    cleanup.push({ ...firstSession, nickname: firstNickname });

    await t.step("注册成功后无需等待心跳就显示应用在线", async () => {
      const online = await listAppOnlineAccounts();
      if (
        !online.some((account) => account.accountId === firstSession.accountId)
      ) {
        throw new Error("注册账号没有立即写入应用在线状态");
      }
    });

    const second = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: secondNickname, password },
    });
    const secondSession = readSession(second.body);
    cleanup.push({ ...secondSession, nickname: secondNickname });

    await t.step("好友聚合与拆分读取接口返回一致数据", async () => {
      const sent = await requestApi("/api/friends/requests", {
        method: "POST",
        token: firstSession.token,
        body: { targetAccountId: secondSession.accountId },
      });
      assertEquals(sent.response.status, 201, "发送申请状态码");

      const aggregate = await requestApi("/api/friends", {
        token: secondSession.token,
      });
      const requests = await requestApi(
        "/api/friends/requests?direction=INCOMING",
        {
          token: secondSession.token,
        },
      );
      assertEquals(aggregate.response.status, 200, "好友聚合接口状态码");
      assertEquals(requests.response.status, 200, "好友申请接口状态码");
      const aggregateRequests = asArray(
        aggregate.body.data?.requests,
        "聚合申请",
      );
      const splitRequests = asArray(requests.body.data?.requests, "拆分申请");
      assertEquals(aggregateRequests.length, 1, "聚合申请数量");
      assertEquals(splitRequests.length, 1, "拆分申请数量");

      const requestId = asString(
        asRecord(splitRequests[0], "好友申请").requestId,
        "好友申请 requestId",
      );
      friendRequestId = requestId;
      const accepted = await requestApi(
        `/api/friends/requests/${encodeURIComponent(requestId)}/accept`,
        { method: "POST", token: secondSession.token },
      );
      assertEquals(accepted.response.status, 200, "接受申请状态码");

      const firstList = await requestApi("/api/friends/list", {
        token: firstSession.token,
      });
      const secondList = await requestApi("/api/friends", {
        token: secondSession.token,
      });
      const firstFriends = asArray(firstList.body.data?.friends, "拆分好友");
      const secondFriends = asArray(secondList.body.data?.friends, "聚合好友");
      assertEquals(firstFriends.length, 1, "拆分好友数量");
      assertEquals(secondFriends.length, 1, "聚合好友数量");
      assertEquals(
        asRecord(firstFriends[0], "拆分好友项").accountId,
        secondSession.accountId,
        "拆分好友账号",
      );
    });

    await t.step("已登录请求会刷新在线状态，心跳接口仍保持可用", async () => {
      const me = await requestApi("/api/auth/me", {
        token: firstSession.token,
      });
      const heartbeat = await requestApi("/api/presence/heartbeat", {
        method: "POST",
        token: firstSession.token,
      });
      assertEquals(me.response.status, 200, "me 状态码");
      assertEquals(heartbeat.response.status, 200, "心跳状态码");
      assertEquals(heartbeat.body.data?.online, true, "心跳在线标记");
    });

    await t.step("退出登录后立即从应用在线列表中移除", async () => {
      const secondSessionResponse = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: firstNickname, password },
      });
      const secondSession = readSession(secondSessionResponse.body);
      cleanup.push({ ...secondSession, nickname: firstNickname });

      const logout = await requestApi("/api/auth/logout", {
        method: "POST",
        token: firstSession.token,
      });
      assertEquals(logout.response.status, 200, "退出登录状态码");
      const online = await listAppOnlineAccounts();
      if (
        !online.some((account) => account.accountId === firstSession.accountId)
      ) {
        throw new Error("另一台设备仍在线时不应被当前会话登出误清除");
      }
      const secondLogout = await requestApi("/api/auth/logout", {
        method: "POST",
        token: secondSession.token,
      });
      assertEquals(secondLogout.response.status, 200, "第二个会话退出状态码");
      const afterAllLogout = await listAppOnlineAccounts();
      if (
        afterAllLogout.some((account) =>
          account.accountId === firstSession.accountId
        )
      ) {
        throw new Error("全部会话退出后账号仍显示为应用在线");
      }
    });
  } finally {
    for (const account of cleanup) {
      await clearPresence(account.accountId);
      await kv.delete(["sessions", account.token]);
      await kv.delete(["accounts", account.accountId]);
      await kv.delete([
        "accountByNickname",
        account.nickname.normalize("NFKC").toLocaleLowerCase("zh-CN"),
      ]);
      await kv.delete(["friendsByAccount", account.accountId]);
    }
    if (friendRequestId) {
      await kv.delete(["friendRequests", friendRequestId]);
    }
    if (cleanup.length === 2) {
      const [first, second] = cleanup.map((account) => account.accountId)
        .sort();
      await kv.delete(["friendRequestByPair", first, second]);
      await kv.delete(["friendsByAccount", first, second]);
      await kv.delete(["friendsByAccount", second, first]);
    }
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

async function requestApi(
  path: string,
  options: {
    method?: string;
    token?: string;
    body?: Record<string, unknown>;
  } = {},
): Promise<{ response: Response; body: ApiResponse }> {
  const headers = new Headers();
  if (options.token) headers.set("authorization", `Bearer ${options.token}`);
  if (options.body) headers.set("content-type", "application/json");
  const response = await handleApiRequest(
    new Request(`http://localhost${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
    }),
  );
  if (!response) throw new Error(`路由没有处理 ${path}`);
  return { response, body: await response.json() as ApiResponse };
}

function readSession(body: ApiResponse): {
  accountId: string;
  token: string;
} {
  const data = asRecord(body.data, "认证响应");
  return {
    token: asString(data.token, "token"),
    accountId: asString(asRecord(data.account, "账号").accountId, "accountId"),
  };
}

function asRecord(value: unknown, label: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label}不是对象`);
  }
  return value as Record<string, unknown>;
}

function asArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) throw new Error(`${label}不是数组`);
  return value;
}

function asString(value: unknown, label: string): string {
  if (typeof value !== "string" || !value) {
    throw new Error(`${label}不是非空字符串`);
  }
  return value;
}

function assertEquals(actual: unknown, expected: unknown, label: string) {
  if (!Object.is(actual, expected)) {
    throw new Error(
      `${label}不符合预期：期望 ${JSON.stringify(expected)}，实际 ${
        JSON.stringify(actual)
      }`,
    );
  }
}
