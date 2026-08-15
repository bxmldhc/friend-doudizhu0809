import { handleUpdateRequest } from "./updates.ts";

const SHA256 = "a".repeat(64);

Deno.test("app update routes publish metadata and APK content", async (t) => {
  const previousDir = Deno.env.get("APP_RELEASES_DIR");
  const releasesDir = await Deno.makeTempDir({ prefix: "friend-ddz-updates-" });
  const apkName = "friend-doudizhu-1.1.0.apk";
  const apkBytes = new TextEncoder().encode("test apk content");

  Deno.env.set("APP_RELEASES_DIR", releasesDir);
  try {
    await Deno.writeFile(`${releasesDir}/${apkName}`, apkBytes);
    await Deno.writeTextFile(
      `${releasesDir}/latest.json`,
      JSON.stringify({
        versionCode: 2,
        versionName: "1.1.0",
        apkFile: apkName,
        sha256: SHA256,
        forceUpdate: false,
        releaseNotes: ["新增自动更新", "", "优化联机体验"],
      }),
    );

    await t.step("latest endpoint uses the request origin", async () => {
      const response = await requireResponse(
        new Request("http://updates.example:18080/api/app/latest"),
      );
      const body = await response.json();

      assertEquals(response.status, 200, "版本接口状态码");
      assertEquals(
        response.headers.get("cache-control"),
        "no-store",
        "缓存策略",
      );
      assertEquals(body.ok, true, "版本接口结果");
      assertEquals(body.data.versionCode, 2, "版本号");
      assertEquals(body.data.versionName, "1.1.0", "版本名称");
      assertEquals(
        body.data.apkUrl,
        `http://updates.example:18080/downloads/${apkName}`,
        "下载地址",
      );
      assertEquals(body.data.sha256, SHA256, "安装包校验值");
      assertEquals(body.data.releaseNotes.length, 2, "更新内容过滤空项");
    });

    await t.step("download endpoint streams the APK", async () => {
      const response = await requireResponse(
        new Request(`http://localhost/downloads/${apkName}`),
      );
      const body = new Uint8Array(await response.arrayBuffer());

      assertEquals(response.status, 200, "下载状态码");
      assertEquals(
        response.headers.get("content-type"),
        "application/vnd.android.package-archive",
        "安装包类型",
      );
      assertEquals(
        response.headers.get("content-length"),
        String(apkBytes.length),
        "安装包大小",
      );
      assertEquals(
        new TextDecoder().decode(body),
        "test apk content",
        "安装包内容",
      );
    });

    await t.step("download endpoint supports HEAD checks", async () => {
      const response = await requireResponse(
        new Request(`http://localhost/downloads/${apkName}`, {
          method: "HEAD",
        }),
      );

      assertEquals(response.status, 200, "HEAD 状态码");
      assertEquals(
        response.headers.get("content-length"),
        String(apkBytes.length),
        "HEAD 安装包大小",
      );
      assertEquals((await response.arrayBuffer()).byteLength, 0, "HEAD 响应体");
    });

    await t.step("invalid download names are rejected", async () => {
      const response = await requireResponse(
        new Request("http://localhost/downloads/%2e%2e%2fsecret.apk"),
      );
      assertEquals(response.status, 404, "非法文件名状态码");
    });

    await t.step("malformed URL encoding is rejected", async () => {
      const response = await requireResponse(
        new Request("http://localhost/downloads/%E0%A4%A.apk"),
      );
      assertEquals(response.status, 404, "错误编码状态码");
    });
  } finally {
    if (previousDir === undefined) {
      Deno.env.delete("APP_RELEASES_DIR");
    } else {
      Deno.env.set("APP_RELEASES_DIR", previousDir);
    }
    await Deno.remove(releasesDir, { recursive: true });
  }
});

Deno.test("latest endpoint reports an unpublished update", async () => {
  const previousDir = Deno.env.get("APP_RELEASES_DIR");
  const releasesDir = await Deno.makeTempDir({ prefix: "friend-ddz-empty-" });
  Deno.env.set("APP_RELEASES_DIR", releasesDir);

  try {
    const response = await requireResponse(
      new Request("http://localhost/api/app/latest"),
    );
    const body = await response.json();

    assertEquals(response.status, 404, "未发布状态码");
    assertEquals(body.ok, false, "未发布结果");
    assertEquals(body.error.code, "UPDATE_NOT_PUBLISHED", "未发布错误码");
  } finally {
    if (previousDir === undefined) {
      Deno.env.delete("APP_RELEASES_DIR");
    } else {
      Deno.env.set("APP_RELEASES_DIR", previousDir);
    }
    await Deno.remove(releasesDir, { recursive: true });
  }
});

async function requireResponse(request: Request): Promise<Response> {
  const response = await handleUpdateRequest(request);
  if (!response) {
    throw new Error(`更新路由没有处理 ${new URL(request.url).pathname}`);
  }
  return response;
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
