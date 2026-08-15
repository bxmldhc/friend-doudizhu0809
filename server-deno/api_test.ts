import { handleApiRequest } from "./api.ts";
import { kv } from "./game/db.ts";
import { recordGame } from "./game/store.ts";

interface ApiResponse {
  ok: boolean;
  data?: Record<string, unknown>;
  error?: {
    code?: string;
    message?: string;
  };
}

Deno.test("REST account API routes preserve identity and session boundaries", async (t) => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");

  const nickname = `测${crypto.randomUUID().replaceAll("-", "").slice(0, 9)}`;
  const nicknameKey = nickname.normalize("NFKC").toLocaleLowerCase("zh-CN");
  const password = "ApiTest!123";
  let accountId = "";
  let token = "";

  try {
    await t.step(
      "register succeeds without leaking password material",
      async () => {
        const { response, body } = await requestApi("/api/auth/register", {
          method: "POST",
          body: { nickname, password },
        });

        assertEquals(response.status, 200, "注册状态码");
        assertEquals(body.ok, true, "注册结果");
        assertNoPasswordMaterial(body);

        const account = asRecord(body.data?.account, "注册账号");
        accountId = asString(account.accountId, "accountId");
        token = asString(body.data?.token, "token");
        assertEquals(account.nickname, nickname, "注册昵称");
      },
    );

    await t.step("duplicate nickname is rejected", async () => {
      const { response, body } = await requestApi("/api/auth/register", {
        method: "POST",
        body: { nickname: nickname.toUpperCase(), password },
      });

      assertEquals(response.status, 409, "昵称冲突状态码");
      assertEquals(body.ok, false, "昵称冲突结果");
      assertEquals(body.error?.code, "NICKNAME_TAKEN", "昵称冲突错误码");
      assertNoPasswordMaterial(body);
    });

    await t.step("wrong password is rejected", async () => {
      const { response, body } = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname, password: "WrongPassword!" },
      });

      assertEquals(response.status, 401, "错误密码状态码");
      assertEquals(body.ok, false, "错误密码结果");
      assertEquals(
        body.error?.code,
        "INVALID_CREDENTIALS",
        "错误密码错误码",
      );
      assertNoPasswordMaterial(body);
    });

    await t.step("me requires a bearer token", async () => {
      const { response, body } = await requestApi("/api/auth/me");

      assertEquals(response.status, 401, "未登录 me 状态码");
      assertEquals(body.ok, false, "未登录 me 结果");
      assertEquals(body.error?.code, "AUTH_REQUIRED", "未登录 me 错误码");
      assertNoPasswordMaterial(body);
    });

    await t.step("stats are bound to the immutable accountId", async () => {
      await recordGame([{
        playerId: accountId,
        name: nickname,
        delta: 6,
        isAi: false,
      }]);

      const { response, body } = await requestApi("/api/stats/me", {
        token,
      });

      assertEquals(response.status, 200, "战绩状态码");
      assertEquals(body.ok, true, "战绩结果");
      assertEquals(body.data?.playerId, accountId, "战绩 accountId");
      assertEquals(body.data?.name, nickname, "战绩昵称");
      assertEquals(body.data?.totalScore, 6, "累计分数");
      assertEquals(body.data?.games, 1, "累计局数");
      assertEquals(body.data?.wins, 1, "累计胜局");
      assertNoPasswordMaterial(body);
    });

    await t.step("logout invalidates the current token", async () => {
      const logout = await requestApi("/api/auth/logout", {
        method: "POST",
        token,
      });
      assertEquals(logout.response.status, 200, "退出状态码");
      assertEquals(logout.body.ok, true, "退出结果");
      assertNoPasswordMaterial(logout.body);

      const me = await requestApi("/api/auth/me", { token });
      assertEquals(me.response.status, 401, "退出后 me 状态码");
      assertEquals(me.body.error?.code, "AUTH_REQUIRED", "退出后 me 错误码");
      assertNoPasswordMaterial(me.body);
    });
  } finally {
    if (token) await kv.delete(["sessions", token]);
    if (accountId) {
      await kv.delete(["accounts", accountId]);
      await kv.delete(["players", accountId]);
    }
    await kv.delete(["accountByNickname", nicknameKey]);

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
    body?: Record<string, unknown>;
    token?: string;
  } = {},
): Promise<{ response: Response; body: ApiResponse }> {
  const headers = new Headers();
  if (options.body) headers.set("content-type", "application/json");
  if (options.token) {
    headers.set("authorization", `Bearer ${options.token}`);
  }
  const response = await handleApiRequest(
    new Request(`http://localhost${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
    }),
  );
  if (!response) throw new Error(`路由没有处理 ${path}`);
  return {
    response,
    body: await response.json() as ApiResponse,
  };
}

function assertNoPasswordMaterial(value: unknown) {
  const forbiddenKeys = new Set([
    "nicknamekey",
    "passwordhash",
    "passwordsalt",
    "passworditerations",
  ]);

  const visit = (current: unknown) => {
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    if (!current || typeof current !== "object") return;
    for (const [key, child] of Object.entries(current)) {
      if (forbiddenKeys.has(key.toLocaleLowerCase())) {
        throw new Error(`API 响应泄露了内部字段 ${key}`);
      }
      visit(child);
    }
  };

  visit(value);
}

function asRecord(value: unknown, label: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} 不是对象`);
  }
  return value as Record<string, unknown>;
}

function asString(value: unknown, label: string): string {
  if (typeof value !== "string" || !value) {
    throw new Error(`${label} 不是非空字符串`);
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
