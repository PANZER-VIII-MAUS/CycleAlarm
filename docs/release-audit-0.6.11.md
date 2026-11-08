# 发版审核记录 · 0.6.11

> 依据 `docs/REVIEW-CHECKLIST.md`。审核对象 `v0.6.11`（versionCode 39）。审核时间 2026-11-08。
> 本版内容是 0.6.10 代码审查（7 项问题）的修复。

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.11 / 39 |
| 本次目标 | 修掉审查的 1 Critical + 4 Important + 1 Minor，并引入对应回归测试 |
| 非目标 | 不新增功能、不改 DB 版本（仍 v11） |
| 验收条件（用户可观察） | ①恢复备份后旧「稍后提醒」不再响 ②play 包设置页看不到「常驻保活」 ③异常 XLSX 会被拒绝而不是 OOM ④非法备份在动库前被拒绝 ⑤`allowBackup=false` ⑥超 2MB 备份提示「文件过大」 |
| 动了哪些目录 | `data/`（BackupManager/BackupValidator）、`core/`（AlarmScheduler/GuardServicePolicy）、`importer/xlsx`、`ui/settings`、`receiver/`、Manifest、`res/xml`、`build.gradle.kts` |
| 是否碰过 Room / R8 / 签名 | Room 未改（v11 不变）、R8 未改、签名未改 |
| 基线 / 发布 | `3b4446a` / 本版提交（tag `v0.6.11`） |

## 1. 门禁表

| 门禁 | 状态 | 证据 |
| --- | --- | --- |
| 需求与验收条件 | 通过 | ①`cancelStoredSnooze` + 2 例 Robolectric 回归 ②`GUARD_SERVICE_AVAILABLE` 能力位 + UI 隐藏 + 启动异常回滚 ③`MAX_TOTAL_UNCOMPRESSED_BYTES`/`MAX_WORKSHEETS` + 2 例测试 ④`BackupValidator` 10 例测试 ⑤Manifest `allowBackup=false` ⑥分支拆分修正 |
| 构建与 CI | 通过 | direct/play 各 **331** 单测 0 失败；lint 0 error / 10 warning；双 flavor + AAB 构建成功 |
| Android 设备验证 | 部分通过 | 0.6.11 覆盖安装启动正常；**play flavor 未装真机**（设置页隐藏逻辑只有编译保证） |
| 代码目标与回归审核 | 通过 | 新增测试 14 例（备份校验 10、XLSX 2、snooze 取消 2） |
| 安全审核 | 通过 | `allowBackup=false` + `dataExtractionRules` 全排除；其余未变 |
| 发布清单与冷静期 | 缺失 | 直接 latest（单人自用期例外） |
| 最新基线复测 | 通过 | 全部基于本次提交 |
| 发布与回滚防护 | 就绪 | tag + digest；未升 DB，可回退 0.6.10 |

## 2. 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| direct / play 单测 | 331 / 331，0 失败 |
| lint | 0 error / 10 warning（`DataExtractionRules` 已通过 `res/xml/data_extraction_rules.xml` 消除） |
| AAB | `bundlePlayRelease` 成功 |
| versionCode 一致性 | build.gradle = 39 |

## 3. 设备验证

| 项 | 结果 |
| --- | --- |
| 覆盖升级 | ✅ 0.6.10 → 0.6.11，启动无崩溃 |
| 版本号 | ✅ 0.6.11（versionCode 39） |
| 数据保留 | ✅ 闹钟/设置/响铃历史均在 |
| 守护服务开关 | ⚠️ direct 包未逐一截图（代码路径与 flavor 位已核对） |

## 4. 未验证项

1. **旧 snooze 取消只有单测 + 代码路径**：未做「真机恢复备份后等旧 snooze 到点」的端到端验证（耗时且会污染用户数据）。
2. **play flavor 设置页隐藏保活开关**：未在真机安装 play 包验证（本地只装 direct；CI 只跑构建与 manifest 断言）。
3. **XLSX 聚合限制**：只有构造 ZIP 的单测（40 表、33MB 解压），未用真实恶意样张验证。
4. **`BackupValidator` 的跨表引用**只校验 `dayPlans.typeId`；snooze 的父闹钟存在性仍由恢复后的 `resyncAll` 兜底删除（设计如此，避免拒绝旧版本备份）。

## 5. 安全明细

| 检查 | 结论 |
| --- | --- |
| 系统备份面 | ✅ `allowBackup=false` + `dataExtractionRules` 全排除 |
| 组件导出面 / PendingIntent / 签名 | ✅ 未变 |
| 敏感日志 | ✅ 无 |

## 6. 最终结论

**需修改后复审（非阻断）**

一句话依据：审查的 P0（旧 snooze）与 P0/P1（play 保活开关）都已修复并有测试；P1 的 XLSX/备份校验/allowBackup 与 P2 提示分支全部落地；
剩余未验证项集中在真机端到端与 play flavor UI，不阻断侧载发布。
