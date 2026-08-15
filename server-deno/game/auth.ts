import { kv } from "./db.ts";

export interface PublicAccount {
  accountId: string;
  nickname: string;
  createdAt: number;
}

interface StoredAccount extends PublicAccount {
  nicknameKey: string;
  passwordHash: string;
  passwordSalt: string;
  passwordIterations: number;
}

interface StoredSession {
  accountId: string;
  createdAt: number;
  expiresAt: number;
}

export interface AuthSession {
  token: string;
  account: PublicAccount;
  expiresAt: number;
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
  return account ? toPublicAccount(account) : null;
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
  return {
    accountId: account.accountId,
    nickname: account.nickname,
    createdAt: account.createdAt,
  };
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
