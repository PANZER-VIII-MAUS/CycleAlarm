# CycleAlarm 设计令牌

以下色值、字号与尺寸用于保持主界面、对话框和设置页的视觉一致。

| 类别 | 令牌 | 值 | 用途 |
|---|---|---|---|
| 浅色颜色 | 主色 | `#038C85` | 主按钮、选中态、开关开启态 |
| 浅色颜色 | 页面底色 | `#F2F4F8` | 主界面与设置页背景 |
| 浅色颜色 | 卡片底色 | `#FFFFFF` / 24dp 圆角 | 每日卡片、信息卡片与设置卡片 |
| 浅色颜色 | 主文字 | `#101418` | 标题、时间、主要正文 |
| 浅色颜色 | 次文字 | `#5F6672` | 副标题、说明与未选中状态 |
| 浅色颜色 | 对话框大时间 | `#0A3B5F` | 时间选择对话框的醒目时间 |
| 浅色颜色 | 危险色 | `#C84239` | 删除等不可逆操作 |
| 深色颜色 | 主色 | `#4ED0C6` | 深色主题主按钮、选中态与开关开启态 |
| 深色颜色 | 页面底色 | `#14161A` | 深色主题主界面与设置页背景 |
| 深色颜色 | 卡片底色 | `#1C1F24` | 深色主题卡片 |
| 字号 | 顶栏 / 对话框标题 | 20sp | `titleLarge` / `headlineSmall` |
| 字号 | 卡片日序 | 18sp | `titleMedium` |
| 字号 | 卡片时间 | 30sp | `headlineMedium`（紧凑布局降为 `titleLarge`） |
| 字号 | 副标题 / 卡片小标题 | 15sp | `bodyMedium` / `titleSmall` |
| 字号 | 主按钮 / 保存 | 17sp | `labelLarge` |
| 字号 | 对话框大时间 | 54sp | `displayMedium` |
| 字号 | 小标签 / 删除操作 | 14sp | `bodySmall` / `labelMedium` |
| 圆角 | 卡片 | 24dp | 主界面和设置页卡片 |
| 圆角 | 对话框 | 28dp | 时间、选择和确认对话框 |
| 控件尺寸 | 主按钮 | 56dp 胶囊 | 底部「添加闹钟」、保存等主操作 |

## 自适应断点（0.6.1 起）

| 场景 | 判定 | 行为 |
|---|---|---|
| 大字号 | `LocalDensity.fontScale > 1.25` | 日卡改用两行紧凑布局；下次响铃大时钟降为 `titleLarge` 且单行显示 |
| 窄卡片 | 卡片可用宽度 `< 340dp`（`BoxWithConstraints`） | 同样切换两行紧凑布局，时间不再被裁切 |
| 矮屏 / 横屏 | `screenHeightDp < 480` | 下次响铃卡与「今天是第 N 天」**进入滚动列表**；底部栏垂直内边距收窄到 6dp |
| 宽屏 / 平板 | `screenWidthDp > 700` | 内容左右内边距自动放大，正文收窄到 640dp 视觉宽度 |

> 紧凑布局（大字号 / 窄卡）下的日卡结构：第 1 行「第 X 天 ·」→ 第 2 行时间（`titleLarge`）→ 第 3 行副标题（`bodySmall`）。

## 文案与本地化

- 所有界面文案在 `app/src/main/res/values/strings.xml`（中文）与 `values-en/strings.xml`（英文），**代码里不再硬编码中文**
- 时间/数字占位符统一用 `%1$s`、`%1$d`；英文文案里的撇号需转义为 `\'`，裸 `%` 需写成 `percent`（aapt2 会校验）
- 数据层错误只抛 `LocalizedFailure(资源 id, 参数…)`，界面用 `Throwable.uiMessage(context)` 解析
- 12 小时制的上下午标签用资源 `time_am` / `time_pm`（中文「上午/下午」，英文 AM/PM）

## 组件与样式落点

| 组件 | 位置 |
|---|---|
| 每日卡片 / 手动闹钟行 | `ui/DayPlanCard.kt` |
| 下次响铃卡 | `ui/NextAlarmCard.kt` |
| 循环周期卡 | `ui/CycleInfoCard.kt` |
| 假期卡 / 假期对话框 | `ui/HolidayCard.kt`、`ui/HolidayDialogs.kt` |
| 闹钟编辑对话框 | `ui/EditAlarmDialog.kt` |
| 通用卡片 / 胶囊按钮 / 遮罩对话框 | `ui/components/` |
| 颜色令牌定义 | `ui/theme/Color.kt` |
