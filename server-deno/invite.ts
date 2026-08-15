import { loadPublishedUpdate } from "./updates.ts";

const ROOM_CODE_PATTERN = /^\d{6}$/;

export async function handleInviteRequest(
  req: Request,
): Promise<Response | null> {
  const url = new URL(req.url);
  const match = url.pathname.match(/^\/invite\/([^/]+)$/);
  if (!match || (req.method !== "GET" && req.method !== "HEAD")) return null;

  const roomCode = decodeRoomCode(match[1]);
  if (!roomCode || !ROOM_CODE_PATTERN.test(roomCode)) {
    return new Response("未找到该邀请", {
      status: 404,
      headers: { "content-type": "text/plain; charset=utf-8" },
    });
  }

  const appUrl = `friendddz://join?room=${encodeURIComponent(roomCode)}`;
  const downloadUrl = await latestApkUrl(url.origin);
  const body = renderInvitePage(roomCode, appUrl, downloadUrl);
  const headers = {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
    "content-security-policy": [
      "default-src 'none'",
      "style-src 'unsafe-inline'",
      "script-src 'unsafe-inline'",
      "img-src 'none'",
      "base-uri 'none'",
      "form-action 'none'",
      "frame-ancestors 'none'",
    ].join("; "),
    "referrer-policy": "no-referrer",
    "x-content-type-options": "nosniff",
  };
  return new Response(req.method === "HEAD" ? null : body, { headers });
}

async function latestApkUrl(origin: string): Promise<string> {
  try {
    const update = await loadPublishedUpdate();
    return new URL(
      `/downloads/${encodeURIComponent(update.apkFile)}`,
      origin,
    ).toString();
  } catch {
    return new URL("/api/app/latest", origin).toString();
  }
}

function decodeRoomCode(value: string): string {
  try {
    return decodeURIComponent(value).trim();
  } catch {
    return "";
  }
}

function renderInvitePage(
  roomCode: string,
  appUrl: string,
  downloadUrl: string,
): string {
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="theme-color" content="#0b5d5b">
  <title>加入好友斗地主房间 ${roomCode}</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      color: #f7fbfa;
      background: #073f42;
      font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
    }
    main {
      width: min(100%, 480px);
      padding: 28px;
      text-align: center;
      background: #0b5d5b;
      border: 1px solid #2c7a76;
      border-radius: 8px;
      box-shadow: 0 18px 44px rgba(0, 0, 0, .24);
    }
    h1 { margin: 0 0 10px; font-size: 28px; letter-spacing: 0; }
    p { margin: 8px 0 22px; color: #d9efeb; line-height: 1.6; }
    strong { color: #f7cf67; font-size: 30px; letter-spacing: 4px; }
    a {
      display: flex;
      min-height: 52px;
      align-items: center;
      justify-content: center;
      margin-top: 12px;
      padding: 12px 18px;
      border-radius: 8px;
      color: #102f30;
      background: #ff8066;
      font-size: 17px;
      font-weight: 700;
      text-decoration: none;
    }
    a.secondary {
      color: #f7fbfa;
      background: transparent;
      border: 1px solid #8fc9c2;
    }
    small { display: block; margin-top: 18px; color: #b8dcd7; line-height: 1.5; }
  </style>
</head>
<body>
  <main>
    <h1>好友斗地主</h1>
    <p>朋友邀请你加入房间</p>
    <strong>${roomCode}</strong>
    <a id="open-app" href="${appUrl}">打开应用并加入房间</a>
    <a class="secondary" href="${downloadUrl}">下载最新版应用</a>
    <small>如果没有自动打开，请点击上方按钮。</small>
  </main>
  <script>
    window.setTimeout(function () {
      window.location.href = ${JSON.stringify(appUrl)};
    }, 300);
  </script>
</body>
</html>`;
}
