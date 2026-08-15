// Deno Deploy leaves this unset and provides its managed KV automatically.
// A self-hosted server sets it to a persistent local database path.
const kvPath = Deno.env.get("DENO_KV_PATH")?.trim();
const isDenoDeploy = Boolean(Deno.env.get("DENO_DEPLOYMENT_ID"));
const localKvPath = kvPath || (isDenoDeploy ? undefined : "./data/local.db");

if (localKvPath) {
  await ensureKvParentDirectory(localKvPath);
}

export const kv = await Deno.openKv(localKvPath);

export async function ensureKvParentDirectory(path: string): Promise<void> {
  const normalized = path.replaceAll("\\", "/");
  const separatorIndex = normalized.lastIndexOf("/");
  if (separatorIndex <= 0) return;

  await Deno.mkdir(normalized.slice(0, separatorIndex), { recursive: true });
}
