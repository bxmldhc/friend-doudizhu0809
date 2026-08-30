# 项目协作规则

## 回复与操作偏好

- 使用中文回复。
- 用户希望由助手准备可直接复制执行的命令，用户自行在 PowerShell 或服务器终端执行。
- 用户明确要求更新时，默认完成构建、发布准备、上传和部署命令，不重复询问是否上传。
- 不声称已经上传或部署，除非已经实际执行并验证；若需要用户执行，明确标注执行环境。

## 标准发布流程

### 1. 构建 Android APK

在本机 PowerShell：

```powershell
cd "E:\桌面\loves\斗地主"
$env:JAVA_HOME = "E:\DevTools\Java21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleRelease --no-daemon
```

产物通常位于：

```text
E:\桌面\loves\斗地主\app\build\outputs\apk\release\app-release.apk
```

发布前复制为当前版本文件名，例如 `wangyouge-1.0.6.apk`，并使用实际 APK 计算 SHA-256，禁止伪造校验值。

### 2. 更新清单

清单文件：

```text
E:\桌面\loves\斗地主\server-deno\releases\latest.json
```

服务器部署时优先使用 `printf` 写入纯 UTF-8、无 BOM 的 JSON，避免 PowerShell 编码导致 Deno 解析失败：

```bash
printf '%s\n' '{"versionCode":7,"versionName":"1.0.6","apkFile":"wangyouge-1.0.6.apk","sha256":"实际SHA256","forceUpdate":false,"releaseNotes":["更新说明"]}' | sudo tee /opt/friend-doudizhu-server/releases/latest.json > /dev/null
```

### 3. Git 推送

Android 主仓库：

```powershell
cd "E:\桌面\loves\斗地主"
git add .
git commit -m "feat: 发布忘忧阁 新版本"
git push origin main
```

服务端仓库：

```powershell
cd "E:\桌面\loves\斗地主\server-deno"
git add .
git commit -m "feat: 更新服务端"
git push origin main
```

### 4. 上传 APK

在本机 PowerShell 使用服务器 `/home/admin`，不要默认使用 `/tmp`：

```powershell
cd "E:\桌面\loves\斗地主"
scp -O "app\build\outputs\apk\release\wangyouge-版本号.apk" "server-deno\releases\latest.json" admin@114.55.142.169:/home/admin/
```

如果文件名或目标目录检查异常，改用明确的远端文件名：

```powershell
scp -O "app\build\outputs\apk\release\wangyouge-版本号.apk" admin@114.55.142.169:/home/admin/wangyouge-版本号.apk
scp -O "server-deno\releases\latest.json" admin@114.55.142.169:/home/admin/latest-upload.json
```

### 5. 服务器部署

登录阿里云服务器后：

```bash
cd /opt/friend-doudizhu-server
git pull --ff-only origin main
sudo systemctl restart friend-doudizhu
sudo systemctl status friend-doudizhu --no-pager

sudo mv /home/admin/wangyouge-版本号.apk /opt/friend-doudizhu-server/releases/
sudo mv /home/admin/latest-upload.json /opt/friend-doudizhu-server/releases/latest.json
sudo chown admin:admin /opt/friend-doudizhu-server/releases/wangyouge-版本号.apk
sudo chown admin:admin /opt/friend-doudizhu-server/releases/latest.json

curl http://127.0.0.1:18080/api/app/latest
```

验证结果必须确认版本号、APK 文件名和 SHA-256 都对应本次构建。更新清单变化后通常不需要重启服务；只有服务端代码变化时才重启。

## 版本规则

- 游戏大厅主版本使用 `主版本.功能版本.修订版本`。
- 当前应用主体为 `忘忧阁`，版本从 `1.0.0` 开始。
- 斗地主或其他内置游戏的小修复使用第三位。
- 大厅功能或新增游戏使用第二位。
- 破坏性架构或重大产品迭代使用第一位。
