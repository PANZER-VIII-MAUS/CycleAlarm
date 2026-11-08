# 发版审核记录 · 0.6.12

> 依据 `docs/REVIEW-CHECKLIST.md`。审核对象 `v0.6.12`（versionCode 40）。审核时间 2026-11-08。
> 本版内容是 0.6.11 复查报告（5 Important + 1 Minor）的修复。

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.12 / 40 |
| 本次目标 | 修掉 0.6.11 复查的 5 Important + 1 Minor；Play lint 转绿并纳入 CI |
| 非目标 | 不加新功能、不改 DB 版本（仍 v11） |
| 验收条件（用户可观察） | ①恢复备份后旧主闹钟和旧提前提醒都不再出现 ②已删除闹钟的提前提醒不再发通知 ③设置页守护开关显示真实状态 ④play 包构建 lint 不再报错 ⑤备份里非法字段被拒 ⑥XLSX 25 张表不再被静默截断 |
| 是否碰过 Manifest / Room / R8 / 签名 | Manifest：守护服务声明移入 `src/direct`；Room/R8/签名未改 |
| 基线 / 发布 | `d4d555a`（v0.6.11）/ 本版提交（tag `v0.6.12`） |

## 1. 门禁表

| 门禁 | 状态 | 证据 |
| --- | --- | --- |
| 需求与验收条件 | 通过 | ①`cancelStoredAlarm` / `cancelStoredDateAlarm` 在事务后调用 ②`ACTION_PRE_ALERT` 先查父闹钟 ③`lastFailureAt` + `guard_on_failed` ④play lint 0 error ⑤`BackupValidator` 新增 5 类校验 + 6 例测试 ⑥`take(MAX_WORKSHEETS)` 统一 30 |
| 构建与 CI | 通过 | direct/play 各 **342** 单测 0 失败；`lintDirectDebug` 0 error / 11 warning、`lintPlayDebug` 0 error / 10 warning；双 flavor + AAB 构建成功；CI 已加入 `lintPlayDebug` |
| Android 设备验证 | 部分通过 | 0.6.12 覆盖安装启动正常；间接验证见 §3 |
| 代码目标与回归审核 | 通过 | 新增：备份校验 6 例；pre-alert 测试改为「先落父闹钟 + 等待异步校验」 |
| 安全审核 | 通过 | play 包彻底不含 `AlarmGuardService`（源码/清单/资源都不再打包）；未新增网络或导出面 |
| 发布清单与冷静期 | 缺失 | 直接 latest（单人自用期例外） |
| 最新基线复测 | 通过 | 全部基于本次提交 |
| 发布与回滚防护 | 就绪 | tag + digest；未升 DB，可回退 0.6.11 |

## 2. 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| direct / play 单测 | 342 / 342，0 失败（新增排期失败注入 2 例、守护状态 2 例、校验 6 例、XLSX 预算 1 例） |
| lint（direct / play） | 0 error / 11 warning；0 error / 10 warning |
| AAB | 构建成功；二进制断言：无 `AlarmGuardService`、有 `RingService` |
| versionCode 一致性 | build.gradle = 40 |

## 3. 设备验证

| 项 | 结果 |
| --- | --- |
| 覆盖升级 | ✅ 0.6.11 → 0.6.12，启动无崩溃（versionCode 40） |
| 守护服务开关 | ✅ direct 包开关存在；状态文案新增「已开启（但服务启动失败）」分支 |
| Play 包 | ⚠️ 未真机安装（本地只装 direct）；play 侧通过「源码/清单/资源都不含 guard」+ lint 0 error 保证 |

## 4. 五项复查遗留的验证结果（2026-11-08 补做）

| # | 项 | 验证方式 | 结果 |
| --- | --- | --- | --- |
| 1 | 恢复备份的端到端 | 真机：先加一条 07:00 闹钟并设 5 分钟提前提醒（FIRE=14、PRE_ALERT=2，含 origWhen 1794696900）→ 恢复今天 11:39 的自动备份 | ✅ 恢复后该闹钟的**主排期（1794716400）与提前提醒（1794696900）都从 `dumpsys alarm` 消失**，UI 列表中该闹钟也不存在，无崩溃 |
| 2 | Play flavor 真机 | 真机装 play release APK（同签名/同包名，数据保留） | ✅ 启动无崩溃、versionCode 40；设置页**没有「常驻保活服务」行**；APK dex 静态检查：direct 含 `AlarmGuardService`+`RingService`，**play 只有 `RingService`** |
| 3 | 逐条排期失败汇总 | 新增 `AlarmSchedulerRescheduleRobolectricTest`：注入 `id = MAX_ALARM_ID + 1` 的非法闹钟 | ✅ `attempted=2`、`failed=["cycle:<id>"]`、正常闹钟仍被排上（`isScheduled=true`） |
| 4 | XLSX 全局单元格预算 | 新增 `XlsxSheetReader(maxTotalCells = 0)` 测试（限制参数化） | ✅ 解析出任何单元格即抛 `SheetReadException` |
| 5 | 守护服务异步失败 | 真机尝试 `appops START_FOREGROUND deny` / 撤销 `FOREGROUND_SERVICE_SPECIAL_USE` / restricted 待机桶——**这台 ROM 全部拒绝或无效**，无法触发真实失败 | ⚠️ 改为状态机单测：`GuardServiceHealthRobolectricTest` 覆盖 recordFailure/clearFailure/lastFailureAt 与「失败不改变用户开关意图」；真机失败路径仍属未实际触发 |

## 5. 未验证项

1. **恢复备份端到端**：已补做并通过（见 §4.1）。
2. **Play flavor 真机**：已补做并通过（见 §4.2）。
3. **逐条排期失败汇总**：已补注入测试（见 §4.3）；未构造「scheduleAlarm 内部抛非 id 异常」的场景。
4. **XLSX 全局单元格预算**：已补参数化测试（见 §4.4）；未用真实超大样张。
5. **守护服务异步失败真实触发**：这台 ROM 不拦前台服务，只能靠状态机单测（见 §4.5）。

## 6. 安全明细

| 检查 | 结论 |
| --- | --- |
| 组件导出面 | ✅ 未变；play 包无守护服务 |
| PendingIntent / 签名 | ✅ 未变 |
| 敏感日志 | ✅ 无 |
| 系统备份 | ✅ `allowBackup=false` + dataExtractionRules 全排除（0.6.11 起） |

## 7. 最终结论

**需修改后复审（非阻断）**

一句话依据：复查的 5 个 Important 全部落地（旧 pre-alert 取消与父校验、真实排期摘要、守护状态回写、play lint 转绿 + CI、备份校验补强）与 1 个 Minor（XLSX 上限统一/单元格预算）；
剩余未验证项集中在真机端到端与 play flavor UI，不阻断侧载发布。
