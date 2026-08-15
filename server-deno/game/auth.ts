import { kv } from "./db.ts";

export interface PublicAccount {
  accountId: string;
  nickname: string;
  createdAt: number;
  role: "USER" | "ADMIN";
  isAdmin: boolean;
}

interface StoredAccount {
  accountId: string;
  nickname: string;
  createdAt: number;
  nicknameKey: string;
  passwordHash: string;
  passwordSalt: string;
  passwordIterations: number;
  sessionVersion?: number;
}

interface StoredSession {
  accountId: string;
  sessionVersion: number;
  createdAt: number;
  expiresAt: number;
}

export interface AuthSession {
  token: string;
  account: PublicAccount;
  expiresAt: number;
}

export interface AdminAccountList {
  accounts: PublicAccount[];
  total: number;
  limit: number;
  query: string;
}

const RESERVED_NICKNAMES = new Set([
  "ai",
  "系统",
  "等待加入",
  "小智",
  "阿强",
  "老k",
]);
const SESSION_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000;
const DEFAULT_PBKDF2_ITERATIONS = 120_000;

export class AuthError extends Error {
  constructor(
    message: string,
    public code: string,
    public status = 400,
  ) {
    super(message);
  }
}

export function validateNickname(rawNickname: string): {
  nickname: string;
  nicknameKey: string;
} {
  const nickname = rawNickname.trim().normalize("NFKC");
  const length = Array.from(nickname).length;
  if (length < 2 || length > 12) {
    throw new AuthError("昵称长度需要为 2 到 12 个字符", "INVALID_NICKNAME");
  }
  if (/[\u0000-\u001f\u007f]/u.test(nickname)) {
    throw new AuthError("昵称包含不可使用的字符", "INVALID_NICKNAME");
  }
  const nicknameKey = nickname.toLocaleLowerCase("zh-CN");
  if (RESERVED_NICKNAMES.has(nicknameKey)) {
    throw new AuthError("该昵称不可使用", "RESERVED_NICKNAME");
  }
  return { nickname, nicknameKey };
}

export function validatePassword(password: string) {
  const length = Array.from(password).length;
  if (length < 6 || length > 64) {
    throw new AuthError("密码长度需要为 6 到 64 个字符", "INVALID_PASSWORD");
  }
}

export async function registerAccount(
  rawNickname: string,
  password: string,
): Promise<AuthSession> {
  const { nickname, nicknameKey } = validateNickname(rawNickname);
  validatePassword(password);

  const nicknameEntry = await kv.get<string>([
    "accountByNickname",
    nicknameKey,
  ]);
  if (nicknameEntry.value) {
    throw new AuthError("该昵称已经被使用", "NICKNAME_TAKEN", 409);
  }

  const accountId = crypto.randomUUID();
  const createdAt = Date.now();
  const iterations = configuredPasswordIterations();
  const passwordSaltBytes = crypto.getRandomValues(new Uint8Array(16));
  const passwordHashBytes = await derivePasswordHash(
    password,
    passwordSaltBytes,
    iterations,
  );
  const account: StoredAccount = {
    accountId,
    nickname,
    nicknameKey,
    passwordHash: bytesToBase64(passwordHashBytes),
    passwordSalt: bytesToBase64(passwordSaltBytes),
    passwordIterations: iterations,
    sessionVersion: 0,
    createdAt,
  };

  const result = await kv.atomic()
    .check(nicknameEntry)
    .set(["accounts", accountId], account)
    .set(["accountByNickname", nicknameKey], accountId)
    .commit();
  if (!result.ok) {
    throw new AuthError("该昵称已经被使用", "NICKNAME_TAKEN", 409);
  }
  return await createSession(account);
}

export async function loginAccount(
  rawNickname: string,
  password: string,
): Promise<AuthSession> {
  const { nicknameKey } = validateNickname(rawNickname);
  validatePassword(password);
  const accountId = (await kv.get<string>([
    "accountByNickname",
    nicknameKey,
  ])).value;
  if (!accountId) {
    throw invalidCredentials();
  }
  const account = (await kv.get<StoredAccount>(["accounts", accountId])).value;
  if (!account) {
    throw invalidCredentials();
  }
  const actualHash = await derivePasswordHash(
    password,
    base64ToBytes(account.passwordSalt),
    account.passwordIterations,
  );
  if (!constantTimeEqual(actualHash, base64ToBytes(account.passwordHash))) {
    throw invalidCredentials();
  }
  return await createSession(account);
}

export async function resolveSession(
  token: string,
): Promise<PublicAccount | null> {
  const normalizedToken = token.trim();
  if (!normalizedToken) return null;
  const key: Deno.KvKey = ["sessions", normalizedToken];
  const session = (await kv.get<StoredSession>(key)).value;
  if (!session) return null;
  if (session.expiresAt <= Date.now()) {
    await kv.delete(key);
    return null;
  }
  const account = (await kv.get<StoredAccount>([
    "accounts",
    session.accountId,
  ])).value;
  if (!account) return null;
  if (session.sessionVersion !== (account.sessionVersion ?? 0)) {
    await kv.delete(key);
    return null;
  }
  return toPublicAccount(account);
}

export async function getPublicAccount(
  accountId: string,
): Promise<PublicAccount | null> {
  const normalizedAccountId = accountId.trim();
  if (!normalizedAccountId) return null;
  const account = (await kv.get<StoredAccount>([
    "accounts",
    normalizedAccountId,
  ])).value;
  return account ? toPublicAccount(account) : null;
}

export async function listAccountsForAdmin(
  rawQuery = "",
  requestedLimit = 100,
): Promise<AdminAccountList> {
  const query = rawQuery.trim().normalize("NFKC").toLocaleLowerCase("zh-CN");
  const limit = Math.max(
    1,
    Math.min(
      100,
      Number.isFinite(requestedLimit) ? Math.floor(requestedLimit) : 100,
    ),
  );
  const matches: PublicAccount[] = [];
  let total = 0;

  for await (const entry of kv.list<StoredAccount>({ prefix: ["accounts"] })) {
    const account = entry.value;
    if (
      query &&
      !account.nicknameKey.includes(query) &&
      !account.accountId.toLocaleLowerCase().includes(query)
    ) {
      continue;
    }
    total += 1;
    matches.push(toPublicAccount(account));
  }

  matches.sort((left, right) =>
    right.createdAt - left.createdAt ||
    left.nickname.localeCompare(right.nickname, "zh-CN")
  );
  return {
    accounts: matches.slice(0, limit),
    total,
    limit,
    query: rawQuery.trim(),
  };
}

export async function countAccounts(): Promise<number> {
  let total = 0;
  for await (const _entry of kv.list({ prefix: ["accounts"] })) {
    total += 1;
  }
  return total;
}

export async function resetAccountPassword(
  accountId: string,
  newPassword: string,
): Promise<PublicAccount> {
  const normalizedAccountId = accountId.trim();
  if (!normalizedAccountId) {
    throw new AuthError("账号不存在", "ACCOUNT_NOT_FOUND", 404);
  }
  validatePassword(newPassword);

  const key: Deno.KvKey = ["accounts", normalizedAccountId];
  const entry = await kv.get<StoredAccount>(key);
  const account = entry.value;
  if (!account) {
    throw new AuthError("账号不存在", "ACCOUNT_NOT_FOUND", 404);
  }

  const iterations = configuredPasswordIterations();
  const passwordSaltBytes = crypto.getRandomValues(new Uint8Array(16));
  const passwordHashBytes = await derivePasswordHash(
    newPassword,
    passwordSaltBytes,
    iterations,
  );
  const updated: StoredAccount = {
    ...account,
    passwordHash: bytesToBase64(passwordHashBytes),
    passwordSalt: bytesToBase64(passwordSaltBytes),
    passwordIterations: iterations,
    sessionVersion: (account.sessionVersion ?? 0) + 1,
  };
  const result = await kv.atomic().check(entry).set(key, updated).commit();
  if (!result.ok) {
    throw new AuthError("账号刚刚发生变化，请重试", "ACCOUNT_CHANGED", 409);
  }
  await invalidateAccountSessions(normalizedAccountId);
  return toPublicAccount(updated);
}

export async function logoutSession(token: string) {
  const normalizedToken = token.trim();
  if (normalizedToken) {
    await kv.delete(["sessions", normalizedToken]);
  }
}

async function createSession(account: StoredAccount): Promise<AuthSession> {
  const token = randomToken();
  const expiresAt = Date.now() + SESSION_LIFETIME_MS;
  await kv.set(
    ["sessions", token],
    {
      accountId: account.accountId,
      sessionVersion: account.sessionVersion ?? 0,
      createdAt: Date.now(),
      expiresAt,
    } satisfies StoredSession,
    { expireIn: SESSION_LIFETIME_MS },
  );
  return {
    token,
    account: toPublicAccount(account),
    expiresAt,
  };
}

function toPublicAccount(account: StoredAccount): PublicAccount {
  const isAdmin = isAdminAccount(account);
  return {
    accountId: account.accountId,
    nickname: account.nickname,
    createdAt: account.createdAt,
    role: isAdmin ? "ADMIN" : "USER",
    isAdmin,
  };
}

export function isAdminAccount(
  account: Pick<StoredAccount, "accountId" | "nicknameKey">,
): boolean {
  const accountIds = configuredAdminValues("ADMIN_ACCOUNT_IDS");
  if (accountIds.has(account.accountId.toLocaleLowerCase())) return true;
  const nicknames = configuredAdminValues("ADMIN_NICKNAMES");
  return nicknames.has(account.nicknameKey);
}

async function invalidateAccountSessions(accountId: string): Promise<void> {
  const keys: Deno.KvKey[] = [];
  for await (
    const entry of kv.list<StoredSession>({ prefix: ["sessions"] })
  ) {
    if (entry.value.accountId === accountId) keys.push(entry.key);
  }
  for (let offset = 0; offset < keys.length; offset += 10) {
    let operation = kv.atomic();
    for (const key of keys.slice(offset, offset + 10)) {
      operation = operation.delete(key);
    }
    await operation.commit();
  }
}

function configuredAdminValues(name: string): Set<string> {
  return new Set(
    (Deno.env.get(name) ?? "")
      .split(",")
      .map((value) => value.trim().normalize("NFKC").toLocaleLowerCase("zh-CN"))
      .filter(Boolean),
  );
}

function configuredPasswordIterations(): number {
  const configured = Number(Deno.env.get("AUTH_PBKDF2_ITERATIONS"));
  return Number.isFinite(configured) && configured >= 10_000
    ? Math.floor(configured)
    : DEFAULT_PBKDF2_ITERATIONS;
}

async function derivePasswordHash(
  password: string,
  salt: Uint8Array,
  iterations: number,
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: Uint8Array.from(salt),
      iterations,
    },
    key,
    256,
  );
  return new Uint8Array(bits);
}

function randomToken(): string {
  return bytesToBase64(crypto.getRandomValues(new Uint8Array(32)))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function constantTimeEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index++) {
    difference |= left[index] ^ right[index];
  }
  return difference === 0;
}

function invalidCredentials(): AuthError {
  return new AuthError("昵称或密码错误", "INVALID_CREDENTIALS", 401);
}
