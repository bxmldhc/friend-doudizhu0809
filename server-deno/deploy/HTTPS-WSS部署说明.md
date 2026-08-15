# HTTPS / WSS 部署说明

## 前提

1. 准备一个自己拥有的域名。
2. 将域名的 DNS A 记录解析到服务器公网 IP。
3. 在安全组和防火墙开放 TCP 80、443。
4. 服务端继续仅监听本机使用的 `18080` 端口。

## 安装与配置 Caddy

按 Caddy 官方 Ubuntu 安装方式安装后：

```bash
sudo cp /opt/friend-doudizhu-server/deploy/Caddyfile.example /etc/caddy/Caddyfile
sudo nano /etc/caddy/Caddyfile
```

将 `your-domain.example` 替换为实际域名，然后检查并重载：

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
sudo systemctl status caddy --no-pager
```

Caddy 会为有效且已正确解析的公网域名自动申请和续期 TLS 证书。HTTP API 使用
`https://实际域名`，WebSocket 使用 `wss://实际域名/ws`。

## 验证

```bash
curl https://实际域名/
curl https://实际域名/api/app/latest
```

不要在配置完成前关闭现有 IP 直连入口。确认新版客户端能够通过 HTTPS/WSS
登录、创建、加入和恢复房间后，再决定是否关闭安全组中的 TCP 18080。

Android App Link 还需要在实际域名发布
`/.well-known/assetlinks.json`。该文件必须使用正式签名证书的真实 SHA-256
指纹，本项目不预填域名或证书指纹。
