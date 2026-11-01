# CycleAlarm 后续执行顺序

> 依据：五轮复审报告（2026-11-01，基线 0.6.5）。排序原则：**先修「假完成/死代码」，再补数据安全网，最后 UI 与文档**。
> 每条都带「怎么验证」，下次继续时按顺序往下做即可。

## 当前状态（2026-11-01 收工时）

| 项 | 值 |
|---|---|
| 版本 | 0.6.5（versionCode 33）已发布；仓库 main 已包含 0.6.6 第一批修复（未发版） |
| 数据库 | v11，迁移链 4→5→6→7→8→9→10→11 |
| 单测 | 279 × 2 flavor（direct + play）= 558 全绿 |
| lint | 0 error |
| CI | build-debug + instrumentation（API 34 / API 36 模拟器）双 job |
| flavor | `direct`（个人自用，含常驻守护服务）/ `play`（manifest 删除该服务） |

## 已完成（0.6.6 第一批，commit 36affcc）

| # | 项 | 验证方式 |
|---|---|---|
| ✅ 1 | Play flavor 在 manifest 层真删 `AlarmGuardService`（`tools:node="remove"`） | `find app/build/intermediates -path '*playRelease*' -name AndroidManifest.xml` 后确认无该服务声明、仍有 `RingService` |
| ✅ 2 | CI 改 flavor 明确任务 + play manifest 断言 + instrumentation 真跑报告 | CI run 77 的 `instrumentation (34)/(36)` |
| ✅ 3 | 迁移护栏：版本从 schema 推导、`@Before` 重置单例、builder 必须 `addMigrations(*ALL_MIGRATIONS)` 源码断言 | `AppDatabaseMigrationChainTest` |
| ✅ 4 | `skippedWeeklyCount` 接进完成页（原死代码） | 覆盖导入未切每周时，完成页显示红字提示 |

## 待做（按顺序）

### 第二批：数据安全网（§4.2–§4.5）

| # | 项 | 怎么验证 |
|---|---|---|
| 5 | V1 老备份（无校验和）解码时给出「未校验」提示或要求二次确认，避免把截断文件当成功 | 新增单测：V1 payload 缺行 → decode 仍成功但带 `unverified` 标记；UI 提示 |
| 6 | v11 去重补边界：同一 (kind, alarmId, expectedAt) 里 `rungAt` 一条非空、一条为空 | Robolectric 迁移测试补一例 |
| 7 | 撤销 token 语义：连删两条后点第二条的撤销，应只撤销第二条（当前静默失效） | UI 自动化/单测：`PendingUndo` 带 token，Snackbar 关闭只清对应 token |
| 8 | 自动备份「有产出没入口」：`BackupManager` 写入 `filesDir/auto-backups` 但没有任何恢复入口 | 设置页加「恢复最近自动备份」入口 + 单测 |
| 9 | 备份安全网：每日自动导出到 `Download/CycleAlarm/`（MediaStore，免权限），保留最近 5 份 | Robolectric/真机：安装后第二天出现文件；文档写明路径 |

### 第三批：调度残余（§4.6）

| # | 项 | 怎么验证 |
|---|---|---|
| 10 | DST/跨时区：`expectedMillisOfOriginal` 用减 24h 在夏令时切换日会错 1 小时 | 单测用 `America/New_York` 3 月/11 月切换日 |
| 11 | `RingEventDao.find()` 写好了没人用 → 接到事件式漏响判定 | 单测 + 调用点 |
| 12 | `BackupCodec.SCHEMA_VERSION=10` 只写不校验 → 解码时校验并给版本提示 | 单测：写 v11 文件、读时断言 |
| 13 | 重启/改时区后 `resyncAll(auditMissed=false)` 的检查点推进边界 | 单测覆盖 `lastCheckAt` 不变 |

### 第四批：UI 与 i18n（§4.7–§4.8）

| # | 项 | 怎么验证 |
|---|---|---|
| 14 | 日卡 130% 字号下时间字号反而变小（阈值 `>1.25f` 才降级） | 手动 130%/150% 截图对比 |
| 15 | 通知/服务文案仍跟随系统语言（`getString` 直接用 application context） | 应用内切英文、系统中文，触发通知看语言 |
| 16 | 旧迁移里的类型名仍是中文（`上课/自习/休息` 存库） | 迁移 SQL 用 key 而非文案，或首次启动按语言重写 |
| 17 | `values-night` 只有小组件颜色，主题色仍走 Compose 令牌 | 视觉检查深色模式 |

### 第五批：文档与发布（§4.9 + 0.6.6 发布）

| # | 项 | 说明 |
|---|---|---|
| 18 | README/docs 版本号、测试数、flavor 路径全量同步 | 发布前用 `grep` 核对 |
| 19 | 发布 0.6.6：`assembleDirectRelease`（产物 `apk/direct/release/app-direct-release.apk`） | 打 tag + GitHub Release + 上传 |
| 20 | 2027 法定节假日数据（等国务院公布，通常 11 月底） | `ChinaHolidaysProvider.official` 追加 + 小版本 |
| 21 | 真机 API 35/36 回归（手机升 ColorOS 16 = Android 16 后） | 全链路手测 |

## 发布命令（flavor 拆分后）

```powershell
# 单测与 lint
.\gradlew.bat testDirectDebugUnitTest lintDirectDebug

# 打印建（个人自用，含常驻守护服务）
.\gradlew.bat assembleDirectRelease
# 产物：app/build/outputs/apk/direct/release/app-direct-release.apk

# 上架包（manifest 无守护服务）
.\gradlew.bat assemblePlayRelease
# 产物：app/build/outputs/apk/play/release/app-play-release.apk

# 真机冒烟（debug 包独立包名，可与正式包共存）
.\gradlew.bat connectedDirectDebugAndroidTest
```

## 教训（写进流程，避免重犯）

1. **测试与生产配置必须同源**：0.6.3 事故根因是 builder 手写迁移列表，测试却用 `ALL_MIGRATIONS`。已有源码断言兜底。
2. **不在用户日常手机上安装非正式签名包**：debug 覆盖安装会因签名不同触发卸载，数据全失。已改为 `com.cyclealarm.app.debug` 独立包名 + CI 模拟器。
3. **「做了没接线」是本项目主要缺陷形态**：新增字段/参数后，必须同时检查①谁生产②谁消费③测试是否覆盖消费侧。
