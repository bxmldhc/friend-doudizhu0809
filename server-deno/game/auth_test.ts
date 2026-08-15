import {
  AuthError,
  loginAccount,
  logoutSession,
  registerAccount,
  resolveSession,
} from "./auth.ts";
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
