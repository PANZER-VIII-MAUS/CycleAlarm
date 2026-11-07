# 发版审核记录 · 0.6.9

> 依据 `docs/REVIEW-CHECKLIST.md`。审核对象 `v0.6.9`（versionCode 37）。审核时间 2026-11-07。
> 本版内容是六轮复审 §8.1 的「修复的修复」。

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.9 / 37 |
| 本次目标 | 修掉六轮复审指出的「动作做对但方向做反」与两处危险行为 |
| 非目标 | 不加新功能、不改 DB 版本（仍 v11）、不上架 |
| 验收条件（用户可观察） | ①恢复备份后每日备份仍是 5 份 ②130% 字号下主时间比默认大 ③设置页可开关「备份到系统下载目录」④连删两条后第二条的撤销可用 ⑤浮窗失败不再显示「兜底成功」 |
| 动了哪些目录 | `data/`（备份目录/下载副本/DAO）、`core/`（策略/语言/渠道）、`service/`、`ui/`（日卡/横幅/设置）、`app/build.gradle.kts`（AAB）、`.github/workflows` |
| 是否碰过 Manifest / R8 / 签名 | 均未改；Gradle 仅加 `bundle.language.enableSplit=false` |
| 基线 / 发布 | `9bf9b1a` / 本版提交（tag `v0.6.9`） |

## 1. 门禁表

| 门禁 | 状态 | 证据 |
| --- | --- | --- |
| 需求与验收条件 | 通过 | ①`expiredByPrefix` 分前缀测试 ②日卡改 `displaySmall`(36sp > 30sp) ③设置页开关 + 默认关 ④`clearPendingUndo(expected)` ⑤`show()` 返回 Boolean 且按结果记账 |
| 构建与 CI | 通过 | direct/play 各 **315** 单测 0 失败；lint 0 error / 13 warning；`assembleDirectRelease` / `assemblePlayRelease` / **`bundlePlayRelease`** 成功（AAB 3.97 MB）；CI 已加 AAB 与 play 产物归档 |
| Android 设备验证 | 部分通过 | 本地构建/测试全绿；**装机冒烟待手机连接后补做**（见未验证项） |
| 代码目标与回归审核 | 通过 | 新增/扩展：分前缀保留 2 例、电池非阻断 2 例、`clearAll` 1 例、v11 schema 2 例、plurals key 一致性 |
| 安全审核 | 通过 | 下载副本改为**默认关闭**并在 README 隐私段声明；密钥/口令未入库；PendingIntent 全 IMMUTABLE |
| 发布清单与冷静期 | 缺失 | 直接 latest（单人自用期例外） |
| 最新基线复测 | 通过 | 全部基于本次提交 |
| 发布与回滚防护 | 就绪 | tag + digest 记录；未升 DB，可回退 0.6.8 |

## 2. 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| direct / play 单测 | 315 / 315，0 失败 |
| AAB | `bundlePlayRelease` 产出 3.97 MB |
| lint | 0 error / 13 warning（`AppBundleLocaleChanges` 已被 language split 关闭消除） |
| 产物 | direct APK、play APK、play AAB（3,976,650 B） |
| versionCode 一致性 | build.gradle = 37 |

## 3. 设备验证

| 项 | 结果 |
| --- | --- |
| 覆盖升级安装 | 见发布时记录 |
| 启动 | 见发布时记录 |

## 4. 未验证项

1. **锁屏三态未复测**（锁屏息屏 / 锁屏亮屏 / 已解锁）：六轮复审 §4.3 认为浮窗在锁屏态可能仍被 keyguard 挡住，
   本版只修了「失败不能假装成功」与跨闹钟残留，未改窗口类型策略。
2. **130% 字号视觉未复核**：只有字号层级推演（36sp × 1.3 = 46.8sp > 30sp），未真机截图比对。
3. **AAB 未做 Play 上传验证**：只有 `bundlePlayRelease` 构建产物。
4. **下载副本开关未真机验证**（默认关，需手动开启后观察 `Download/CycleAlarm/`）。
5. **UI 债改动未做 130%/200% 字号与宽屏截图比对**（布局改动只有编译与 lint 保证）。

## 5. 安全明细

| 检查 | 结论 |
| --- | --- |
| 公共目录写入 | ✅ 改为用户显式开启（默认关），保留 7 份，查重过滤 `IS_PENDING=0` |
| 组件导出面 | ✅ 未变 |
| PendingIntent | ✅ 全 IMMUTABLE |
| 敏感日志 | ✅ 无 |
| 签名 | ✅ 未改 |

## 6. 最终结论

**需修改后复审（非阻断）**

一句话依据：六轮复审的两处危险行为（备份互删、静默写公共目录）与三项「假修复」都有代码与单测证据；
剩余 4 条未验证项属真机环境覆盖，不阻断侧载发布；下次发版前优先补「锁屏三态」真机验收。
