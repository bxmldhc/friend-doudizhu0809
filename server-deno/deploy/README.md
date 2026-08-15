# 阿里云 Ubuntu 22.04 部署

以下步骤默认服务器登录用户为 `admin`，仓库为：

```text
https://github.com/bxmldhc/friend-doudizhu-server0809.git
```

## 1. 安装 Git 和 Deno

```bash
sudo apt update
sudo apt install -y git unzip
curl -fsSL https://deno.land/install.sh | sh
/home/admin/.deno/bin/deno --version
```

## 2. 下载服务端

```bash
sudo git clone https://github.com/bxmldhc/friend-doudizhu-server0809.git /opt/friend-doudizhu-server
sudo chown -R admin:admin /opt/friend-doudizhu-server
sudo mkdir -p /var/lib/friend-doudizhu
sudo chown admin:admin /var/lib/friend-doudizhu
```

## 3. 安装开机自启服务

```bash
sudo cp /opt/friend-doudizhu-server/deploy/friend-doudizhu.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now friend-doudizhu
sudo systemctl status friend-doudizhu --no-pager
```

## 4. 放行端口

在阿里云安全组入方向放行 TCP `18080`，授权对象可暂设为 `0.0.0.0/0`。如果启用了
Ubuntu UFW，再执行：

```bash
sudo ufw allow 18080/tcp
```

测试地址：

```text
http://114.55.142.169:18080/
```

Android 客户端地址：

```text
ws://114.55.142.169:18080
```

## 更新服务

```bash
cd /opt/friend-doudizhu-server
git pull
sudo systemctl restart friend-doudizhu
sudo systemctl status friend-doudizhu --no-pager
```

查看最近日志：

```bash
sudo journalctl -u friend-doudizhu -n 100 --no-pager
```

当前公网 IP 直连使用未加密的 `ws://`。朋友小范围测试可以先用，
正式长期使用建议后续绑定域名并配置 HTTPS/WSS。
