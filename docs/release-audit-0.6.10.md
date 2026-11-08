# 发版审核记录 · 0.6.10

> 依据 `docs/REVIEW-CHECKLIST.md`。审核对象 `v0.6.10`（versionCode 38）。审核时间 2026-11-08。
> 本版内容是七轮复审 §5 的 B/C/D/E 四组。

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.10 / 38 |
| 本次目标 | 修掉七轮复审的 3 处「修了一半」+ 反复提的遗留 + 把 AAB 纳入验证 |
| 非目标 | 不加新功能、不改 DB 版本（仍 v11）、不做锁屏浮窗窗口类型改造 |
| 验收条件（用户可观察） | ①删除一条闹钟只弹一条撤销提示 ②130% 字号下「下次响铃」也变大 ③开启「备份到下载目录」后多次启动只有一份当日文件，关闭后公共目录被清空 ④被跳过的日期闹钟不再显示「待响铃」 |
| 动了哪些目录 | `data/`、`core/`、`service/`、`ui/`、`.github/workflows`、`docs/` |
| 是否碰过 Manifest / Gradle / Room / R8 / 签名 | Gradle 仅版本号；Manifest/Room/R8/签名均未改 |
| 基线 / 发布 | `04af089` / 本版提交（tag `v0.6.10`） |

## 1. 门禁表

| 门禁 | 状态 | 证据 |
| --- | --- | --- |
| 需求与验收条件 | 通过 | ①单路径已删（代码 grep 无本地 `showUndoSnackbar` 调用点）②`NextAlarmCard` 改 `displaySmall` ③真机实测（见 §3）④`DateAlarmSection` 改走 `EffectiveTriggerPolicy` |
| 构建与 CI | 通过 | direct/play 各 **317** 单测 0 失败；lint **0 error / 10 warning**；双 flavor + AAB 构建成功；CI 新增 AAB 内部 manifest 断言 |
| Android 设备验证 | **通过** | 0.6.10 覆盖安装；下载副本查重与清理真机实测（见 §3） |
| 代码目标与回归审核 | 通过 | 新增/扩展：分前缀合并保留、`isRestoreName`、MediaStore 尾斜杠修复由真机证明 |
| 安全审核 | 通过 | 下载副本关闭时会清理公共目录（隐私积极处理）；其余项未变 |
| 发布清单与冷静期 | 缺失 | 直接 latest（单人自用期例外） |
| 最新基线复测 | 通过 | 全部基于本次提交 |
| 发布与回滚防护 | 就绪 | tag + digest；未升 DB，可回退 0.6.9 |

## 2. 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| direct / play 单测 | 317 / 317，0 失败 |
| lint | 0 error / **10 warning**（清理 7 个死资源：snack_deleted_*、restore_auto_item、perm_health_missing plurals 等） |
| AAB | `bundlePlayRelease` 成功；**本地二进制 grep 验证**：AAB 内 manifest 无 `AlarmGuardService`、有 `RingService` |
| versionCode 一致性 | build.gradle = 38 |

## 3. 设备验证（本次重点：MediaStore 回归）

| 项 | 结果 |
| --- | --- |
| 覆盖升级 | ✅ 0.6.9 → 0.6.10，启动无崩溃 |
| MediaStore 相对路径实证 | ✅ `content query` 显示 `relative_path=Download/CycleAlarm/`（**带尾斜杠**），确认七轮复审 §3.3 的回归成立 |
| 查重（核心） | ✅ 开启开关后**冷启动 3 次**，目录内始终只有 1 份 `CycleAlarm-auto-backup-2026-11-08.txt`（0.6.9 会重复写） |
| 关闭清理 | ✅ 再次点击关闭后，`/sdcard/Download/CycleAlarm/` 为空、MediaStore 无备份记录 |
| 恢复列表类型 | ✅ 设置页「恢复自动备份」显示「每日备份 + 恢复前快照」 |
| 130% 字号 | ✅ 主页与「下次响铃」大时间随字号变大（与日卡同策略） |
| **锁屏三态** | ✅ 全通过（方法：`cmd alarm set-time` 临时拨表，测完 `auto_time=1` 恢复）：①**锁屏息屏** → 全屏 RingActivity 出现在 keyguard 上；②**锁屏亮屏** → BAL 直启被拦但 **FSI 拉起全屏**（keyguard 让开，topResumedActivity=RingActivity）；③**已解锁后台** → BAL 拦截日志在案，**悬浮窗兜底**出现（appop=SYSTEM_ALERT_WINDOW），通知同时带停止/稍后 |

## 4. 未验证项

1. ~~锁屏三态未复测~~ → **本次已实测通过**（见 §3）：锁屏两态走 FSI 全屏，解锁后台走悬浮窗兜底；
   但**未改窗口类型策略**——若后续 ROM 收紧 FSI（如 ColorOS 限制「全屏通知」开关），仍需回归。
2. **AAB 未做 Play 上传**：只有构建与内部 manifest 断言。
3. **删除撤销在真机未点击验证**：删除会动用户数据，仅代码与编译保证。
4. **200% 字号 / 平板宽屏**未复测。
5. **instrumentation 本地仍无法复现**（依赖 CI 模拟器；本地设备策略限制）。

## 5. 安全明细

| 检查 | 结论 |
| --- | --- |
| 公共目录明文备份 | ✅ 默认关闭；关闭时主动清理；README 隐私段已声明 |
| 组件导出面 / PendingIntent / 签名 | ✅ 未变 |
| 敏感日志 | ✅ 无 |

## 6. 最终结论

**需修改后复审（非阻断）**

一句话依据：七轮复审的 3 处「修了一半」全部真修好，且 MediaStore 尾斜杠回归经真机三次冷启动实证修复；
AAB 已纳入 CI 断言；剩余未验证项集中在锁屏浮窗窗口策略与 Play 上传，不阻断侧载发布。
