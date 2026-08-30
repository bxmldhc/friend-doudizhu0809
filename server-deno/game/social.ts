import { AuthError, getPublicAccount, type PublicAccount } from "./auth.ts";
import { kv } from "./db.ts";

// 客户端每 10 秒发送一次心跳；保留约 3 次心跳的容错窗口。
export const APP_HEARTBEAT_INTERVAL_MS = 10_000;
export const APP_ONLINE_WINDOW_MS = 35_000;
const MAX_FRIENDS = 200;

export interface AppPresence {
  accountId: string;
  nickname: string;
  lastSeenAt: number;
}

interface StoredFriendRequest {
  requestId: string;
  fromAccountId: string;
  toAccountId: string;
  createdAt: number;
  updatedAt: number;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
}

interface StoredFriend {
  friendAccountId: string;
  createdAt: number;
}

export interface OnlineAccountSnapshot {
  accountId: string;
  nickname: string;
  lastSeenAt: number;
}

export interface FriendProfile {
  accountId: string;
  nickname: string;
  createdAt: number;
  online: boolean;
  lastSeenAt: number | null;
}

export interface FriendRequestProfile {
  requestId: string;
  account: FriendProfile;
  direction: "INCOMING" | "OUTGOING";
  createdAt: number;
}

export async function touchPresence(
  account: PublicAccount,
  sessionId = "",
): Promise<AppPresence> {
  const presence: AppPresence = {
    accountId: account.accountId,
    nickname: account.nickname,
    lastSeenAt: Date.now(),
  };
  await kv.set(presenceKey(account.accountId, sessionId), presence, {
    expireIn: APP_ONLINE_WINDOW_MS * 2,
  });
  return presence;
}

export async function clearPresence(
  accountId: string,
  sessionId = "",
): Promise<void> {
  const normalizedAccountId = accountId.trim();
  if (!normalizedAccountId) return;
  if (sessionId.trim()) {
    await kv.delete(presenceKey(normalizedAccountId, sessionId));
    return;
  }
  const keys: Deno.KvKey[] = [];
  for await (
    const entry of kv.list<AppPresence>({
      prefix: ["presence", normalizedAccountId],
    })
  ) {
    keys.push(entry.key);
  }
  for (const key of keys) await kv.delete(key);
}

export async function getPresence(
  accountId: string,
): Promise<AppPresence | null> {
  const normalizedAccountId = accountId.trim();
  if (!normalizedAccountId) return null;
  const now = Date.now();
  let latest: AppPresence | null = null;
  const staleKeys: Deno.KvKey[] = [];
  for await (
    const entry of kv.list<AppPresence>({
      prefix: ["presence", normalizedAccountId],
    })
  ) {
    if (entry.value.lastSeenAt + APP_ONLINE_WINDOW_MS <= now) {
      staleKeys.push(entry.key);
      continue;
    }
    if (!latest || entry.value.lastSeenAt > latest.lastSeenAt) {
      latest = entry.value;
    }
  }
  for (const key of staleKeys) await kv.delete(key);
  return latest;
}

export async function listAppOnlineAccounts(): Promise<
  OnlineAccountSnapshot[]
> {
  const now = Date.now();
  const accounts = new Map<string, OnlineAccountSnapshot>();
  const staleKeys: Deno.KvKey[] = [];
  for await (const entry of kv.list<AppPresence>({ prefix: ["presence"] })) {
    const presence = entry.value;
    if (presence.lastSeenAt + APP_ONLINE_WINDOW_MS <= now) {
      staleKeys.push(entry.key);
      continue;
    }
    const account: OnlineAccountSnapshot = {
      accountId: presence.accountId,
      nickname: presence.nickname,
      lastSeenAt: presence.lastSeenAt,
    };
    const existing = accounts.get(account.accountId);
    if (!existing || account.lastSeenAt > existing.lastSeenAt) {
      accounts.set(account.accountId, account);
    }
  }
  for (const key of staleKeys) await kv.delete(key);
  return [...accounts.values()].sort((left, right) =>
    right.lastSeenAt - left.lastSeenAt
  );
}

export async function listFriends(accountId: string): Promise<FriendProfile[]> {
  const records: StoredFriend[] = [];
  for await (
    const entry of kv.list<StoredFriend>({
      prefix: ["friendsByAccount", accountId],
    })
  ) {
    records.push(entry.value);
  }
  const friends = await Promise.all(records.map(async (record) => {
    const account = await getPublicAccount(record.friendAccountId);
    if (!account || account.disabled) return null;
    return toFriendProfile(account, record.createdAt);
  }));
  return friends.filter((friend): friend is FriendProfile => friend !== null)
    .sort((left, right) =>
      left.nickname.localeCompare(right.nickname, "zh-CN")
    );
}

export async function listFriendRequests(
  accountId: string,
): Promise<FriendRequestProfile[]> {
  const requests: FriendRequestProfile[] = [];
  for await (
    const entry of kv.list<StoredFriendRequest>({ prefix: ["friendRequests"] })
  ) {
    const request = entry.value;
    if (request.status !== "PENDING") continue;
    const direction = request.toAccountId === accountId
      ? "INCOMING"
      : request.fromAccountId === accountId
      ? "OUTGOING"
      : null;
    if (!direction) continue;
    const otherId = direction === "INCOMING"
      ? request.fromAccountId
      : request.toAccountId;
    const account = await getPublicAccount(otherId);
    if (!account || account.disabled) continue;
    requests.push({
      requestId: request.requestId,
      account: await toFriendProfile(account, request.createdAt),
      direction,
      createdAt: request.createdAt,
    });
  }
  return requests.sort((left, right) => right.createdAt - left.createdAt);
}

export async function sendFriendRequest(
  fromAccountId: string,
  targetAccountId: string,
): Promise<FriendRequestProfile> {
  const targetId = targetAccountId.trim();
  if (!targetId || targetId === fromAccountId) {
    throw new AuthError("不能添加自己为好友", "INVALID_FRIEND_TARGET");
  }
  const target = await getPublicAccount(targetId);
  if (!target || target.disabled) {
    throw new AuthError("未找到可添加的账号", "FRIEND_NOT_FOUND", 404);
  }
  const pairKey = friendPairKey(fromAccountId, targetId);
  const pairEntry = await kv.get<string>(pairKey);
  if (pairEntry.value) {
    const existing = await kv.get<StoredFriendRequest>([
      "friendRequests",
      pairEntry.value,
    ]);
    if (existing.value?.status === "ACCEPTED") {
      throw new AuthError("你们已经是好友了", "ALREADY_FRIENDS", 409);
    }
    if (existing.value?.status === "PENDING") {
      throw new AuthError(
        "好友申请已发送，等待对方处理",
        "FRIEND_REQUEST_EXISTS",
        409,
      );
    }
  }

  const now = Date.now();
  const request: StoredFriendRequest = {
    requestId: crypto.randomUUID(),
    fromAccountId,
    toAccountId: targetId,
    createdAt: now,
    updatedAt: now,
    status: "PENDING",
  };
  const result = await kv.atomic()
    .check(pairEntry)
    .set(["friendRequests", request.requestId], request)
    .set(pairKey, request.requestId)
    .commit();
  if (!result.ok) {
    throw new AuthError(
      "好友申请状态刚刚变化，请重试",
      "FRIEND_REQUEST_CHANGED",
      409,
    );
  }
  return {
    requestId: request.requestId,
    account: await toFriendProfile(target, now),
    direction: "OUTGOING",
    createdAt: now,
  };
}

export async function acceptFriendRequest(
  accountId: string,
  requestId: string,
): Promise<FriendProfile> {
  const key: Deno.KvKey = ["friendRequests", requestId.trim()];
  const entry = await kv.get<StoredFriendRequest>(key);
  const request = entry.value;
  if (!request || request.toAccountId !== accountId) {
    throw new AuthError("好友申请不存在", "FRIEND_REQUEST_NOT_FOUND", 404);
  }
  if (request.status !== "PENDING") {
    throw new AuthError("该好友申请已经处理", "FRIEND_REQUEST_RESOLVED", 409);
  }
  const account = await getPublicAccount(request.fromAccountId);
  if (!account || account.disabled) {
    throw new AuthError("对方账号不可用", "FRIEND_NOT_FOUND", 404);
  }
  const [ownFriendCount, otherFriendCount] = await Promise.all([
    countFriends(accountId),
    countFriends(request.fromAccountId),
  ]);
  if (ownFriendCount >= MAX_FRIENDS || otherFriendCount >= MAX_FRIENDS) {
    throw new AuthError(
      `好友数量不能超过 ${MAX_FRIENDS} 人`,
      "FRIEND_LIMIT_REACHED",
      409,
    );
  }
  const now = Date.now();
  const updated = { ...request, status: "ACCEPTED" as const, updatedAt: now };
  const result = await kv.atomic()
    .check(entry)
    .set(key, updated)
    .set(
      ["friendsByAccount", accountId, request.fromAccountId],
      {
        friendAccountId: request.fromAccountId,
        createdAt: now,
      } satisfies StoredFriend,
    )
    .set(
      ["friendsByAccount", request.fromAccountId, accountId],
      {
        friendAccountId: accountId,
        createdAt: now,
      } satisfies StoredFriend,
    )
    .commit();
  if (!result.ok) {
    throw new AuthError(
      "好友申请状态刚刚变化，请重试",
      "FRIEND_REQUEST_CHANGED",
      409,
    );
  }
  return toFriendProfile(account, now);
}

export async function rejectFriendRequest(
  accountId: string,
  requestId: string,
): Promise<void> {
  const key: Deno.KvKey = ["friendRequests", requestId.trim()];
  const entry = await kv.get<StoredFriendRequest>(key);
  const request = entry.value;
  if (!request || request.toAccountId !== accountId) {
    throw new AuthError("好友申请不存在", "FRIEND_REQUEST_NOT_FOUND", 404);
  }
  if (request.status !== "PENDING") {
    throw new AuthError("该好友申请已经处理", "FRIEND_REQUEST_RESOLVED", 409);
  }
  const updated = {
    ...request,
    status: "REJECTED" as const,
    updatedAt: Date.now(),
  };
  const result = await kv.atomic()
    .check(entry)
    .set(key, updated)
    .delete(friendPairKey(request.fromAccountId, request.toAccountId))
    .commit();
  if (!result.ok) {
    throw new AuthError(
      "好友申请状态刚刚变化，请重试",
      "FRIEND_REQUEST_CHANGED",
      409,
    );
  }
}

export async function removeFriend(
  accountId: string,
  friendAccountId: string,
): Promise<void> {
  const friendId = friendAccountId.trim();
  if (!friendId || friendId === accountId) {
    throw new AuthError("好友不存在", "FRIEND_NOT_FOUND", 404);
  }
  const ownEntry = await kv.get<StoredFriend>([
    "friendsByAccount",
    accountId,
    friendId,
  ]);
  if (!ownEntry.value) {
    throw new AuthError("好友不存在", "FRIEND_NOT_FOUND", 404);
  }
  await kv.atomic()
    .delete(ownEntry.key)
    .delete(["friendsByAccount", friendId, accountId])
    .delete(friendPairKey(accountId, friendId))
    .commit();
}

async function toFriendProfile(
  account: PublicAccount,
  createdAt: number,
): Promise<FriendProfile> {
  const presence = await getPresence(account.accountId);
  return {
    accountId: account.accountId,
    nickname: account.nickname,
    createdAt,
    online: presence !== null,
    lastSeenAt: presence?.lastSeenAt ?? null,
  };
}

function friendPairKey(left: string, right: string): Deno.KvKey {
  const [first, second] = [left, right].sort();
  return ["friendRequestByPair", first, second];
}

async function countFriends(accountId: string): Promise<number> {
  let count = 0;
  for await (
    const _entry of kv.list<StoredFriend>({
      prefix: ["friendsByAccount", accountId],
    })
  ) {
    count += 1;
  }
  return count;
}

function presenceKey(accountId: string, sessionId: string): Deno.KvKey {
  const normalizedSessionId = sessionId.trim();
  return normalizedSessionId
    ? ["presence", accountId, normalizedSessionId]
    : ["presence", accountId];
}
