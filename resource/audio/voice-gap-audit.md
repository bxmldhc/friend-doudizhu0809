# 斗地主语音资源缺口清单

## 当前已经接入

以下 8 条中文口播已经存在于 `voice` 目录，并已复制到 Android `res/raw`：

| 文件 | 台词 |
|---|---|
| `call_landlord.wav` | 叫地主 |
| `rob_landlord.wav` | 抢地主 |
| `not_call.wav` | 不叫 |
| `not_rob.wav` | 不抢 |
| `pass.wav` | 不出 |
| `cards_left_two.wav` | 我还剩两张牌 |
| `cards_left_one.wav` | 我还剩一张牌 |
| `your_turn.wav` | 轮到你了 |

## P0：建议下一批制作

这组最能让玩家明确听懂“出了什么牌”，优先级最高。

### 单牌点数

`3、4、5、6、7、8、9、10、J、Q、K、A、2、小王、大王`

建议文件：

```text
single_3.wav ... single_2.wav
single_small_joker.wav
single_big_joker.wav
```

### 常见牌型

| 建议文件 | 台词 |
|---|---|
| `pair.wav` | 对子 |
| `triple.wav` | 三张 |
| `triple_single.wav` | 三带一 |
| `triple_pair.wav` | 三带二 |
| `straight.wav` | 顺子 |
| `pair_straight.wav` | 连对 |
| `airplane.wav` | 飞机 |
| `airplane_single.wav` | 飞机带单 |
| `airplane_pair.wav` | 飞机带对 |
| `four_two_single.wav` | 四带二 |
| `four_two_pair.wav` | 四带两对 |
| `bomb.wav` | 炸弹 |
| `rocket.wav` | 王炸 |

对子建议由“牌型 + 点数”组合播放，例如“对子”后接“3”，这样不必额外制作
`对3` 到 `对2` 的 13 个完整句子。若希望更像传统斗地主，可再单独制作完整对子口播。

## P1：增强牌桌反馈

| 建议文件 | 台词 |
|---|---|
| `landlord_confirmed.wav` | 地主确定 |
| `cannot_beat.wav` | 管不上 |
| `invalid_play.wav` | 牌型不对 |
| `countdown_five.wav` | 还剩五秒 |
| `trustee_on.wav` | 已托管 |
| `reconnected.wav` | 已重新连接 |

## P2：氛围与结果

可按需制作“春天”“反春天”“加倍”“再来一局”等，但当前服务端尚未完整提供这些独立事件，
不应先接入客户端触发。

## 接入前置条件

目前客户端事件只可靠区分叫地主、抢地主、不叫、不抢、不出、剩余牌数和轮到自己。
要准确播放牌型和点数，建议服务端在已确认的 `PLAY` 事件中增加：

```json
{
  "type": "PLAY",
  "seat": 1,
  "comboType": "TRIPLE_SINGLE",
  "primaryRank": 12
}
```

客户端只根据服务器确认事件播放，不根据本地选牌提前判断，继续保持“服务器是唯一裁判”。
