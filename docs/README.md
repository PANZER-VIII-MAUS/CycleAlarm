# CycleAlarm 文档索引

| 文档 | 内容 |
|---|---|
| [../README.md](../README.md) | 项目说明、功能、权限、更新日志、构建与打包、核心实现 |
| [design-tokens.md](design-tokens.md) | 色值 / 字号 / 圆角 / 自适应断点等设计令牌 |
| [version-history.md](version-history.md) | 版本里程碑与当前待办（0.1.0 → 0.6.1） |
| [screenshots/](screenshots/) | 0.6.1 截图（中文 / English / 深色 / 设置页） |
| [ui-reskin/design-reference.jpg](ui-reskin/design-reference.jpg) | 早期界面设计参考图 |
| [../tools/README-signing.md](../tools/README-signing.md) | 签名密钥保管与换密码流程 |

## 约定

- **界面文案**一律放 `app/src/main/res/values/`（中文）与 `values-en/`（英文），代码里不再硬编码；
  数据层只抛 `LocalizedFailure` 错误码，由界面用 `uiMessage(context)` 解析
- **识别关键词**（匹配中文课表用的「周一 / 第1-2节 / 单双周 / 休息」等）刻意保留中文，改动前请先读源码注释
- **测试**：`gradlew testDebugUnitTest`（253 个），涉及 Room 迁移的用 Robolectric + `room-testing`
- **发布**：`gradlew assembleRelease` → 打 tag → GitHub Release 上传 APK；签名密钥与密码管理见签名文档
