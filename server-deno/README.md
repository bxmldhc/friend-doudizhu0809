# 斗地主联机服务器（Deno）

这是远程联机斗地主的 WebSocket 服务器，使用 TypeScript + Deno 编写，可直接部署到 Deno Deploy 免费套餐。

## 本地运行

安装 Deno 后执行：

```bash
cd server-deno
deno task dev
```

默认监听 `18080`，客户端本地联调填：

```text
ws://10.0.2.2:18080
```

## 部署到 Deno Deploy

1. 把 `server-deno` 目录推送到一个 GitHub 仓库。
2. 打开 `https://dash.deno.com`，用 GitHub 登录。
3. 点击 `New Project`，选择 `Deploy from GitHub`。
4. 选择刚才的仓库，入口文件填：

```text
main.ts
```

5. 部署完成后会得到一个地址，例如：

```text
https://doudizhu-server.deno.dev
```

6. Android 客户端里的“服务器地址”填：

```text
wss://doudizhu-server.deno.dev
```

客户端会自动在地址后面补上 `/ws`。

## 功能

- 6 位房号建房 / 加入
- 三人一桌，创建房间时明确选择 0~2 个 AI
- 服务器负责校验出牌、同步牌局、托管 AI
- 胜负和战绩保存在 Deno KV 中，重启不丢失

## 数据与隐私

- 服务器会接收客户端昵称、房号、牌局操作和牌局状态。
- Deno KV 会按昵称保存对局次数和胜场；昵称不是账号系统，也没有身份验证。
- 房间状态、手牌、重连凭证和场内分数保存在服务器运行内存中，房间销毁后不应继续保留。
- 不要在昵称中填写真实姓名、手机号、微信号、邮箱或其他个人信息。
- 公开部署前，建议使用 `wss://`，并根据实际用途补充访问控制、限流、日志清理和隐私声明。
- 不要把真实房号、运行日志、Deno 凭证或部署配置提交到 Git。

## 免费额度

Deno Deploy 免费套餐：

- 每月约 100 万次请求
- 100GB 流量
- 1GB KV 存储

朋友之间娱乐完全够用。
