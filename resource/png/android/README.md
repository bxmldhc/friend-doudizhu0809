# Android PNG 导出位

本运行环境未提供可用的 SVG 光栅化器，因此这里不放伪造或 1×1 占位 PNG。请使用 Android Studio / Inkscape / ImageMagick 将 `svg/` 下源文件导出到以下密度目录：

- `drawable-xhdpi`：源尺寸 × 1
- `drawable-xxhdpi`：源尺寸 × 1.5
- `drawable-xxxhdpi`：源尺寸 × 2

推荐牌面使用 `drawable-nodpi`，源尺寸 160×224；牌桌背景使用 `drawable-nodpi` 或按设备宽高裁切，不要把整张 1920×900 背景强行缩放到所有设备。
