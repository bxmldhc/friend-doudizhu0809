# 好友斗地主 · 轻量欢乐资源包

这是一套全程横屏的 Android 斗地主视觉资源与交互规范。视觉核心是「深海蓝绿牌桌 + 珊瑚橙动作 + 蜡黄身份标记」，以高对比、短路径和熟人局语气替代旧式大厅堆叠。

## 目录

- `svg/brand/`：图标、启动图、头像、牌背、地主标记、按钮与状态源文件
- `svg/cards/`：54 张扑克牌的 SVG 矢量源
- `png/android/`：Android 多密度 PNG 导出目录
- `prototype/`：全流程横屏高保真可点击 HTML 原型
- `design-spec.md`：颜色、字号、状态和命名规范
- `resource-manifest.json`：资源清单与导出映射

## 接入原则

- Android 从首页到结算始终使用 `requestedOrientation = landscape`，不再在流程中旋转 Activity。
- 手机只渲染服务器状态，不在客户端判断牌型、胜负或 AI 决策。
- 牌面按 `drawable-nodpi` 使用，避免扑克牌在不同密度下被系统二次缩放。
- SVG 是设计源；PNG 是运行时兜底。后续改色、改字只改 SVG 源后重新导出。
