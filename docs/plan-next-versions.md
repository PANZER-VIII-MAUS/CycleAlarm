# 后续执行顺序（0.6.2 → 0.6.4 → 上线前）

> 依据：`CycleAlarm 四轮复审报告（v0.2.7 → v0.6.1）`。
> 排序原则：**先修数据/真相源，再修响铃可靠性，再清 UI 与测试债，最后做上架工程**。
> 同一组内，**先做被其它修复依赖的共用改造**，再做点状修复。

## 已完成（0.6.2 第一批，commit cb7e7e2）

| # | 项 | 状态 |
|---|---|---|
| ✅ 1 | 备份格式 V2：A 记录补 v10 四字段 + 尾记录 sha256/条数（X1、X2） | 完成 |
| ✅ 2 | 导入快照 V3：补四字段，兼容 V1/V2（X1） | 完成 |
| ✅ 3 | 恢复备份：清 `import_snapshots` / `ring_events`、自动落恢复前备份、二次确认（X3） | 完成 |
| ✅ 4 | 覆盖导入传 `weeklyConfirmed`、完成页显示被跳过条目（H1） | 完成 |

---

## 0.6.2 · 数据真相源（继续，P0）

| 顺序 | 项 | 为什么在这个位置 | 涉及文件 |
|---|---|---|---|
| **5** | **抽 `EffectiveTriggerPolicy`**：跳过 / 临时静音 / 生效触发点，供排期、漏响判定、对账、首页「下次响铃」、桌面小组件共用（N1） | 本轮最重；后续「小组件/首页不认 skip」「对账误判 RESCHEDULE_LATER」都依赖它 | `core/SkipPolicy`、`TemporaryMuteStore`、`AlarmScheduler`、`ResyncAuditPolicy`、`AlarmResyncHelper`、`NextAlarmFinder`、`WidgetTextPolicy` |
| 6 | `markSelfWrite` 挪出 `dateAlarms.forEach` 循环（N2） | 1 行修复，但影响所有对账防抖 | `core/AlarmScheduler.kt` |
| 7 | `markRung` 移到 `RingService.startForeground` 成功后（N3）；`ackedAt` 接上真实停止路径（N4） | 漏响漏报 + 历史页全「未处理」，同一处改造 | `AlarmReceiver`、`RingService`、`RingActivity` |
| 8 | 周导入不再硬删 `dayIndex ≥ 7` 的手动闹钟（H2） | 与 0.3.0 的「保留数据、排期层过滤」同源 | `AlarmImportWriter` |
| 9 | `DatabaseSelfCheck` 移到 IO 线程；排期诊断在对齐正常时也写回「正常」（N5 两条） | 首启 ANR 风险 + 诊断永远显示旧异常 | `MainActivity`、`ReconcilePolicy`、`ResyncCheckpoint` |
| 10 | 收尾验证：259+ 单测、lint、装机冒烟（改铃声→备份往返、静音 20 分钟不误报、覆盖导入周课表） | 本版三条主诉的验收 | — |

## 0.6.3 · 响铃链路收紧

| 顺序 | 项 | 说明 |
|---|---|---|
| 11 | 自定义铃声失效回退系统铃声；`vibrate=false` 时停止上一条振动 | 最坏情况是「静音响铃」 |
| 12 | 跨午夜 snooze 的 `expectedAt` 按触发点所在日计算 | 23:55 → 00:05 场景 |
| 13 | 提前提醒：改用精确闹钟、关闭时取消已武装 PI、通知 id 按闹钟区分 | 三个独立小修 |
| 14 | 小组件：点「稍后/停止」后立即刷新；`BootReceiver` 尊重「常驻保活」开关 | 用户可见的陈旧状态 |
| 15 | `ring_events` 加唯一约束 + `snooze` 去重（**DB v11**：迁移 + schema 导出 + `DatabaseSelfCheck` 期望值 + 迁移测试一起改） | 涉及库结构，整块做一次 |
| 16 | `MissedAlarm` 带上 `alarmId`（同一时刻两条闹钟都漏响只报一次的问题） | 与 15 一起改数据模型 |
| 17 | `resyncAll(auditMissed=false)` 不推进 checkpoint | 避免吞掉待上报事件 |

## 0.6.4 · UI 与测试债

| 顺序 | 项 | 说明 |
|---|---|---|
| 18 | 主界面开关对比度 ≥ 3:1；日卡 `compact` 阈值改按**屏宽**判定（当前普通手机永远走三行） | 两块都是「看起来能用但不对」 |
| 19 | 撤销展示时序（`pendingUndo` 在 Snackbar 前被清空）；删安排类型时先关对话框 | 撤销窗口实际上点不到 |
| 20 | `RingActivity` 边到边 + 200% 字号不溢出 | targetSdk 36 强制边到边 |
| 21 | 读屏：liveRegion 从每分钟变化的倒计时移走；12h 模式补上午/下午 | 无障碍 |
| 22 | i18n 收尾：Manifest label、widget 布局文字、`BackupManager` require、导入警告、DB 自检细节、旧迁移类型名；VM/服务改用**应用内语言**取字符串；切语言后全栈重建 | 报告 §3.3 的最后一批中文残留 |
| 23 | 节假日按内置 key 去重（不按本地化 label）；`DateAlarmStatus` 补时间维度；小组件深色版 | 三个独立数据/显示问题 |
| 24 | 测试补强：迁移从 v4/v5 起点、v10 默认值断言、`DatabaseSelfCheck.EXPECTED` 与导出 schema 对齐、i18n key 集合一致性 | 报告 §5.3 盲区 |
| 25 | 清理拆分文件里每个 130+ 行的 import 复制（含未使用 import） | 可读性 |

## 上线前必修（0.6.4 之后）

| 顺序 | 项 |
|---|---|
| 26 | instrumentation 冒烟：AlarmManager → Receiver → Service → Activity 全链路 + R8 包 |
| 27 | API 35 / 36 真机回归（边到边、FGS、通知权限） |
| 28 | CI 归档 `mapping.txt`；产物命名区分「未签名」；`if-no-files-found: error` |
| 29 | 密钥口令迁出 `keystore.properties`（环境变量 / CI secret） |
| 30 | Play 上架包默认关闭常驻 `specialUse` 服务 |

## 关键依赖

```
5 (EffectiveTriggerPolicy)
 ├── 14 (小组件/首页认 skip)
 ├── 17 (checkpoint 推进策略)
 └── 16 (MissedAlarm 去重口径)
15 (DB v11) ── 24 (迁移测试以 v11 为终态)
7  (markRung/acked) ── 15/16 的历史页口径
```
