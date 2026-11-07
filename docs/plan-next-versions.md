# CycleAlarm 后续执行顺序

> 最近一轮大清理：五轮复审（基线 0.6.5）的 15 条附录 B 问题已全部闭环，见
> `docs/REVIEW-CHECKLIST.md` 附录 B 与 `docs/release-audit-0.6.*.md`。
> 本文只保留「还没做完的」，按优先级排序；新问题请补在对应段落而不是另开文档。

## 当前状态（2026-11-07）

| 项 | 值 |
|---|---|
| 版本 | **0.6.8**（versionCode 36），Release `v0.6.8` 已发布 |
| main | `a9de04c` + 本次文档/CI 提交 |
| 数据库 | v11（迁移链 4→5→6→7→8→9→10→11） |
| 单测 | 308（direct / play 各 308 次执行），0 失败 |
| lint | 0 error / 13 warning |
| CI | build-debug + instrumentation（API 34/36 模拟器）；API36 偶发模拟器镜像下载失败，已加自动重试 |
| flavor | `direct`（自用，含常驻守护服务）/ `play`（manifest 删除该服务） |

## 剩余事项

### 第一批：工程收尾（随时可做）

| # | 项 | 验证方式 |
|---|---|---|
| 1 | CI API36 模拟器稳定性：观察加固后的 run 是否恢复全绿；若仍偶发，考虑 pin emulator 版本/改 `target: default` | GitHub Actions run 列表 |
| 2 | lint 剩余 13 条：TypographyDashes 3、ImplicitSamInstance 3（误报）、UnusedResources 2、其余各 1 | `lintDirectDebug` 报告 |
| 3 | Play 上架包安装回归（`app-play-release.apk`）每次发布前跑一次 | 装置安装 + 确认无 `AlarmGuardService` |

### 第二批：等外部条件

| # | 项 | 条件 |
|---|---|---|
| 4 | 2027 法定节假日数据 | 国务院通常 11 月底公布 → 追加 `ChinaHolidaysProvider` 数据并发小版本 |
| 5 | API 35/36 真机全链路回归 | 手机升 ColorOS 16（= Android 16）后，按清单 §3 手测 |
| 6 | ColorOS 全屏意图（FSI）限制 | 已知不可依赖；现有方案 = 悬浮窗兜底 + 健康横幅，若 ROM 政策变化再评估 |

### 第三批：上架 Play 准备（需要决策）

| # | 项 | 说明 |
|---|---|---|
| 7 | 隐私政策 + Data safety 表单 | 本应用不联网、不上传数据，声明相对简单 |
| 8 | 四项敏感权限用途说明 | `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `SYSTEM_ALERT_WINDOW` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（README 已有逐条说明，可直接用于表单） |
| 9 | `FOREGROUND_SERVICE_SPECIAL_USE` 声明 | `play` flavor 已删除常驻守护服务，仅响铃服务使用 |
| 10 | target API 声明与 Play 政策核对 | targetSdk 36，符合当前要求 |

### 第四批：功能候选（未排期，需要拍板）

| # | 功能 | 价值 |
|---|---|---|
| 11 | ICS / 日历订阅导入 | 比 Excel 门槛低，可与系统日历联动 |
| 12 | 桌面小组件可配置（选显示哪一天/哪条闹钟） | 提升小组件实用性 |
| 13 | 多套循环方案并存切换（工作周期 / 假期周期） | 适合倒班 + 假期两种生活节奏 |
| 14 | 排班导出 CSV / 图片 | 分享与存档 |
| 15 | 备份自动同步网盘 | 需用户授权，工作量大 |

## 发布命令（flavor 拆分后）

```powershell
# 单测与 lint
.\gradlew.bat testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug

# 打印建（个人自用，含常驻守护服务）
.\gradlew.bat assembleDirectRelease
# 产物：app/build/outputs/apk/direct/release/app-direct-release.apk

# 上架包（manifest 无守护服务）
.\gradlew.bat assemblePlayRelease
# 产物：app/build/outputs/apk/play/release/app-play-release.apk

# 真机冒烟（debug 包独立包名，可与正式包共存）
.\gradlew.bat connectedDirectDebugAndroidTest
```

## 教训（写进流程）

1. **测试与生产配置必须同源**：0.6.3 事故根因是 `Room.databaseBuilder` 手写迁移列表；现在有源码断言守卫。
2. **不在用户日常手机上装非正式签名包**：debug 覆盖安装会因签名不同触发卸载。已改为独立包名 + CI 模拟器验证。
3. **「做了没接线」是本项目主要缺陷形态**：新增字段/参数后必须同时检查①谁生产②谁消费③测试是否覆盖消费侧。
4. **国内 ROM 的权限是组合拳**：ColorOS 上锁屏响铃界面 = 电池白名单 + 悬浮窗 + 通知动作，缺一不可；应用内健康横幅负责提示。
