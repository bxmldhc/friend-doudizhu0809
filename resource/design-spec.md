# 好友斗地主视觉规范

## 视觉系统

轻量欢乐：用像朋友聊天一样直接的文案，配合深海蓝绿的稳定牌桌和珊瑚橙的少量动作强调。牌桌是主场，入口和结算采用干净的深色面板，不做传统棋牌游戏的金色堆饰。

## Tokens

```css
--bg: oklch(18% 0.035 215);
--surface: oklch(23% 0.04 214);
--fg: oklch(97% 0.02 95);
--muted: oklch(75% 0.035 205);
--border: oklch(100% 0 0 / 14%);
--accent: oklch(72% 0.17 42);
--felt: oklch(34% 0.09 165);
--gold: oklch(83% 0.15 87);
--danger: oklch(68% 0.17 26);
--success: oklch(72% 0.13 158);
```

字体：展示使用 `"Noto Sans SC"` 600，正文使用 `"Noto Sans SC"` 400，数字与房号使用 `"JetBrains Mono"` 600。标题负字距 `-0.02em`，按钮与标签字距 `0.02em`。

## 关键状态

`room_created` 对应首页房号分享；`player_joined` 对应等待区座位；`game_started` 对应牌桌；`bid_required` / `rob_required` 对应叫地主与抢地主操作；`play_required` 对应出牌操作；`play_rejected` 保留选牌并显示错误；`connection_lost` 显示重连浮层；`game_over` 进入结算与战绩。

## 资源命名

使用小写短横线：`card-heart-a.svg`、`card-back.svg`、`action-call.svg`、`status-reconnecting.svg`。牌面尺寸源为 160×224，牌背为 160×224，图标源为 96×96，按钮源为 240×88。
