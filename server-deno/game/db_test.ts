import { ensureKvParentDirectory } from "./db.ts";

Deno.test("local KV initialization creates a missing parent directory", async () => {
  const root = await Deno.makeTempDir();
  const databasePath = `${root}/nested/storage/game.db`;

  try {
    await ensureKvParentDirectory(databasePath);
    const info = await Deno.stat(`${root}/nested/storage`);
    if (!info.isDirectory) {
      throw new Error("KV parent path was not created as a directory");
    }
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
