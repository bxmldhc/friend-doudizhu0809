import { handleInviteRequest } from "./invite.ts";

Deno.test("invite page opens the app and links the latest APK", async (t) => {
  const previousDir = Deno.env.get("APP_RELEASES_DIR");
  const releasesDir = await Deno.makeTempDir({ prefix: "friend-ddz-invite-" });
  Deno.env.set("APP_RELEASES_DIR", releasesDir);

  try {
    await Deno.writeTextFile(
      `${releasesDir}/latest.json`,
      JSON.stringify({
        versionCode: 3,
        versionName: "1.2.0",
        apkFile: "wangyouge-1.2.0.apk",
        sha256: "a".repeat(64),
        forceUpdate: false,
        releaseNotes: [],
      }),
    );

    await t.step("valid room renders Android open choices", async () => {
      const response = await requireResponse(
        new Request("http://games.example:18080/invite/123456"),
      );
      const html = await response.text();

      assertEquals(response.status, 200, "邀请页状态码");
      assertIncludes(
        response.headers.get("content-type") ?? "",
        "text/html",
        "内容类型",
      );
      assertIncludes(html, "忘忧阁", "页面标题");
      assertIncludes(html, "123456", "房号");
      assertIncludes(
        html,
        "intent://join?room=123456#Intent;scheme=wangyouge;package=com.loves.wangyouge",
        "Android intent 地址",
      );
      assertIncludes(
        html,
        "action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE",
        "Android intent 行为",
      );
      assertNotIncludes(
        html,
        "browser_fallback_url",
        "intent 不应提前刷新页面并打断自定义 scheme 回退",
      );
      assertIncludes(html, "wangyouge://join?room=123456", "备用应用地址");
      assertIncludes(
        html,
        "window.location.href = intentUrl",
        "点击后尝试 Android intent",
      );
      assertIncludes(
        html,
        "window.location.href = appUrl",
        "intent 未唤起时尝试自定义 scheme",
      );
      assertIncludes(
        html,
        'document.addEventListener("visibilitychange"',
        "应用打开后取消备用跳转",
      );
      assertIncludes(
        html,
        "http://games.example:18080/downloads/wangyouge-1.2.0.apk",
        "最新安装包地址",
      );
      assertIncludes(html, "正在打开应用", "打开状态提示");
      assertNotIncludes(html, "开发", "内部开发说明");
    });

    await t.step("HEAD returns headers without a body", async () => {
      const response = await requireResponse(
        new Request("http://localhost/invite/123456", { method: "HEAD" }),
      );
      assertEquals(response.status, 200, "HEAD 状态码");
      assertEquals((await response.arrayBuffer()).byteLength, 0, "HEAD 响应体");
    });

    await t.step("invalid room codes return 404", async () => {
      for (
        const path of [
          "/invite/12345",
          "/invite/1234567",
          "/invite/12ab56",
          "/invite/%E0%A4%A",
        ]
      ) {
        const response = await requireResponse(
          new Request(`http://localhost${path}`),
        );
        assertEquals(response.status, 404, `非法房号 ${path}`);
      }
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

Deno.test("invite page keeps a useful download fallback", async () => {
  const previousDir = Deno.env.get("APP_RELEASES_DIR");
  const releasesDir = await Deno.makeTempDir({
    prefix: "friend-ddz-invite-empty-",
  });
  Deno.env.set("APP_RELEASES_DIR", releasesDir);

  try {
    const response = await requireResponse(
      new Request("http://localhost:18080/invite/654321"),
    );
    const html = await response.text();
    assertEquals(response.status, 200, "无发布清单时邀请页状态码");
    assertIncludes(
      html,
      "http://localhost:18080/api/app/latest",
      "版本接口兜底入口",
    );
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
  const response = await handleInviteRequest(request);
  if (!response) {
    throw new Error(`邀请路由没有处理 ${new URL(request.url).pathname}`);
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

function assertIncludes(actual: string, expected: string, label: string) {
  if (!actual.includes(expected)) {
    throw new Error(`${label}缺少 ${JSON.stringify(expected)}`);
  }
}

function assertNotIncludes(actual: string, expected: string, label: string) {
  if (actual.includes(expected)) {
    throw new Error(`${label}不应包含 ${JSON.stringify(expected)}`);
  }
}
