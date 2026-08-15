# 好友斗地主联机服务器

这是“好友斗地主”的 WebSocket 服务端，使用 TypeScript 和 Deno 编写。
服务器是牌局的唯一裁判，负责房间、发牌、叫抢地主、出牌校验、AI 托管、
断线处理、场内计分和玩家累计战绩。账号 API 使用昵称和密码登录，
长期战绩绑定不可变的 `accountId`，昵称只用于登录和展示。

## 本地运行

安装 Deno 后，在本目录执行：

```bash
deno task dev
```

默认监听 `18080`。Android 模拟器连接本机时填写：

```text
ws://10.0.2.2:18080
```

客户端会自动补全 `/ws`。

健康检查：

```text
GET http://127.0.0.1:18080/
```

正常返回 `Doudizhu server is running`。

## 账号 REST API

所有接口返回 JSON。成功格式：

```json
{ "ok": true, "data": {} }
```

失败格式：

```json
{ "ok": false, "error": { "code": "错误码", "message": "可显示的错误信息" } }
```

| 方法   | 路径                 | 鉴权              | 用途                             |
| ------ | -------------------- | ----------------- | -------------------------------- |
| `POST` | `/api/auth/register` | 无                | 使用 `nickname`、`password` 注册 |
| `POST` | `/api/auth/login`    | 无                | 登录并获取会话 token             |
| `GET`  | `/api/auth/me`       | Bearer token      | 获取当前公开账号信息             |
| `POST` | `/api/auth/logout`   | 可选 Bearer token | 注销当前 token                   |
| `GET`  | `/api/stats/me`      | Bearer token      | 获取当前账号累计战绩             |

注册和登录请求示例：

```bash
curl -X POST http://127.0.0.1:18080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"测试玩家","password":"change-me"}'
```

需要登录的接口使用：

```bash
curl http://127.0.0.1:18080/api/auth/me \
  -H 'Authorization: Bearer 这里替换为登录返回的token'
```

昵称规则：

- 去除首尾空白并进行 NFKC 标准化。
- 长度为 2 到 12 个字符。
- 不区分大小写，不能与已有昵称或保留名称重复。

密码规则：

- 长度为 6 到 64 个字符。
- 服务端只保存 PBKDF2 派生结果和随机盐，不在 API 中返回密码散列或盐。
- 会话 token 默认有效 30 天，应按密码同等敏感信息保护。

## 环境变量

| 变量                     | 默认值                 | 说明                                   |
| ------------------------ | ---------------------- | -------------------------------------- |
| `PORT`                   | `18080`                | HTTP 和 WebSocket 共用监听端口         |
| `DENO_KV_PATH`           | 本地 `./data/local.db` | 自托管 KV 文件路径；Deno Deploy 不设置 |
| `AUTH_PBKDF2_ITERATIONS` | `120000`               | 密码派生迭代次数，最低接受 `10000`     |
| `APP_RELEASES_DIR`       | 本地 `./releases`      | APK 和 `latest.json` 的发布目录         |

生产环境不要为了加快登录而降低 `AUTH_PBKDF2_ITERATIONS`。修改迭代次数只影响
之后注册的账号，已有账号会继续使用注册时保存的迭代次数。

## 应用更新接口

| 方法  | 路径                   | 用途                         |
| ----- | ---------------------- | ---------------------------- |
| `GET` | `/api/app/latest`      | 获取当前发布版本和安装包地址 |
| `GET` | `/downloads/{apkFile}` | 下载 APK                     |

发布目录需要包含 `latest.json` 和清单中指定的 APK。APK 与正式发布清单不提交
Git，应单独上传到服务器。完整步骤见根目录
[`自动更新发布手册.md`](../自动更新发布手册.md)。

## 阿里云 Ubuntu 自托管

仓库内的 `deploy/friend-doudizhu.service` 用于 systemd 开机自启。推荐部署目录：

```text
/opt/friend-doudizhu-server
```

持久化数据库：

```text
/var/lib/friend-doudizhu/game.db
```

首次部署流程见 [deploy/README.md](deploy/README.md)。

当前服务配置为：

```text
服务名：friend-doudizhu
端口：18080
KV：/var/lib/friend-doudizhu/game.db
```

日常启动、停止、更新、日志和备份操作见根目录
[`服务端操作手册.md`](../服务端操作手册.md)。

## Deno Deploy

在 Deno Deploy 中不要设置 `DENO_KV_PATH`，应用会使用分配给项目的托管 KV。
首次创建应用并绑定 KV 后，可以从 `server-deno` 目录部署：

```bash
deno deploy . \
  --org bxmldhc \
  --app friend-doudizhu-server0809 \
  --prod
```

如尚未绑定 KV，需要先在 Deno Deploy 控制台或 CLI 中创建 Deno KV 数据库，
并把它分配给 `friend-doudizhu-server0809` 应用。部署成功后，Android 应使用
平台提供的 `wss://` 地址。

## 数据说明

- 房间状态、手牌、重连信息和场内分数保存在运行内存中。
- 房间内所有真人都断线后，房间销毁，场内数据随之清除。
- 账号、会话和长期战绩保存在 KV 中。
- 新版客户端登录后，服务端用可信 `accountId` 覆盖客户端提交的身份。
- 长期记录目前包含累计总分、局数和胜局数；AI 不计入长期战绩。
- 同一账号可以跨设备读取同一份累计战绩。
- 旧版 APK 仍允许游客身份进入，属于迁移兼容行为，不具备账号身份保证。
- 数据库备份必须妥善保管，其中包含账号密码派生结果和有效会话。
- 公网正式使用应配置 HTTPS/WSS，避免密码和 token 以明文传输。

## 验证

运行全部测试：

```bash
deno task test
```

只运行 REST API 路由测试：

```bash
deno test -A --unstable-kv api_test.ts
```

静态检查：

```bash
deno check main.ts api_test.ts updates_test.ts
```
