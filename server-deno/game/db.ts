// Deno Deploy leaves this unset and provides its managed KV automatically.
// A self-hosted server sets it to a persistent local database path.
const kvPath = Deno.env.get("DENO_KV_PATH")?.trim();
const isDenoDeploy = Boolean(Deno.env.get("DENO_DEPLOYMENT_ID"));

export const kv = await Deno.openKv(
  kvPath || (isDenoDeploy ? undefined : "./data/local.db"),
);
