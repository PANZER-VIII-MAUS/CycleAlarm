# 发版审核清单（CycleAlarm 定制版）

> 用途：每次发版前跑一遍，**不新增功能**，只确认「该验证的验证过、没验证的写出来」。
> 预计耗时：30~60 分钟（含构建与真机冒烟）。
> 适用阶段：**侧载分发期**（单人开发 / 无服务端 / 无三方崩溃 SDK）。
> 上架 Play 前需额外补：Play 声明表单、隐私政策、Data safety、API 35/36 真机回归。

---

## 0. 本次改动卡片（先填，填不满说明你自己都没搞清楚）

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode |  |
| 本次目标（一句话） |  |
| **非目标**（明确不做什么） |  |
| 验收条件（用户可观察） |  |
| 动了哪些目录 |  |
| 是否碰过以下任一项（勾选）：Manifest / Gradle / Room schema / R8 规则 / 签名 / CI |  |
| 基线 commit / tag |  |

> **规则**：验收条件必须写成「用户能看到什么」。写不出可观察结果的改动，不算完成。

---

## 1. 门禁表（8 格，每次都要填）

| 门禁 | 状态 | 证据（命令 / 文件 / 截图 / 设备） | 阻断项或后续动作 |
| --- | --- | --- | --- |
| 需求与验收条件 | 通过 / 未通过 / 信息不足 |  |  |
| 构建与 CI | 通过 / 失败 / 未运行 |  |  |
| Android 设备验证 | 通过 / 未通过 / 未验证 |  |  |
| 代码目标与回归审核 | 通过 / 需修改 |  |  |
| 安全审核 | 通过 / 阻断 / 未执行 |  |  |
| 发布清单与冷静期 | 满足 / 缺失 |  |  |
| 最新基线复测 | 通过 / 未执行 |  |  |
| 发布与回滚防护 | 就绪 / 未就绪 |  |  |

**状态判定标准（比原文更严，因为单人项目没有第二双眼睛）**

- **通过** = 有产物或设备证据。只看代码不算。
- **未运行** = 没跑。**未验证** = 跑了但没覆盖到（如只在 API 34 跑过）。
- 空白格 = 不合格，必须写「未运行」而不是留空。

---

## 2. 构建与静态检查

```powershell
cd D:\repos\CycleAlarm

# 1) 单测 + lint + debug 构建
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug

# 2) release 构建（验 R8 / 资源压缩 / 无签名路径）
.\gradlew.bat assembleRelease

# 3) Play 变体（若这次涉及上架包）
.\gradlew.bat assembleRelease -PplayBuild=true
```

- [ ] 单测全绿（对照 `app/build/reports/tests/testDebugUnitTest/index.html` 的 tests/failures 数，**不要只看 exit code**）
- [ ] lint **0 error**（warning 数量记下来，只允许下降或持平）
- [ ] release 构建成功，`output-metadata.json` 里的 versionCode 与 `build.gradle.kts` 一致
- [ ] `mapping.txt` 已生成（崩溃 retrace 必需）
- [ ] 若本次动了 Room：**`addMigrations(*ALL_MIGRATIONS)` 未被改回手写列表**

---

## 3. Android 设备验证（最小冒烟集）

> 没有 API 35/36 真机时，**允许只跑模拟器**，但必须在 §6 写明「API 35/36 真机未验证」。

```powershell
# 模拟器（API 34 与 36 各一次）
.\gradlew.bat connectedDebugAndroidTest
# 若无 androidTest 用例或装包失败，记「未验证」，不许记「通过」

# 关键 adb 检查
adb shell dumpsys alarm | Select-String "com.cyclealarm"
adb shell dumpsys deviceidle force-idle      # 模拟 Doze
adb shell dumpsys deviceidle unforce
```

- [ ] 冷启动进主界面不崩、首屏可交互
- [ ] 设置页 / 导入页 / 响铃历史页都能进出
- [ ] 设一条 1 分钟后的闹钟 → 响铃 → 停止（**真响一次**，不是看代码）
- [ ] 关掉「闹钟响铃」通知渠道 → 再响一次 → 仍能停止
- [ ] 把系统时间往前拨 2 小时 → 打开 App → **不应弹「错过闹钟」**
- [ ] 锁定屏幕 → 闹钟响 → 全屏页能出现、按钮可点

---

## 4. 闹钟类 App 专属必查项（本项目最容易出事的地方）

### 4.1 排期与调度的真相源

- [ ] 排期、漏响判定、对账、首页「下次响铃」、桌面小组件**五处都走 `EffectiveTriggerPolicy`**
      （新增调用点若直接调 `nextTriggerFor`，就是回归）
- [ ] 跳过 / 今天静音之后，首页与小组件的显示**立刻**变化（当前最长滞后 60 秒，属已知问题）
- [ ] 跳过**按日期闹钟**后，漏响审计不会把它报成「错过」（当前未修，属已知问题）

### 4.2 响铃与事件语义

- [ ] `rungAt` 只在**前台化成功之后**写；`startForeground` 失败时事件保持 `rungAt = null`
- [ ] `ackedAt` 在用户真正停止/稍后时写入（`onDestroy` 路径），响铃历史不全是「未处理」
- [ ] 连续两条闹钟：第二条界面显示正确、停止/稍后作用在**正确的那条**
- [ ] 「一直响」到 15 分钟上限会发「未被处理」通知，不是静默消失
- [ ] 自定义铃声失效时能回退（自定义 → 系统闹钟 → 提示音），不出现静音响铃

### 4.3 迁移与数据

- [ ] 本次是否新增 DB 版本？若是：
      - [ ] 迁移测试**走生产入口**（`AppDatabase.get()`），不是只用 `MigrationTestHelper`
      - [ ] 测了「混合态数据」（如一行 `rungAt` 有值、一行 NULL），不是只造方便构造的全 NULL
      - [ ] `SchemaDeltaTest` 覆盖到新版本
- [ ] 备份/恢复：本次是否动了 `BackupCodec` / `ImportSnapshotCodec`？
      - [ ] 若 `AlarmEntity` 加了字段，**备份与快照两处都同步**
      - [ ] 用**非默认值**（非空铃声、`vibrate=false`、`ramp=true`、`preAlert>0`）做往返测试
- [ ] 恢复备份后：`import_snapshots` 与 `ring_events` 已清、瞬时状态（skip/mute）已作废

### 4.4 权限与后台

- [ ] 精确闹钟权限被拒时：降级到 `set(RTC_WAKEUP)`、有可见提示、不反复对账重排
- [ ] FGS 被 ROM 拒绝时：不崩、仍响铃、有降级通知
- [ ] 通知权限被拒时：仍有可操作的停止入口
- [ ] 常驻保活开关关闭后，**开机不再拉起** `AlarmGuardService`

---

## 5. 安全门禁（单独出结论，任一项不过即阻断）

| 检查 | 命令 / 位置 | 结论 |
| --- | --- | --- |
| 密钥文件未入库 | `git log --all -- keystore.properties`、`git ls-files \| findstr keystore` |  |
| 口令未明文提交 | `keystore.properties` 只含 `storeFile` / `keyAlias`；口令走 `CYCLEALARM_STORE_PASSWORD` 环境变量 |  |
| 组件导出面 | `AndroidManifest.xml` 的 `exported`：只有 `MainActivity` 与 `BootReceiver` 应为 true |  |
| PendingIntent 可变更性 | 全仓 PendingIntent 均带 `FLAG_IMMUTABLE`（或明确的 mutability 决策） |  |
| 敏感数据落盘 | 备份文件为明文（含备注），仅在用户主动导出时产生；README 需声明 |  |
| 敏感信息进日志 | 全仓 grep `Log.*(password\|token\|uri)` |  |
| 权限最小化 | `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `SYSTEM_ALERT_WINDOW` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 各自用途写进 README |  |
| 依赖漏洞 | `.\gradlew.bat dependencyUpdates`（若已配置）或人工核对新增依赖 |  |
| R8 不漏 keep | release 包实际启动一次；`mapping.txt` 已归档 |  |

> **硬规则**：`keystore.properties` 里有明文口令 → 直接阻断，不允许以「已有问题」放行。

---

## 6. 未验证项（**每次至少写 3 条**，写不出来说明没认真审）

> 本节不是形式主义。这个项目五轮里所有难查的 bug，都是「看起来完成了、但从未被真正验证」的：

- [ ] `____`（例：API 35/36 真机未验证，只有 API 36 模拟器）
- [ ] `____`（例：instrumentation 用例在本机 ColorOS 上装包失败，3 条 smoke 未跑成）
- [ ] `____`（例：Play 变体只构建未安装，manifest 差异未核对）

---

## 7. 发布与回滚防护

- [ ] 先发 **GitHub pre-release**，观察 1~3 天（自用 + 至少 1 位真实用户）
- [ ] 观察期无「响铃不响 / 数据丢失 / 卡死」类反馈，再转 latest
- [ ] **回滚手段写清楚**（不允许写「重新上传旧 APK」了事）：
      - 侧载用户装回旧版 = 可以（但若是 DB 迁移方向，**旧版读不了新库**，等于数据丢失）
      - 因此：**涉及 DB 版本升级的版本，回滚手段实际是「不升级」，必须提前说明**
- [ ] `mapping.txt` 已归档到该 Release（崩溃可 retrace）
- [ ] 签名的 keystore 与口令**在本次发版前有可用的离线备份**
- [ ] 该版本 tag 已打，且 tag 上的 CI 重跑过一次（不是只在旧基线上绿过）

---

## 8. 最终结论（三选一，不许含糊）

| 结论 | 适用条件 |
| --- | --- |
| **可发布** | 8 格门禁全绿；未验证项里没有「影响响铃正确性 / 数据完整性」的条目；安全门禁通过 |
| **需修改后复审** | 有 1~2 条非阻断项待修，修完重跑本清单 |
| **阻断** | 安全项未关闭 / 数据迁移无生产路径测试 / 存在「响了但不响」类回归 |

一句话依据：`________________________________________`

---

## 附录 A：常用命令速查

```powershell
# 全量门禁（本地等价于 CI 的 build-debug job）
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease

# 单测报告 / lint 报告
start app\build\reports\tests\testDebugUnitTest\index.html
start app\build\reports\lint-results-debug.html

# 看当前排期
adb shell dumpsys alarm | Select-String "cyclealarm"

# 看下一次系统闹钟
adb shell dumpsys alarm | Select-String "nextAlarmClock"

# 进 Doze / 退出
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# 清 App 数据重来（会丢本地数据，谨慎）
adb shell pm clear com.cyclealarm.app
```

## 附录 B：截至 0.6.5 的已知未闭环项（发版前逐条确认是否仍存在）

> 这份清单来自五轮复审（0.0.7 → 0.6.5），**不是待办列表**，而是「发版前如果这些还在，就要在 Release Notes 里说明或先修掉」的清单。

| # | 项 | 影响 |
| --- | --- | --- |
| 1 | v11 迁移去重只留 `MIN(id)`、不合并 `rungAt/ackedAt` | 历史页可能把「响过的」显示成漏响 |
| 2 | 唯一索引 + `IGNORE` → 重复触发补不上 `rungAt` | 同上，特定链路 |
| 3 | V1 老备份无校验和 | 截断的 V1 备份能「部分恢复」 |
| 4 | 自动备份写在私有目录、无恢复入口 | 用户实际救不回来 |
| 5 | 完成页 `skippedWeeklyCount` 未接线 | 静默跳过时无提示 |
| 6 | 日期闹钟「跳过下一次」仍误报漏响 | 用户明确跳过却被指责漏响 |
| 7 | 跨夏令时 `-24h` 算错 | 少数地区少数日期 |
| 8 | 提前提醒改「关闭」后旧预告仍会弹一次 | 一次误响 |
| 9 | Play flavor 只改 BuildConfig、manifest 未删声明 | 上架审核风险未降 |
| 10 | instrumentation 从未跑成功 | 端到端零验证 |
| 11 | 迁移护栏 `current = 11` 硬编码 + 缺 `@Before` 重置 | 将来静默失效 |

---

*本清单为单人/侧载阶段的裁剪版，来源于五轮代码复审的实践。上架 Play 前需按 Play 政策补：target API 声明、`USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `FOREGROUND_SERVICE_SPECIAL_USE` 三项用途说明、隐私政策与 Data safety 表单、API 35/36 真机回归。*
