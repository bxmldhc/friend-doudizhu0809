import { handleApiRequest } from "./api.ts";
import { AdminRoomOverview, AdminRoomSnapshot } from "./game/rooms.ts";
import { kv } from "./game/db.ts";
import { isAdminAccount, resolveSession } from "./game/auth.ts";

interface ApiResponse {
  ok: boolean;
  data?: Record<string, unknown>;
  error?: {
    code?: string;
    message?: string;
  };
}

const emptyRooms = {
  getAdminOverview(): AdminRoomOverview {
    return {
      roomCount: 2,
      activeGameCount: 1,
      connectedHumanCount: 3,
      occupiedHumanCount: 4,
      aiCount: 2,
    };
  },
  getAdminRoomSnapshots(): AdminRoomSnapshot[] {
    return [{
      roomCode: "123456",
      createdAt: 1_700_000_000_000,
      phase: "PLAYING",
      gameType: "DOUDIZHU",
      gameNumber: 2,
      difficulty: "HARD",
      aiCount: 1,
      actionTimeoutSeconds: 20,
      connectedHumanCount: 1,
      occupiedHumanCount: 2,
      hostSeat: 0,
      hostName: "管理员",
      seats: [
        {
          seat: 0,
          name: "管理员",
          isAi: false,
          isConnected: true,
          isAway: false,
          isHost: true,
        },
        {
          seat: 1,
          name: "朋友",
          isAi: false,
          isConnected: false,
          isAway: true,
          isHost: false,
        },
        {
          seat: 2,
          name: "小智",
          isAi: true,
          isConnected: false,
          isAway: false,
          isHost: false,
        },
      ],
    }];
  },
};

Deno.test("管理员 API 权限、查询和密码重置保持安全边界", async (t) => {
  const envNames = [
    "AUTH_PBKDF2_ITERATIONS",
    "ADMIN_ACCOUNT_IDS",
    "ADMIN_NICKNAMES",
  ] as const;
  const previousEnv = new Map(
    envNames.map((name) => [name, Deno.env.get(name)]),
  );
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  Deno.env.set("ADMIN_ACCOUNT_IDS", "");

  const suffix = crypto.randomUUID().replaceAll("-", "").slice(0, 7);
  const adminNickname = `admin${suffix}`;
  const userNickname = `user${suffix}`;
  const managedNickname = `mgr${suffix}`;
  Deno.env.set("ADMIN_NICKNAMES", `${adminNickname},dlc`);
  const adminPassword = "Admin123!";
  const userPassword = "User123!";
  const newPassword = "Changed456!";
  const temporaryPassword = "Temporary789!";
  const managedPassword = "Managed456!";
  const cleanup: Array<{
    accountId: string;
    nicknameKey: string;
    tokens: string[];
  }> = [];
  const auditKeys: Deno.KvKey[] = [];
  const registrationEntry = await kv.get(["settings", "registration"]);

  try {
    const admin = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: adminNickname, password: adminPassword },
    });
    const adminAccount = asRecord(admin.body.data?.account, "管理员账号");
    const adminId = asString(adminAccount.accountId, "管理员 accountId");
    const adminToken = asString(admin.body.data?.token, "管理员 token");
    cleanup.push({
      accountId: adminId,
      nicknameKey: adminNickname,
      tokens: [adminToken],
    });

    const user = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: userNickname, password: userPassword },
    });
    const userAccount = asRecord(user.body.data?.account, "普通账号");
    const userId = asString(userAccount.accountId, "普通账号 accountId");
    const userToken = asString(user.body.data?.token, "普通账号 token");
    cleanup.push({
      accountId: userId,
      nicknameKey: userNickname.toLocaleLowerCase("zh-CN"),
      tokens: [userToken],
    });

    await t.step("角色只由服务端环境变量计算", () => {
      assertEquals(adminAccount.role, "ADMIN", "管理员 role");
      assertEquals(adminAccount.isAdmin, true, "管理员 isAdmin");
      assertEquals(userAccount.role, "USER", "普通账号 role");
      assertEquals(userAccount.isAdmin, false, "普通账号 isAdmin");
      assertNoSecrets(admin.body);
      assertNoSecrets(user.body);
      assertEquals(
        isAdminAccount({ accountId: "unused", nicknameKey: "dlc" }),
        true,
        "dlc 兼容管理员白名单",
      );
    });

    await t.step("未登录和普通账号不能调用管理员 API", async () => {
      const unauthenticated = await requestApi("/api/admin/overview");
      assertEquals(
        unauthenticated.response.status,
        401,
        "未登录管理接口状态码",
      );
      assertEquals(
        unauthenticated.body.error?.code,
        "AUTH_REQUIRED",
        "未登录管理接口错误码",
      );

      const forbidden = await requestApi("/api/admin/overview", {
        token: userToken,
      });
      assertEquals(forbidden.response.status, 403, "普通账号管理接口状态码");
      assertEquals(
        forbidden.body.error?.code,
        "ADMIN_REQUIRED",
        "普通账号管理接口错误码",
      );
    });

    await t.step("管理员可读取账号统计和脱敏房间信息", async () => {
      const overview = await requestApi("/api/admin/overview", {
        token: adminToken,
      });
      assertEquals(overview.response.status, 200, "管理概览状态码");
      assertEquals(overview.body.data?.roomCount, 2, "管理概览房间数");
      assertEquals(overview.body.data?.activeGameCount, 1, "进行中房间数");
      if (Number(overview.body.data?.accountCount) < 2) {
        throw new Error("管理概览账号数没有包含测试账号");
      }

      const rooms = await requestApi("/api/admin/rooms", {
        token: adminToken,
      });
      assertEquals(rooms.response.status, 200, "管理房间状态码");
      const serialized = JSON.stringify(rooms.body).toLocaleLowerCase();
      for (
        const forbiddenKey of [
          "resumetoken",
          "passwordhash",
          "passwordsalt",
          "bottomcards",
        ]
      ) {
        if (serialized.includes(forbiddenKey)) {
          throw new Error(`管理房间接口泄露字段 ${forbiddenKey}`);
        }
      }
    });

    await t.step("账号列表最多返回 100 条并支持 query", async () => {
      const accounts = await requestApi(
        `/api/admin/accounts?limit=999&query=${
          encodeURIComponent(userNickname)
        }`,
        { token: adminToken },
      );
      assertEquals(accounts.response.status, 200, "账号列表状态码");
      assertEquals(accounts.body.data?.limit, 100, "账号列表上限");
      assertEquals(accounts.body.data?.total, 1, "账号查询匹配数");
      const items = accounts.body.data?.accounts;
      if (!Array.isArray(items) || items.length !== 1) {
        throw new Error("账号 query 没有只返回匹配账号");
      }
      const listed = asRecord(items[0], "账号列表项");
      assertEquals(listed.accountId, userId, "账号列表 accountId");
      assertEquals(listed.role, "USER", "账号列表 role");
      assertEquals(listed.disabled, false, "账号列表禁用状态");
      assertNoSecrets(accounts.body);
    });

    await t.step("注册开关仅管理员可改且关闭后拒绝注册", async () => {
      const forbidden = await requestApi("/api/admin/registration", {
        token: userToken,
      });
      assertEquals(forbidden.response.status, 403, "普通账号读取注册开关");

      const disabled = await requestApi("/api/admin/registration", {
        method: "POST",
        token: adminToken,
        body: { enabled: false },
      });
      assertEquals(disabled.response.status, 200, "关闭注册状态码");
      assertEquals(disabled.body.data?.enabled, false, "关闭注册结果");

      const rejected = await requestApi("/api/auth/register", {
        method: "POST",
        body: {
          nickname: `new${suffix}`,
          password: "NewUser123!",
        },
      });
      assertEquals(rejected.response.status, 403, "注册关闭状态码");
      assertEquals(
        rejected.body.error?.code,
        "REGISTRATION_DISABLED",
        "注册关闭错误码",
      );

      const invalidPassword = await requestApi("/api/admin/accounts", {
        method: "POST",
        token: adminToken,
        body: { nickname: managedNickname, temporaryPassword: "short" },
      });
      assertEquals(
        invalidPassword.response.status,
        400,
        "管理员弱密码创建状态码",
      );
      assertEquals(
        invalidPassword.body.error?.code,
        "INVALID_PASSWORD",
        "管理员弱密码创建错误码",
      );

      const managed = await requestApi("/api/admin/accounts", {
        method: "POST",
        token: adminToken,
        body: { nickname: managedNickname, temporaryPassword },
      });
      assertEquals(managed.response.status, 201, "管理员创建账号状态码");
      const managedAccount = asRecord(
        managed.body.data?.account,
        "管理员创建的账号",
      );
      const managedId = asString(
        managedAccount.accountId,
        "管理员创建账号 accountId",
      );
      assertEquals(
        managedAccount.mustChangePassword,
        true,
        "管理员创建账号必须修改临时密码",
      );
      cleanup.push({
        accountId: managedId,
        nicknameKey: managedNickname.toLocaleLowerCase("zh-CN"),
        tokens: [],
      });
      assertNoSecrets(managed.body);

      const duplicate = await requestApi("/api/admin/accounts", {
        method: "POST",
        token: adminToken,
        body: { nickname: managedNickname, temporaryPassword },
      });
      assertEquals(duplicate.response.status, 409, "管理员创建重名账号状态码");
      assertEquals(
        duplicate.body.error?.code,
        "NICKNAME_TAKEN",
        "管理员创建重名账号错误码",
      );

      const enabled = await requestApi("/api/admin/registration", {
        method: "POST",
        token: adminToken,
        body: { enabled: true },
      });
      assertEquals(enabled.body.data?.enabled, true, "恢复注册结果");
    });

    await t.step("临时密码账号登录后必须改密，改密后解除限制", async () => {
      const managedLogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: managedNickname, password: temporaryPassword },
      });
      assertEquals(managedLogin.response.status, 200, "临时密码登录状态码");
      const managedAccount = asRecord(
        managedLogin.body.data?.account,
        "临时密码登录账号",
      );
      assertEquals(
        managedAccount.mustChangePassword,
        true,
        "临时密码登录响应必须包含改密标记",
      );
      const managedToken = asString(
        managedLogin.body.data?.token,
        "临时密码账号 token",
      );
      cleanup[2].tokens.push(managedToken);

      const changed = await requestApi("/api/auth/change-password", {
        method: "POST",
        token: managedToken,
        body: {
          currentPassword: temporaryPassword,
          newPassword: managedPassword,
        },
      });
      assertEquals(changed.response.status, 200, "临时密码改密状态码");
      const changedAccount = asRecord(
        changed.body.data?.account,
        "临时密码改密账号",
      );
      assertEquals(
        changedAccount.mustChangePassword,
        false,
        "自行改密后必须解除改密标记",
      );
      cleanup[2].tokens.push(
        asString(changed.body.data?.token, "临时密码改密后的 token"),
      );
    });

    await t.step("管理员可禁用恢复账号但不能禁用自己", async () => {
      const selfDisable = await requestApi(
        `/api/admin/accounts/${encodeURIComponent(adminId)}/status`,
        {
          method: "POST",
          token: adminToken,
          body: { disabled: true },
        },
      );
      assertEquals(selfDisable.response.status, 409, "管理员自禁用状态码");
      assertEquals(
        selfDisable.body.error?.code,
        "CANNOT_DISABLE_SELF",
        "管理员自禁用错误码",
      );

      const disabled = await requestApi(
        `/api/admin/accounts/${encodeURIComponent(userId)}/status`,
        {
          method: "POST",
          token: adminToken,
          body: { disabled: true },
        },
      );
      assertEquals(disabled.response.status, 200, "禁用账号状态码");
      assertEquals(
        asRecord(disabled.body.data?.account, "禁用账号").disabled,
        true,
        "禁用账号结果",
      );
      if (await resolveSession(userToken)) {
        throw new Error("管理员禁用账号后旧 token 仍然有效");
      }
      const blockedLogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: userNickname, password: userPassword },
      });
      assertEquals(blockedLogin.response.status, 403, "禁用账号登录状态码");
      assertEquals(
        blockedLogin.body.error?.code,
        "ACCOUNT_DISABLED",
        "禁用账号登录错误码",
      );

      const enabled = await requestApi(
        `/api/admin/accounts/${encodeURIComponent(userId)}/status`,
        {
          method: "POST",
          token: adminToken,
          body: { disabled: false },
        },
      );
      assertEquals(enabled.response.status, 200, "恢复账号状态码");
      const relogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: userNickname, password: userPassword },
      });
      assertEquals(relogin.response.status, 200, "恢复账号登录状态码");
      cleanup[1].tokens.push(
        asString(relogin.body.data?.token, "恢复后的 token"),
      );
    });

    await t.step("密码重置校验密码且使目标全部旧会话失效", async () => {
      const secondLogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: userNickname, password: userPassword },
      });
      const secondToken = asString(
        secondLogin.body.data?.token,
        "第二个 token",
      );
      cleanup[1].tokens.push(secondToken);

      const invalid = await requestApi(
        `/api/admin/accounts/${encodeURIComponent(userId)}/reset-password`,
        {
          method: "POST",
          token: adminToken,
          body: { password: "short" },
        },
      );
      assertEquals(invalid.response.status, 400, "弱密码重置状态码");
      assertEquals(
        invalid.body.error?.code,
        "INVALID_PASSWORD",
        "弱密码重置错误码",
      );

      const reset = await requestApi(
        `/api/admin/accounts/${encodeURIComponent(userId)}/reset-password`,
        {
          method: "POST",
          token: adminToken,
          body: {
            password: newPassword,
            isAdmin: true,
            role: "ADMIN",
          },
        },
      );
      assertEquals(reset.response.status, 200, "密码重置状态码");
      assertEquals(
        reset.body.data?.sessionsInvalidated,
        true,
        "旧会话失效标记",
      );
      assertEquals(
        asRecord(reset.body.data?.account, "重置密码账号").mustChangePassword,
        true,
        "管理员重置后的密码应标记为临时密码",
      );
      assertNoSecrets(reset.body);

      if (
        await resolveSession(userToken) ||
        await resolveSession(secondToken)
      ) {
        throw new Error("密码重置后目标账号仍有旧会话有效");
      }
      const oldLogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: userNickname, password: userPassword },
      });
      assertEquals(oldLogin.response.status, 401, "旧密码登录状态码");

      const newLogin = await requestApi("/api/auth/login", {
        method: "POST",
        body: { nickname: userNickname, password: newPassword },
      });
      assertEquals(newLogin.response.status, 200, "新密码登录状态码");
      const newAccount = asRecord(newLogin.body.data?.account, "重登账号");
      assertEquals(
        newAccount.isAdmin,
        false,
        "客户端自报权限不应改变账号角色",
      );
      assertEquals(
        newAccount.mustChangePassword,
        true,
        "使用管理员重置密码登录后必须自行改密",
      );
      cleanup[1].tokens.push(
        asString(newLogin.body.data?.token, "重置后 token"),
      );
    });

    await t.step("审计接口记录管理动作且不包含密码和 token", async () => {
      const audit = await requestApi("/api/admin/audit?limit=20", {
        token: adminToken,
      });
      assertEquals(audit.response.status, 200, "审计接口状态码");
      const records = audit.body.data?.records;
      if (!Array.isArray(records)) throw new Error("审计记录不是数组");
      const actions = new Set(
        records.map((record) => asRecord(record, "审计项").action),
      );
      for (
        const expected of [
          "REGISTRATION_DISABLED",
          "REGISTRATION_ENABLED",
          "ACCOUNT_DISABLED",
          "ACCOUNT_ENABLED",
          "ACCOUNT_CREATED",
          "PASSWORD_RESET",
        ]
      ) {
        if (!actions.has(expected)) {
          throw new Error(`审计记录缺少动作 ${expected}`);
        }
      }
      assertNoSecrets(audit.body);
      const serialized = JSON.stringify(audit.body).toLocaleLowerCase();
      if (
        serialized.includes(adminToken.toLocaleLowerCase()) ||
        serialized.includes(newPassword.toLocaleLowerCase())
      ) {
        throw new Error("审计接口泄露 token 或密码");
      }
    });
  } finally {
    if (registrationEntry.value === null) {
      await kv.delete(["settings", "registration"]);
    } else {
      await kv.set(["settings", "registration"], registrationEntry.value);
    }
    for await (
      const entry of kv.list<{ actorAccountId?: string }>({
        prefix: ["adminAudit"],
      })
    ) {
      if (entry.value.actorAccountId === cleanup[0]?.accountId) {
        auditKeys.push(entry.key);
      }
    }
    for (const key of auditKeys) await kv.delete(key);
    for (const account of cleanup) {
      for (const token of account.tokens) {
        await kv.delete(["sessions", token]);
      }
      await kv.delete(["accounts", account.accountId]);
      await kv.delete(["accountByNickname", account.nicknameKey]);
      await kv.delete(["players", account.accountId]);
      await kv.delete(["recentMatches", account.accountId]);
    }
    for (const [name, value] of previousEnv) {
      if (value === undefined) {
        Deno.env.delete(name);
      } else {
        Deno.env.set(name, value);
      }
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
    { roomManager: emptyRooms },
  );
  if (!response) throw new Error(`路由没有处理 ${path}`);
  return {
    response,
    body: await response.json() as ApiResponse,
  };
}

function assertNoSecrets(value: unknown) {
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
        throw new Error(`API 响应泄露内部字段 ${key}`);
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
