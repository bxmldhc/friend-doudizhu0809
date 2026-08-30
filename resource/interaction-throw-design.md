# 局内投掷互动接入设计

## 目标与边界

为斗地主和五子棋的进行中对局提供玩家之间的投掷互动。玩家点击其他玩家头像，在资料弹窗的“投掷”页选择番茄、鸡蛋或玫瑰；服务端确认后，所有当前在线房间成员看到同一段从投掷者头像飞向目标头像的动画。

第一期免费，不做商城、不扣积分、不补发历史动画。协议保留成本和库存字段，后续可将对应游戏总积分兑换接入服务端，不改消息骨架。

不允许客户端本地伪造成功动画。只有收到服务端 `ITEM_THROWN` 事件后才播放。

## 当前接入点

- 服务端客户端消息联合类型：`server-deno/game/protocol.ts` 的 `ClientMessage`。
- 服务端房间鉴权、限流与广播：`server-deno/game/rooms.ts` 的 `handleMessage()`、`sendQuickMessage()`。
- Android WebSocket 编码、解码：`app/src/main/java/com/loves/wangyouge/network/GameProtocol.kt`。
- Android 对局状态编解码：`app/src/main/java/com/loves/wangyouge/network/GameCodec.kt`。
- 斗地主头像与资料弹窗：`app/src/main/java/com/loves/wangyouge/ui/GameScreen.kt` 的 `OpponentPanel`、`PlayerProfileDialog`。
- 五子棋头像与资料弹窗：`app/src/main/java/com/loves/wangyouge/ui/GomokuScreen.kt` 的 `GomokuPlayerPanel`、`GomokuPlayerProfileDialog`。
- 资料选择与网络会话状态：`app/src/main/java/com/loves/wangyouge/ui/DoudizhuViewModel.kt` 的 `openPlayerProfile()`、`openGomokuPlayerProfile()`。

现有 `lastEvent` 只保存最新的一条牌局状态事件，适合叫地主、出牌、落子和语音，不适合短时互动动画。连续操作会覆盖该字段，因此投掷必须走独立 WebSocket 事件，不能复用 `lastEvent`。

## 协议

### 客户端请求

```json
{
  "type": "THROW_ITEM",
  "targetSeat": 1,
  "itemId": "TOMATO"
}
```

固定道具 ID：

- `TOMATO`
- `EGG`
- `ROSE`

### 服务端确认并房间广播

```json
{
  "type": "ITEM_THROWN",
  "eventId": "724186:3:throw:42",
  "fromSeat": 0,
  "targetSeat": 1,
  "itemId": "TOMATO",
  "cost": 0,
  "currency": "GAME_SCORE",
  "at": 1788057600123
}
```

字段规则：

- `eventId`：服务端生成、房间内唯一；客户端按此去重。
- `fromSeat`、`targetSeat`：服务器根据当前房间座位确认，客户端不可替代发送者。
- `cost`：首期恒为 `0`。
- `currency`：首期固定 `GAME_SCORE`，只作为未来积分兑换扩展位。
- `at`：服务端毫秒时间戳，仅用于排序、日志与诊断，不用于客户端倒计时。

### 错误码

- `THROW_NOT_IN_ROOM`：连接不在房间。
- `THROW_NOT_IN_GAME`：等待区、结算页或已销毁房间不可投掷。
- `THROW_INVALID_TARGET`：座位不存在、目标为空位或目标是自己。
- `THROW_INVALID_ITEM`：未知道具 ID。
- `THROW_RATE_LIMITED`：触发过快。
- `THROW_NOT_ALLOWED`：预留给未来的库存不足、禁言或管理限制。

服务端只广播成功事件；失败仅回复请求者 `ERROR`，客户端显示简短提示，不播放动画。

## 服务端规则

1. 仅允许已通过现有房间身份校验的真人玩家发送。
2. 仅在已开始且未结束的对局内允许；斗地主和五子棋共用规则。
3. 目标必须是当前房间已占用座位，可投向真人或 AI；首期禁止投自己。
4. 同一发送者冷却建议为 `8_000ms`；服务端在 `Room` 中维护每座位最近投掷时间，不能信任客户端计时。
5. 服务端验证成功后更新房间活动时间，并立即向当前连接的全部座位广播 `ITEM_THROWN`。
6. 断线、重连、刷新状态时不回放 `ITEM_THROWN`。互动是瞬时表达，不应干扰恢复中的牌局或棋局。
7. 首期不扣分，但服务端响应始终携带 `cost` 和 `currency`。以后启用积分兑换时，在验证通过、广播之前由服务端原子扣除对应游戏积分；客户端接口不变。

不建议把互动写入长期战绩、对局结算或房间排行榜。它只应作为短期房间事件，避免无意义的数据增长。

## Android 接入方案

### 网络与状态

1. `GameProtocol.kt` 新增 `encodeThrowItem(targetSeat, itemId)`，并在服务端消息解析中加入 `NetMessage.ItemThrown`。
2. `DoudizhuViewModel` 收到事件后校验 `eventId` 未处理过，再将其放入独立的 `SharedFlow<ThrowItemEvent>` 或带容量的动画队列。
3. 队列最多保留 3 个待播事件；超过时丢弃最旧未播放事件，保证局内操作和渲染不被刷屏拖慢。
4. 动画完成后仅从本地队列移除，不向服务端回执。
5. 资料弹窗状态增加 `seatIndex` 和 `canThrow`，以便“投掷”页知道目标座位、并禁止 AI/空位以外的不合法目标展示。

### 头像资料弹窗

资料弹窗使用两个 Tab：

- `资料`：保留现有历史战绩内容。
- `投掷`：显示番茄、鸡蛋、玫瑰三项；点击后立即关闭弹窗并发送请求。

按钮应显示冷却状态，例如“8 秒后可投掷”；冷却以本地展示为辅，以服务器错误为最终结果。首期可向 AI 投掷，AI 不会回投。

### 头像坐标与飞行动画

斗地主与五子棋的牌桌根 `Box` 各建立一个“桌面坐标系”。每一个可点击头像容器增加 `onGloballyPositioned`，记录：

```kotlin
seatCenters[seatIndex] = coordinates.positionInRoot() + Offset(
    coordinates.size.width / 2f,
    coordinates.size.height / 2f
)
```

根容器同样记录自身 `positionInRoot()`，动画层使用相对坐标：

```text
relativeCenter = avatarCenterInRoot - tableRootInRoot
```

收到 `ITEM_THROWN` 后：

1. 等待本帧布局完成；若投掷者或目标头像坐标尚未登记，最多等待 300ms。
2. 从 `fromSeat` 中心出发，沿二次贝塞尔曲线飞向 `targetSeat` 中心。
3. 飞行时长 `420ms`；到达后做 `180ms` 的缩放和淡出。
4. 同一事件在所有本地客户端播放；各端的实际像素坐标不同，但座位关系一致。
5. 若 300ms 后仍无坐标，使用屏幕边缘预设锚点作为兜底，并记一条调试日志；不应让事件阻塞后续动画。

建议在斗地主和五子棋分别封装为同一个 `ThrowItemOverlay`，入参统一为：当前动画事件、座位中心映射、桌面根坐标、动画结束回调。不要把动画塞进头像组件内部，否则跨屏飞行和重叠管理都会变复杂。

## 首期无正式资源时的 fallback

没有 PNG 时，仍可先完成完整协议与交互：

- 番茄：珊瑚橙实心圆，配深红小点。
- 鸡蛋：蜡黄色椭圆，配白色内层。
- 玫瑰：玫红圆瓣组合。

fallback 只用于开发和内部测试，不使用 Emoji，不用文字本身作为飞行物。道具选择按钮可保留中文名称以确保可辨识。

资源到位后只替换绘制实现或 drawable，不改协议、ViewModel、动画坐标和服务端逻辑。

## 正式 PNG 资源需求

请提供透明背景 PNG，分别命名：

- `tomato.png`
- `egg.png`
- `rose.png`

规格：

- 源尺寸：`512 x 512 px`，透明背景 RGBA。
- 内容占画布约 `80%`，四周保留至少 `10%` 安全边距，避免旋转和缩放被裁切。
- 正视或轻微 3/4 视角；轮廓在深海蓝绿色牌桌上清晰。
- 不含文字、阴影底板、白色方形背景或水印。
- Android 导出：至少 `mdpi 96px`、`xhdpi 144px`、`xxhdpi 192px`、`xxxhdpi 288px`；可由 512px 原图无损导出。

可选补充资源：

- `tomato_splat.png`
- `egg_splat.png`
- `rose_burst.png`

这些只用于命中后的 180ms 效果；未提供时以缩放淡出替代，不阻塞首期上线。

## 验收

1. 斗地主和五子棋中，点击其他玩家头像都能进入“投掷”页并选择三种道具。
2. 客户端未收到服务端成功事件时，不播放任何投掷动画。
3. 成功事件在房间内所有在线客户端各播放一次；同一 `eventId` 重复到达不重复播放。
4. 发送者每 8 秒内第二次投掷被服务端拒绝，且所有客户端都不会看到第二次动画。
5. 不能投自己、空位、等待区或已结束牌局；异常请求均由服务端拒绝。
6. 横屏常见手机尺寸下，道具从正确头像中心飞向正确目标头像；斗地主倒三角和五子棋左右布局均无裁切。
7. 连续触发时动画不遮挡出牌、落子、倒计时和结算控制；队列上限生效。
8. 断线重连后只恢复牌局状态，不回放离线期间或重连前的投掷。
9. 当前所有成功响应的 `cost` 为 `0`，但协议字段已存在，未来启用游戏积分兑换无需更新客户端消息结构。
