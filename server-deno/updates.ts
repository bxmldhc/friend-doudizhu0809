export interface PublishedUpdate {
  versionCode: number;
  versionName: string;
  apkFile: string;
  sha256: string;
  forceUpdate: boolean;
  releaseNotes: string[];
}

const APK_FILE_PATTERN = /^[A-Za-z0-9._-]+\.apk$/;

function releasesDir(): string {
  return Deno.env.get("APP_RELEASES_DIR") ?? "./releases";
}

export async function handleUpdateRequest(
  req: Request,
): Promise<Response | null> {
  const url = new URL(req.url);
  if (req.method === "GET" && url.pathname === "/api/app/latest") {
    try {
      const update = await loadPublishedUpdate();
      const apkUrl = new URL(
        `/downloads/${encodeURIComponent(update.apkFile)}`,
        url.origin,
      ).toString();
      return Response.json(
        {
          ok: true,
          data: {
            versionCode: update.versionCode,
            versionName: update.versionName,
            apkUrl,
            sha256: update.sha256,
            forceUpdate: update.forceUpdate,
            releaseNotes: update.releaseNotes,
          },
        },
        {
          headers: {
            "cache-control": "no-store",
            "access-control-allow-origin": "*",
          },
        },
      );
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) {
        return Response.json(
          {
            ok: false,
            error: {
              code: "UPDATE_NOT_PUBLISHED",
              message: "当前没有已发布的安装包",
            },
          },
          { status: 404, headers: { "cache-control": "no-store" } },
        );
      }
      console.error("Update manifest failed", error);
      return Response.json(
        {
          ok: false,
          error: {
            code: "UPDATE_MANIFEST_INVALID",
            message: "版本信息暂时不可用",
          },
        },
        { status: 500, headers: { "cache-control": "no-store" } },
      );
    }
  }

  if (
    (req.method === "GET" || req.method === "HEAD") &&
    url.pathname.startsWith("/downloads/")
  ) {
    const encodedName = url.pathname.slice("/downloads/".length);
    const fileName = decodeFileName(encodedName);
    if (!fileName) {
      return new Response("Not found", { status: 404 });
    }
    if (!APK_FILE_PATTERN.test(fileName)) {
      return new Response("Not found", { status: 404 });
    }
    try {
      const filePath = `${releasesDir()}/${fileName}`;
      const stat = await Deno.stat(filePath);
      const headers = {
        "content-type": "application/vnd.android.package-archive",
        "content-length": String(stat.size),
        "content-disposition": `attachment; filename="${fileName}"`,
        "cache-control": "public, max-age=86400, immutable",
      };
      if (req.method === "HEAD") {
        return new Response(null, { headers });
      }
      const file = await Deno.open(filePath, { read: true });
      return new Response(file.readable, { headers });
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) {
        return new Response("Not found", { status: 404 });
      }
      throw error;
    }
  }
  return null;
}

function decodeFileName(encodedName: string): string | null {
  try {
    return decodeURIComponent(encodedName);
  } catch {
    return null;
  }
}

export async function loadPublishedUpdate(): Promise<PublishedUpdate> {
  const parsed = JSON.parse(
    await Deno.readTextFile(`${releasesDir()}/latest.json`),
  ) as Record<string, unknown>;
  const update: PublishedUpdate = {
    versionCode: Number(parsed.versionCode),
    versionName: String(parsed.versionName ?? "").trim(),
    apkFile: String(parsed.apkFile ?? "").trim(),
    sha256: String(parsed.sha256 ?? "").trim().toLowerCase(),
    forceUpdate: Boolean(parsed.forceUpdate),
    releaseNotes: Array.isArray(parsed.releaseNotes)
      ? parsed.releaseNotes.map(String).map((item) => item.trim()).filter(
        Boolean,
      )
      : [],
  };
  if (
    !Number.isInteger(update.versionCode) ||
    update.versionCode < 1 ||
    !update.versionName ||
    !APK_FILE_PATTERN.test(update.apkFile) ||
    !/^[a-f0-9]{64}$/.test(update.sha256)
  ) {
    throw new Error("Invalid update manifest");
  }
  return update;
}
