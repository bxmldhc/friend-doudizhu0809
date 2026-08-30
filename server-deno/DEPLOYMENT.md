# 忘忧阁服务端发布手册

## 常规更新

版本规则：

- 大厅主体重大迭代：第一位增加，例如 `2.0.0`
- 大厅功能更新：第二位增加，例如 `1.1.0`
- 斗地主、五子棋等内置游戏的小修复：第三位增加，例如 `1.0.1`
- 每次发布必须同时增加 `versionCode`

本轮版本为 `1.1.0`，`versionCode` 为 `11`。

### 1. 本地构建

在项目根目录执行：

```powershell
cd "E:\桌面\loves\斗地主"
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon
```

### 2. 生成发布文件

PowerShell 不负责写 `latest.json`，避免 UTF-8 BOM 导致服务端解析失败。先复制 APK 并计算 SHA256：

```powershell
cd "E:\桌面\loves\斗地主"
Copy-Item "app\build\outputs\apk\release\app-release.apk" "app\build\outputs\apk\release\wangyouge-1.1.0.apk" -Force
Get-FileHash "app\build\outputs\apk\release\wangyouge-1.1.0.apk" -Algorithm SHA256
```

记录输出的 SHA256，下面上传时只上传 APK：

```powershell
cd "E:\桌面\loves\斗地主"
scp -O "app\build\outputs\apk\release\wangyouge-1.1.0.apk" admin@114.55.142.169:/home/admin/
```

### 3. 服务器更新代码和 APK

登录服务器后执行。把 `ACTUAL_SHA256` 替换成上一步真实输出的 SHA256：

```bash
cd /opt/friend-doudizhu-server
git pull --ff-only origin main
sudo mv /home/admin/wangyouge-1.1.0.apk /opt/friend-doudizhu-server/releases/
sudo chown admin:admin /opt/friend-doudizhu-server/releases/wangyouge-1.1.0.apk
printf '%s\n' '{"versionCode":11,"versionName":"1.1.0","apkFile":"wangyouge-1.1.0.apk","sha256":"ACTUAL_SHA256","forceUpdate":false,"releaseNotes":["累计战绩与真人对局过滤","等待区好友邀请","好友申请分类","开局自动补位 AI","优化农民 AI 配合","新增番茄、鸡蛋、玫瑰互动"]}' | sudo tee /opt/friend-doudizhu-server/releases/latest.json > /dev/null
sudo systemctl restart friend-doudizhu
curl http://127.0.0.1:18080/api/app/latest
```

如果返回 `UPDATE_MANIFEST_INVALID`，优先检查：

1. `latest.json` 是否由服务器上的 `printf` 生成；
2. `versionCode`、`versionName`、`apkFile`、`sha256` 是否与 APK 完全对应；
3. APK 是否位于 `/opt/friend-doudizhu-server/releases/`；
4. 是否重启了 `friend-doudizhu` 服务。

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
http://114.55.142.169:18080/downloads/wangyouge-1.1.0.apk
```
