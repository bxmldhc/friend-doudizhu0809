# 忘忧阁服务端发布手册

## 常规更新

本机 PowerShell 上传：

```powershell
cd "E:\桌面\loves\斗地主"
scp -O "app\build\outputs\apk\release\wangyouge-版本号.apk" "server-deno\releases\latest.json" admin@114.55.142.169:/home/admin/
```

服务器执行：

```bash
cd /opt/friend-doudizhu-server
git pull --ff-only origin main
sudo systemctl restart friend-doudizhu
sudo mv /home/admin/wangyouge-版本号.apk /opt/friend-doudizhu-server/releases/
sudo mv /home/admin/latest.json /opt/friend-doudizhu-server/releases/latest.json
sudo chown admin:admin /opt/friend-doudizhu-server/releases/wangyouge-版本号.apk
sudo chown admin:admin /opt/friend-doudizhu-server/releases/latest.json
curl http://127.0.0.1:18080/api/app/latest
```

如果 `latest.json` 出现 `UPDATE_MANIFEST_INVALID`，不要用 PowerShell 重写 JSON，也不要直接上传包含中文说明的清单。直接在服务器用 `printf` 写入纯 ASCII、无 BOM 内容：

```bash
printf '%s\n' '{"versionCode":10,"versionName":"1.0.9","apkFile":"wangyouge-1.0.9.apk","sha256":"ACTUAL_SHA256","forceUpdate":false,"releaseNotes":["Unified rooms","Friends and presence","Gomoku records"]}' | sudo tee /opt/friend-doudizhu-server/releases/latest.json > /dev/null
curl http://127.0.0.1:18080/api/app/latest
```

## 常用维护

查看服务：

```bash
sudo systemctl status friend-doudizhu --no-pager
```

重启服务：

```bash
sudo systemctl restart friend-doudizhu
```

查看最近日志：

```bash
sudo journalctl -u friend-doudizhu -n 100 --no-pager
```

下载地址：

```text
http://114.55.142.169:18080/downloads/wangyouge-版本号.apk
```
