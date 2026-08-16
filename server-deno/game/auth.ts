import { kv } from "./db.ts";

export interface PublicAccount {
  accountId: string;
  nickname: string;
  createdAt: number;
  role: "USER" | "ADMIN";
  isAdmin: boolean;
  disabled: boolean;
  mustChangePassword: boolean;
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
  disabled?: boolean;
  disabledAt?: number | null;
  mustChangePassword?: boolean;
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

export interface RegistrationSettings {
  enabled: boolean;
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
const REGISTRATION_SETTINGS_KEY: Deno.KvKey = ["settings", "registration"];

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
  const registrationEntry = await kv.get<RegistrationSettings>(
    REGISTRATION_SETTINGS_KEY,
  );
  if (registrationEntry.value?.enabled === false) {
    throw new AuthError("新账号注册暂时关闭", "REGISTRATION_DISABLED", 403);
  }
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
    .check(registrationEntry)
    .check(nicknameEntry)
    .set(["accounts", accountId], account)
    .set(["accountByNickname", nicknameKey], accountId)
    .commit();
  if (!result.ok) {
    if (!(await getRegistrationSettings()).enabled) {
      throw new AuthError("新账号注册暂时关闭", "REGISTRATION_DISABLED", 403);
    }
    if (
      (await kv.get<string>(["accountByNickname", nicknameKey])).value
    ) {
      throw new AuthError("该昵称已经被使用", "NICKNAME_TAKEN", 409);
    }
    throw new AuthError(
      "注册设置刚刚发生变化，请重试",
      "REGISTRATION_CHANGED",
      409,
    );
  }
  return await createSession(account);
}

export async function createAccountForAdmin(
  rawNickname: string,
  temporaryPassword: string,
): Promise<PublicAccount> {
  const { nickname, nicknameKey } = validateNickname(rawNickname);
  validatePassword(temporaryPassword);

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
    temporaryPassword,
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
    mustChangePassword: true,
  };

  const result = await kv.atomic()
    .check(nicknameEntry)
    .set(["accounts", accountId], account)
    .set(["accountByNickname", nicknameKey], accountId)
    .commit();
  if (!result.ok) {
    if (
      (await kv.get<string>(["accountByNickname", nicknameKey])).value
    ) {
      throw new AuthError("该昵称已经被使用", "NICKNAME_TAKEN", 409);
    }
    throw new AuthError("账号创建失败，请重试", "ACCOUNT_CREATE_FAILED", 409);
  }
  return toPublicAccount(account);
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
  assertAccountEnabled(account);
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
  if (account.disabled) {
    await kv.delete(key);
    return null;
  }
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
    mustChangePassword: true,
  };
  const result = await kv.atomic().check(entry).set(key, updated).commit();
  if (!result.ok) {
    throw new AuthError("账号刚刚发生变化，请重试", "ACCOUNT_CHANGED", 409);
  }
  await invalidateAccountSessions(normalizedAccountId);
  return toPublicAccount(updated);
}

export async function changeOwnPassword(
  token: string,
  currentPassword: string,
  newPassword: string,
): Promise<AuthSession> {
  validatePassword(currentPassword);
  validatePassword(newPassword);
  const { sessionEntry, accountEntry } = await requireStoredSession(token);
  const account = accountEntry.value!;
  assertAccountEnabled(account);
  await assertPasswordMatches(account, currentPassword);
  if (currentPassword === newPassword) {
    throw new AuthError(
      "新密码不能与当前密码相同",
      "PASSWORD_UNCHANGED",
    );
  }

  const updated = await withNewPassword(account, newPassword);
  updated.sessionVersion = (account.sessionVersion ?? 0) + 1;
  updated.mustChangePassword = false;
  const replacement = newStoredSession(updated);
  const replacementToken = randomToken();
  const result = await kv.atomic()
    .check(accountEntry)
    .check(sessionEntry)
    .set(accountEntry.key, updated)
    .delete(sessionEntry.key)
    .set(["sessions", replacementToken], replacement, {
      expireIn: SESSION_LIFETIME_MS,
    })
    .commit();
  if (!result.ok) {
    throw new AuthError("账号刚刚发生变化，请重试", "ACCOUNT_CHANGED", 409);
  }
  await invalidateAccountSessions(account.accountId, replacementToken);
  return {
    token: replacementToken,
    account: toPublicAccount(updated),
    expiresAt: replacement.expiresAt,
  };
}

export async function logoutOtherSessions(token: string): Promise<AuthSession> {
  const normalizedToken = token.trim();
  const { sessionEntry, accountEntry } = await requireStoredSession(
    normalizedToken,
  );
  const account = accountEntry.value!;
  assertAccountEnabled(account);
  const updated: StoredAccount = {
    ...account,
    sessionVersion: (account.sessionVersion ?? 0) + 1,
  };
  const currentSession = newStoredSession(updated);
  const result = await kv.atomic()
    .check(accountEntry)
    .check(sessionEntry)
    .set(accountEntry.key, updated)
    .set(sessionEntry.key, currentSession, { expireIn: SESSION_LIFETIME_MS })
    .commit();
  if (!result.ok) {
    throw new AuthError("账号刚刚发生变化，请重试", "ACCOUNT_CHANGED", 409);
  }
  await invalidateAccountSessions(account.accountId, normalizedToken);
  return {
    token: normalizedToken,
    account: toPublicAccount(updated),
    expiresAt: currentSession.expiresAt,
  };
}

export async function setAccountDisabled(
  accountId: string,
  disabled: boolean,
): Promise<PublicAccount> {
  const normalizedAccountId = accountId.trim();
  if (!normalizedAccountId) {
    throw new AuthError("账号不存在", "ACCOUNT_NOT_FOUND", 404);
  }
  const key: Deno.KvKey = ["accounts", normalizedAccountId];
  const entry = await kv.get<StoredAccount>(key);
  const account = entry.value;
  if (!account) {
    throw new AuthError("账号不存在", "ACCOUNT_NOT_FOUND", 404);
  }
  if (Boolean(account.disabled) === disabled) {
    return toPublicAccount(account);
  }
  const updated: StoredAccount = {
    ...account,
    disabled,
    disabledAt: disabled ? Date.now() : null,
    sessionVersion: disabled
      ? (account.sessionVersion ?? 0) + 1
      : account.sessionVersion ?? 0,
  };
  const result = await kv.atomic().check(entry).set(key, updated).commit();
  if (!result.ok) {
    throw new AuthError("账号刚刚发生变化，请重试", "ACCOUNT_CHANGED", 409);
  }
  if (disabled) await invalidateAccountSessions(normalizedAccountId);
  return toPublicAccount(updated);
}

export async function getRegistrationSettings(): Promise<RegistrationSettings> {
  const stored = await kv.get<RegistrationSettings>(REGISTRATION_SETTINGS_KEY);
  return { enabled: stored.value?.enabled !== false };
}

export async function setRegistrationEnabled(
  enabled: boolean,
): Promise<RegistrationSettings> {
  const settings = { enabled };
  await kv.set(REGISTRATION_SETTINGS_KEY, settings);
  return settings;
}

export async function logoutSession(token: string) {
  const normalizedToken = token.trim();
  if (normalizedToken) {
    await kv.delete(["sessions", normalizedToken]);
  }
}

async function createSession(account: StoredAccount): Promise<AuthSession> {
  assertAccountEnabled(account);
  const token = randomToken();
  const session = newStoredSession(account);
  await kv.set(
    ["sessions", token],
    session,
    { expireIn: SESSION_LIFETIME_MS },
  );
  return {
    token,
    account: toPublicAccount(account),
    expiresAt: session.expiresAt,
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
    disabled: Boolean(account.disabled),
    mustChangePassword: Boolean(account.mustChangePassword),
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

async function invalidateAccountSessions(
  accountId: string,
  exceptToken = "",
): Promise<void> {
  const keys: Deno.KvKey[] = [];
  for await (
    const entry of kv.list<StoredSession>({ prefix: ["sessions"] })
  ) {
    if (
      entry.value.accountId === accountId &&
      entry.key[1] !== exceptToken
    ) {
      keys.push(entry.key);
    }
  }
  for (let offset = 0; offset < keys.length; offset += 10) {
    let operation = kv.atomic();
    for (const key of keys.slice(offset, offset + 10)) {
      operation = operation.delete(key);
    }
    await operation.commit();
  }
}

async function requireStoredSession(token: string): Promise<{
  sessionEntry: Deno.KvEntry<StoredSession>;
  accountEntry: Deno.KvEntry<StoredAccount>;
}> {
  const normalizedToken = token.trim();
  if (!normalizedToken) {
    throw new AuthError("登录已失效，请重新登录", "AUTH_REQUIRED", 401);
  }
  const sessionEntry = await kv.get<StoredSession>([
    "sessions",
    normalizedToken,
  ]);
  const session = sessionEntry.value;
  if (!session || session.expiresAt <= Date.now()) {
    if (session) await kv.delete(sessionEntry.key);
    throw new AuthError("登录已失效，请重新登录", "AUTH_REQUIRED", 401);
  }
  const accountEntry = await kv.get<StoredAccount>([
    "accounts",
    session.accountId,
  ]);
  const account = accountEntry.value;
  if (
    !account ||
    session.sessionVersion !== (account.sessionVersion ?? 0)
  ) {
    await kv.delete(sessionEntry.key);
    throw new AuthError("登录已失效，请重新登录", "AUTH_REQUIRED", 401);
  }
  return { sessionEntry, accountEntry };
}

function newStoredSession(account: StoredAccount): StoredSession {
  const createdAt = Date.now();
  return {
    accountId: account.accountId,
    sessionVersion: account.sessionVersion ?? 0,
    createdAt,
    expiresAt: createdAt + SESSION_LIFETIME_MS,
  };
}

async function assertPasswordMatches(
  account: StoredAccount,
  password: string,
): Promise<void> {
  const actualHash = await derivePasswordHash(
    password,
    base64ToBytes(account.passwordSalt),
    account.passwordIterations,
  );
  if (!constantTimeEqual(actualHash, base64ToBytes(account.passwordHash))) {
    throw new AuthError("当前密码错误", "CURRENT_PASSWORD_INVALID", 401);
  }
}

async function withNewPassword(
  account: StoredAccount,
  newPassword: string,
): Promise<StoredAccount> {
  const iterations = configuredPasswordIterations();
  const passwordSaltBytes = crypto.getRandomValues(new Uint8Array(16));
  const passwordHashBytes = await derivePasswordHash(
    newPassword,
    passwordSaltBytes,
    iterations,
  );
  return {
    ...account,
    passwordHash: bytesToBase64(passwordHashBytes),
    passwordSalt: bytesToBase64(passwordSaltBytes),
    passwordIterations: iterations,
  };
}

function assertAccountEnabled(account: StoredAccount): void {
  if (account.disabled) {
    throw new AuthError("账号已被禁用", "ACCOUNT_DISABLED", 403);
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
