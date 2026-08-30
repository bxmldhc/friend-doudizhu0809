import { handleApiRequest, type RoomInvitationReader } from "./api.ts";
import { clearPresence, touchPresence } from "./game/social.ts";
import { kv } from "./game/db.ts";
import { getPublicAccount } from "./game/auth.ts";

interface ApiResponse {
  ok: boolean;
  data?: Record<string, unknown>;
  error?: { code?: string };
}

Deno.test("房间邀请只对在线好友生效，并可接受或忽略", async (t) => {
  const previousIterations = Deno.env.get("AUTH_PBKDF2_ITERATIONS");
  Deno.env.set("AUTH_PBKDF2_ITERATIONS", "10000");

  const suffix = crypto.randomUUID().replaceAll("-", "").slice(0, 8);
  const inviterNickname = `邀请甲${suffix}`;
  const targetNickname = `邀请乙${suffix}`;
  const password = "InvitationTest!123";
  const cleanup: Array<{ accountId: string; nickname: string; token: string }> =
    [];
  let friendRequestId = "";
  const invitationIds: string[] = [];
  const dependencies = {
    roomInvitationReader: fakeRoomInvitationReader(),
  };

  try {
    const inviter = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: inviterNickname, password },
      dependencies,
    });
    const inviterSession = readSession(inviter.body);
    cleanup.push({ ...inviterSession, nickname: inviterNickname });

    const target = await requestApi("/api/auth/register", {
      method: "POST",
      body: { nickname: targetNickname, password },
      dependencies,
    });
    const targetSession = readSession(target.body);
    cleanup.push({ ...targetSession, nickname: targetNickname });

    await t.step("邀请前需要先成为好友", async () => {
      const sent = await requestApi("/api/friends/requests", {
        method: "POST",
        token: inviterSession.token,
        body: { targetAccountId: targetSession.accountId },
        dependencies,
      });
      assertEquals(sent.response.status, 201, "发送好友申请状态码");
      friendRequestId = asString(
        asRecord(sent.body.data?.request, "好友申请").requestId,
        "好友申请 ID",
      );
      const accepted = await requestApi(
        `/api/friends/requests/${encodeURIComponent(friendRequestId)}/accept`,
        { method: "POST", token: targetSession.token, dependencies },
      );
      assertEquals(accepted.response.status, 200, "接受好友申请状态码");
    });

    await t.step("创建、读取并接受房间邀请", async () => {
      const created = await requestApi("/api/rooms/123456/invitations", {
        method: "POST",
        token: inviterSession.token,
        body: { targetAccountId: targetSession.accountId },
        dependencies,
      });
      assertEquals(created.response.status, 201, "创建邀请状态码");
      const invitation = asRecord(created.body.data?.invitation, "房间邀请");
      const invitationId = asString(invitation.invitationId, "邀请 ID");
      invitationIds.push(invitationId);
      assertEquals(invitation.gameType, "DOUDIZHU", "邀请游戏类型");

      const duplicate = await requestApi("/api/rooms/123456/invitations", {
        method: "POST",
        token: inviterSession.token,
        body: { targetAccountId: targetSession.accountId },
        dependencies,
      });
      assertEquals(duplicate.response.status, 409, "重复邀请状态码");
      assertEquals(
        duplicate.body.error?.code,
        "ROOM_INVITATION_EXISTS",
        "重复邀请错误码",
      );

      const listed = await requestApi("/api/invitations", {
        token: targetSession.token,
        dependencies,
      });
      assertEquals(listed.response.status, 200, "邀请列表状态码");
      const invitations = asArray(listed.body.data?.invitations, "邀请列表");
      assertEquals(invitations.length, 1, "待处理邀请数量");

      const accepted = await requestApi(
        `/api/invitations/${encodeURIComponent(invitationId)}/accept`,
        { method: "POST", token: targetSession.token, dependencies },
      );
      assertEquals(accepted.response.status, 200, "接受邀请状态码");
      assertEquals(accepted.body.data?.roomCode, "123456", "接受后的房间号");
      assertEquals(accepted.body.data?.gameType, "DOUDIZHU", "接受后的游戏类型");

      const afterAccept = await requestApi("/api/invitations", {
        token: targetSession.token,
        dependencies,
      });
      assertEquals(
        asArray(afterAccept.body.data?.invitations, "接受后邀请列表").length,
        0,
        "接受后不应保留待处理邀请",
      );
    });

    await t.step("接收者可以忽略邀请", async () => {
      const created = await requestApi("/api/rooms/654321/invitations", {
        method: "POST",
        token: inviterSession.token,
        body: { targetAccountId: targetSession.accountId },
        dependencies,
      });
      const invitationId = asString(
        asRecord(created.body.data?.invitation, "房间邀请").invitationId,
        "邀请 ID",
      );
      invitationIds.push(invitationId);

      const dismissed = await requestApi(
        `/api/invitations/${encodeURIComponent(invitationId)}/dismiss`,
        { method: "POST", token: targetSession.token, dependencies },
      );
      assertEquals(dismissed.response.status, 200, "忽略邀请状态码");
      const afterDismiss = await requestApi("/api/invitations", {
        token: targetSession.token,
        dependencies,
      });
      assertEquals(
        asArray(afterDismiss.body.data?.invitations, "忽略后邀请列表").length,
        0,
        "忽略后不应保留待处理邀请",
      );
    });

    await t.step("离线好友和未接入房间查询都会被拒绝", async () => {
      await clearPresence(targetSession.accountId);
      const offline = await requestApi("/api/rooms/123456/invitations", {
        method: "POST",
        token: inviterSession.token,
        body: { targetAccountId: targetSession.accountId },
        dependencies,
      });
      assertEquals(offline.response.status, 409, "离线好友邀请状态码");
      assertEquals(
        offline.body.error?.code,
        "INVITATION_TARGET_OFFLINE",
        "离线好友邀请错误码",
      );

      const targetAccount = await getPublicAccount(targetSession.accountId);
      if (!targetAccount) throw new Error("测试账号不存在");
      await touchPresence(targetAccount, targetSession.token);
      const unavailable = await requestApi(
        "/api/rooms/123456/invitations",
        {
          method: "POST",
          token: inviterSession.token,
          body: { targetAccountId: targetSession.accountId },
          dependencies: {},
        },
      );
      assertEquals(unavailable.response.status, 503, "未接入房间查询状态码");
      assertEquals(
        unavailable.body.error?.code,
        "ROOM_INVITATIONS_UNAVAILABLE",
        "未接入房间查询错误码",
      );
    });
  } finally {
    for (const invitationId of invitationIds) {
      await kv.delete(["roomInvitations", invitationId]);
    }
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
    if (friendRequestId) await kv.delete(["friendRequests", friendRequestId]);
    if (cleanup.length === 2) {
      const [first, second] = cleanup.map((account) => account.accountId).sort();
      await kv.delete(["friendRequestByPair", first, second]);
      await kv.delete(["friendsByAccount", first, second]);
      await kv.delete(["friendsByAccount", second, first]);
      for (const roomCode of ["123456", "654321"]) {
        await kv.delete(["roomInvitationByRoomTarget", roomCode, second]);
      }
      for (const invitationId of invitationIds) {
        await kv.delete(["roomInvitationByTarget", second, invitationId]);
      }
    }
    if (previousIterations === undefined) {
      Deno.env.delete("AUTH_PBKDF2_ITERATIONS");
    } else {
      Deno.env.set("AUTH_PBKDF2_ITERATIONS", previousIterations);
    }
  }
});

function fakeRoomInvitationReader(): RoomInvitationReader {
  return {
    getRoomInvitationEligibility({ roomCode }) {
      if (roomCode === "123456") {
        return { ok: true, gameType: "DOUDIZHU" };
      }
      if (roomCode === "654321") {
        return { ok: true, gameType: "GOMOKU" };
      }
      return { ok: false, code: "ROOM_NOT_FOUND", message: "房间不存在" };
    },
  };
}

async function requestApi(
  path: string,
  options: {
    method?: string;
    body?: Record<string, unknown>;
    token?: string;
    dependencies: { roomInvitationReader?: RoomInvitationReader };
  },
): Promise<{ response: Response; body: ApiResponse }> {
  const headers = new Headers();
  if (options.body) headers.set("content-type", "application/json");
  if (options.token) headers.set("authorization", `Bearer ${options.token}`);
  const response = await handleApiRequest(
    new Request(`http://localhost${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
    }),
    options.dependencies,
  );
  if (!response) throw new Error(`路由没有处理 ${path}`);
  return { response, body: await response.json() as ApiResponse };
}

function readSession(body: ApiResponse): { accountId: string; token: string } {
  const data = asRecord(body.data, "认证响应");
  return {
    accountId: asString(asRecord(data.account, "账号").accountId, "accountId"),
    token: asString(data.token, "token"),
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
