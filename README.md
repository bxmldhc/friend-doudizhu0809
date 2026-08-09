# 斗地主 Android 应用

基于 Kotlin + Jetpack Compose 的好友斗地主，三人一桌，创建房间时可选择 0~2 个 AI。

## 功能

- AI 数量：0~2 个，对应需要 3~1 名真人玩家
- AI 难度：简单 / 中等 / 困难
- 本机人机练习
- 好友远程联机：创建房间 / 输入房号加入
- 叫地主与抢地主
- 标准牌型：单张、对子、三张、三带一、三带二、顺子、连对、飞机、飞机带翅膀、四带二、炸弹、王炸
- 炸弹和王炸翻倍计分
- 出牌提示、不出、回合高亮、底牌展示、胜负结算与累计积分
- 困难 AI 会记牌、避免拆炸弹、优先甩长顺、根据剩余牌数选择是否拆对

## 联机服务器

联机版使用 Deno Deploy 免费托管：

- 服务器代码位于 `server-deno/`
- 部署方法见 `server-deno/README.md`
- 服务器地址可以填写 Deno Deploy 提供的 `https://...` 地址，客户端会自动转换为 `wss://.../ws`；也支持直接填写 `wss://.../ws`。
- 房间号为 6 位数字，朋友输入房号即可加入

## 环境要求

- JDK 17 或更高
- Android SDK Platform 35
- Android Studio 或 Gradle 8.10.2+

首次打开项目时，请让 Android Studio 使用本机已安装的 JDK 和 Android SDK。
`local.properties` 只属于本机配置，不要提交到 Git。

## Android Studio 方式

1. 打开 Android Studio。
2. 选择 `Open`，打开本项目目录。
3. 等待 Gradle 同步完成。
4. 连接手机并开启 USB 调试，或启动模拟器。
5. 点击 Run，安装并启动应用。

## 命令行编译

```powershell
cd <项目目录>
$env:JAVA_HOME="<JDK 目录>"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的手机：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 运行测试

```powershell
cd <项目目录>
$env:JAVA_HOME="<JDK 目录>"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest
```

测试覆盖牌型识别、管牌规则、合法出牌生成、叫地主流程、回合流转、AI 决策。

## 项目结构

```text
app/src/main/java/com/loves/doudizhu/
├── MainActivity.kt
├── ai/AiDecider.kt
├── logic/
│   ├── HandEvaluator.kt
│   ├── LegalPlayGenerator.kt
│   └── DoudizhuEngine.kt
├── model/
│   ├── Card.kt
│   └── GameConfig.kt
└── ui/
    ├── DoudizhuApp.kt
    ├── DoudizhuViewModel.kt
    ├── GameScreen.kt
    ├── SettingsScreen.kt
    └── theme/Theme.kt
```

## 对局规则

- 使用 54 张牌，每人 17 张，留 3 张底牌。
- 随机一名玩家开始叫地主，其余玩家可以抢地主或不抢，最后叫/抢者为地主并收底牌。
- 三家都不叫时自动重新发牌。
- 地主先出牌；每轮只能跟同牌型且更大的牌，炸弹可压普通牌型，王炸最大。
- 连续两家不出后，上一手出牌者获得新一轮出牌权。
- 地主获胜：地主得分，两个农民失分；农民获胜：两个农民得分，地主失分。
- 基础分由叫/抢次数决定，炸弹和王炸每次使本局倍数翻倍。

## 同屏轮流说明

- `同屏轮流`：可设置 1~3 名真人。轮到哪位真人时，手机只显示他的手牌，操作完自动交棒。
- `单人专注`：固定 1 名真人 + 2 名 AI，AI 回合也能一直看到自己的手牌。

## 常见问题

- 项目位于中文路径时，AGP 会在 Windows 上提示路径检查。项目已加入 `android.overridePathCheck=true`。
- 中文路径下运行单元测试时，Gradle 测试 worker 的 UTF-8 类路径文件可能被 GBK 读乱。项目已加入 Debug 测试专用 ASCII 临时类路径兼容逻辑，不影响 APK 构建。
- Gradle 分发下载源已配置为腾讯云镜像，避免 `services.gradle.org` 超时。
- 如果换了 Android SDK 位置，修改 `local.properties` 中的 `sdk.dir`。
- 首次构建需要联网下载 Gradle 插件和依赖；之后会使用本地缓存。

## 数据与隐私

- 应用会把用户填写的昵称、最近战绩和重连凭证保存在设备本地，用于显示战绩和恢复牌局。
- 联机服务器会接收昵称、房号、牌局状态和操作消息。服务器端会把昵称对应的对局次数与胜场写入 Deno KV。
- 房间、手牌、重连凭证和场内分数属于联机运行数据，不应写入源码、截图、日志或提交记录。
- 请使用不包含真实姓名、手机号、微信号或其他个人信息的游戏昵称。
- 部署自己的服务器时，请只使用 `wss://` 地址，并为生产环境补充访问控制、数据清理策略和隐私声明。
