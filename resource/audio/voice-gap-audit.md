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

此外，1.3.0 已接入以下无台词牌型短音效：

| 文件 | 服务端确认牌型 |
|---|---|
| `card_play.wav` | 单牌、四带二 |
| `play_pair.wav` | 对子 |
| `play_triple.wav` | 三张、三带一、三带二 |
| `play_straight.wav` | 顺子、连对 |
| `play_airplane.wav` | 飞机、飞机带单、飞机带对 |
| `play_bomb.wav` | 炸弹 |
| `play_rocket.wav` | 王炸 |

这些文件是操作反馈音效，不是真人念牌语音。

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

## 已完成的协议支持

服务端已在确认合法出牌后下发：

```json
{
  "type": "PLAY",
  "seat": 1,
  "comboType": "TRIPLE_SINGLE",
  "primaryRank": 12,
  "comboLength": 1
}
```

客户端只根据该服务器确认事件播放，不根据本地选牌提前判断，继续保持“服务器是唯一裁判”。

待补齐 P0 真人录音后，可直接利用 `comboType` 和 `primaryRank` 组合播放，不需要再次修改服务端裁判逻辑。
