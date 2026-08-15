import {
  AuthError,
  changeOwnPassword,
  getRegistrationSettings,
  loginAccount,
  logoutOtherSessions,
  logoutSession,
  registerAccount,
  resetAccountPassword,
  resolveSession,
  setAccountDisabled,
  setRegistrationEnabled,
} from "./auth.ts";
import { kv } from "./db.ts";
import { getStats, recordGame } from "./store.ts";

Deno.test("account login, uniqueness, session and stats use accountId", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const suffix = crypto.randomUUID().slice(0, 8);
  const nickname = `玩家${suffix}`;

  try {
    const registered = await registerAccount(nickname, "secret12");
    if (
      registered.account.nickname !== nickname ||
      !registered.account.accountId ||
      !registered.token
    ) {
      throw new Error("注册没有返回完整账号和会话");
    }

    const resolved = await resolveSession(registered.token);
    if (resolved?.accountId !== registered.account.accountId) {
      throw new Error("会话没有解析到注册账号");
    }

    await assertAuthError(
      () => registerAccount(nickname.toUpperCase(), "secret12"),
      "NICKNAME_TAKEN",
    );
    await assertAuthError(
      () => loginAccount(nickname, "wrong-password"),
      "INVALID_CREDENTIALS",
    );

    const loggedIn = await loginAccount(nickname, "secret12");
    if (loggedIn.account.accountId !== registered.account.accountId) {
      throw new Error("再次登录后 accountId 发生变化");
    }

    await recordGame([{
      playerId: registered.account.accountId,
      name: nickname,
      delta: 4,
      isAi: false,
    }]);
    const stats = await getStats(registered.account.accountId);
    if (
      stats?.totalScore !== 4 ||
      stats.games !== 1 ||
      stats.wins !== 1
    ) {
      throw new Error("累计战绩没有按 accountId 保存");
    }

    await logoutSession(registered.token);
    if (await resolveSession(registered.token)) {
      throw new Error("退出登录后会话仍然有效");
    }
  } finally {
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

Deno.test("password reset revokes every earlier session by account version", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const nickname = `重置${crypto.randomUUID().slice(0, 8)}`;

  try {
    const first = await registerAccount(nickname, "secret12");
    const second = await loginAccount(nickname, "secret12");
    await resetAccountPassword(first.account.accountId, "changed12");

    if (
      await resolveSession(first.token) ||
      await resolveSession(second.token)
    ) {
      throw new Error("密码重置后旧会话仍然有效");
    }

    const current = await loginAccount(nickname, "changed12");
    if (
      (await resolveSession(current.token))?.accountId !==
        first.account.accountId
    ) {
      throw new Error("新密码登录后的会话无法解析");
    }
  } finally {
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

Deno.test("account status, registration settings and self-service sessions stay secure", async () => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");
  const suffix = crypto.randomUUID().slice(0, 8);
  const nickname = `安全${suffix}`;
  const blockedNickname = `停用${suffix}`;
  const tokens: string[] = [];
  const accountIds: string[] = [];
  const registrationEntry = await kv.get(["settings", "registration"]);
  try {
    const first = await registerAccount(nickname, "secret12");
    accountIds.push(first.account.accountId);
    tokens.push(first.token);
    const second = await loginAccount(nickname, "secret12");
    tokens.push(second.token);

    const retained = await logoutOtherSessions(first.token);
    if (retained.token !== first.token) {
      throw new Error("退出其他设备不应替换当前 token");
    }
    if (!(await resolveSession(first.token))) {
      throw new Error("退出其他设备错误地注销了当前设备");
    }
    if (await resolveSession(second.token)) {
      throw new Error("退出其他设备后旧设备仍然有效");
    }

    const third = await loginAccount(nickname, "secret12");
    tokens.push(third.token);
    await assertAuthError(
      () => changeOwnPassword(first.token, "wrong12", "changed12"),
      "CURRENT_PASSWORD_INVALID",
    );
    if (!(await resolveSession(first.token))) {
      throw new Error("改密码失败不应注销当前设备");
    }

    const changed = await changeOwnPassword(
      first.token,
      "secret12",
      "changed12",
    );
    tokens.push(changed.token);
    if (changed.token === first.token) {
      throw new Error("改密码成功后应签发新的当前会话");
    }
    if (
      await resolveSession(first.token) ||
      await resolveSession(third.token)
    ) {
      throw new Error("改密码后旧会话仍然有效");
    }
    if (!(await resolveSession(changed.token))) {
      throw new Error("改密码后的新会话无效");
    }
    await assertAuthError(
      () => loginAccount(nickname, "secret12"),
      "INVALID_CREDENTIALS",
    );

    const blocked = await registerAccount(blockedNickname, "secret12");
    accountIds.push(blocked.account.accountId);
    tokens.push(blocked.token);
    const disabled = await setAccountDisabled(
      blocked.account.accountId,
      true,
    );
    if (!disabled.disabled || await resolveSession(blocked.token)) {
      throw new Error("账号禁用没有立即撤销已有会话");
    }
    await assertAuthError(
      () => loginAccount(blockedNickname, "wrong12"),
      "INVALID_CREDENTIALS",
    );
    await assertAuthError(
      () => loginAccount(blockedNickname, "secret12"),
      "ACCOUNT_DISABLED",
    );
    const enabled = await setAccountDisabled(
      blocked.account.accountId,
      false,
    );
    if (enabled.disabled) throw new Error("账号恢复后仍标记为禁用");
    const restored = await loginAccount(blockedNickname, "secret12");
    tokens.push(restored.token);

    await setRegistrationEnabled(false);
    if ((await getRegistrationSettings()).enabled) {
      throw new Error("注册开关没有关闭");
    }
    await assertAuthError(
      () => registerAccount(`关闭${suffix}`, "secret12"),
      "REGISTRATION_DISABLED",
    );
    await setRegistrationEnabled(true);
    if (!(await getRegistrationSettings()).enabled) {
      throw new Error("注册开关没有恢复");
    }
  } finally {
    if (registrationEntry.value === null) {
      await kv.delete(["settings", "registration"]);
    } else {
      await kv.set(["settings", "registration"], registrationEntry.value);
    }
    for (const token of tokens) await kv.delete(["sessions", token]);
    for (const accountId of accountIds) {
      const account = await kv.get<{ nicknameKey: string }>([
        "accounts",
        accountId,
      ]);
      if (account.value) {
        await kv.delete(["accountByNickname", account.value.nicknameKey]);
      }
      await kv.delete(["accounts", accountId]);
    }
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

async function assertAuthError(
  action: () => Promise<unknown>,
  expectedCode: string,
) {
  try {
    await action();
  } catch (error) {
    if (error instanceof AuthError && error.code === expectedCode) return;
    throw error;
  }
  throw new Error(`预期账号错误 ${expectedCode}，但操作成功了`);
}
