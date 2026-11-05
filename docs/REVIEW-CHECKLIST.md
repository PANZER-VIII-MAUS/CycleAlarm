# 发版审核清单（CycleAlarm 定制版）

> 用途：每次发版前跑一遍，**不新增功能**，只确认「该验证的验证过、没验证的写出来」。
> 预计耗时：30~60 分钟（含构建与真机冒烟）。
> 适用阶段：**侧载分发期**（单人开发 / 无服务端 / 无三方崩溃 SDK）。
> 上架 Play 前需额外补：Play 声明表单、隐私政策、Data safety、API 35/36 真机回归。
>
> 版本：与 `0.6.6`（flavor 拆分后）同步。每版发版记录见 `docs/release-audit-<版本>.md`。

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

**状态判定标准（比通用模板更严，因为单人项目没有第二双眼睛）**

- **通过** = 有产物或设备证据。只看代码不算。
- **未运行** = 没跑。**未验证** = 跑了但没覆盖到（如只在 API 34 跑过）。
- 空白格 = 不合格，必须写「未运行」而不是留空。

---

## 2. 构建与静态检查

> **flavor 拆分后**：`direct` = 个人自用（含常驻守护服务）；`play` = 上架包（manifest 删除该服务）。
> 聚合任务（`testDebugUnitTest` / `assembleRelease` / `lintDebug`）在 flavor 下不可用。

```powershell
cd D:\repos\CycleAlarm

# 1) 单测 + lint + debug 构建（两个 flavor 都跑）
.\gradlew.bat testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug assembleDirectDebug

# 2) release 构建（R8 / 资源压缩；direct 用于分发，play 用于上架核对）
.\gradlew.bat assembleDirectRelease assemblePlayRelease
```

- [ ] 单测全绿（对照 `app/build/test-results/testDirectDebugUnitTest/*.xml` 的 tests/failures，**不要只看 exit code**）
- [ ] lint **0 error**（warning 数量记下来，只允许下降或持平）
- [ ] `output-metadata.json` 的 versionCode 与 `build.gradle.kts` 一致
- [ ] `app/build/outputs/mapping/directRelease/mapping.txt` 已生成
- [ ] 若本次动了 Room：`addMigrations(*ALL_MIGRATIONS)` 未被改回手写列表（有源码断言测试守着）
- [ ] **play 变体合并 manifest 里没有 `AlarmGuardService`**（CI 有断言；本地可用
      `find app/build/intermediates -path '*play*' -name AndroidManifest.xml` 自查）

---

## 3. Android 设备验证（最小冒烟集）

> 没有 API 35/36 真机时，**允许只跑模拟器**，但必须在 §6 写明「API 35/36 真机未验证」。

```powershell
# instrumentation 冒烟（CI 已在 API 34 / 36 模拟器上跑；本地需连设备）
.\gradlew.bat connectedDirectDebugAndroidTest

# 关键 adb 检查
adb shell dumpsys alarm | Select-String "cyclealarm"
adb shell dumpsys deviceidle whitelist | Select-String "cyclealarm"
adb shell appops get com.cyclealarm.app SYSTEM_ALERT_WINDOW
adb shell appops get com.cyclealarm.app USE_FULL_SCREEN_INTENT
adb shell dumpsys deviceidle force-idle      # 模拟 Doze
adb shell dumpsys deviceidle unforce
```

- [ ] 冷启动进主界面不崩、首屏可交互
- [ ] 设置页 / 导入页 / 响铃历史页都能进出
- [ ] 设一条 1 分钟后的闹钟 → 响铃 → 停止（**真响一次**，不是看代码）
- [ ] **锁屏**状态下响铃 → 界面出现且按钮可点（ColorOS 需：电池白名单 + 悬浮窗权限）
- [ ] 关掉「闹钟响铃」通知渠道 → 再响一次 → 仍能停止
- [ ] 把系统时间往前拨 2 小时 → 打开 App → **不应弹「错过闹钟」**
- [ ] 主页健康横幅：故意关掉通知权限 → 横幅应出现并指向正确设置页
- [ ] 设置页「上次响铃」应显示本次走的是「全屏界面 / 悬浮窗兜底 / 仅通知」

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
- [ ] 响铃通知带「停止 + 稍后」两个动作（锁屏上无需解锁即可处理）

### 4.3 迁移与数据

- [ ] 本次是否新增 DB 版本？若是：
      - [ ] 迁移测试**走生产入口**（`AppDatabaseMigrationChainTest` 用 `AppDatabase.get()`）
      - [ ] 测了「混合态数据」（如一行 `rungAt` 有值、一行 NULL），不是只造方便构造的全 NULL
      - [ ] `SchemaDeltaTest` / `DatabaseSchemaContractTest` 覆盖到新版本
- [ ] 备份/恢复：本次是否动了 `BackupCodec` / `ImportSnapshotCodec`？
      - [ ] 若 `AlarmEntity` 加了字段，**备份与快照两处都同步**
      - [ ] 用**非默认值**（非空铃声、`vibrate=false`、`ramp=true`、`preAlert>0`）做往返测试
- [ ] 恢复备份后：`import_snapshots` 与 `ring_events` 已清、瞬时状态（skip/mute）已作废

### 4.4 权限与后台

- [ ] 精确闹钟权限被拒时：降级到 `set(RTC_WAKEUP)`、有可见提示、不反复对账重排
- [ ] FGS 被 ROM 拒绝时：不崩、仍响铃、有降级通知
- [ ] 通知权限被拒时：仍有可操作的停止入口
- [ ] 常驻保活开关关闭后，**开机不再拉起** `AlarmGuardService`
- [ ] **ColorOS 类 ROM 专项**：电池白名单 + 悬浮窗权限齐备时，锁屏响铃界面能显示；
      缺失时主页健康横幅能报出缺项（`PermissionHealthPolicy`）

---

## 5. 安全门禁（单独出结论，任一项不过即阻断）

| 检查 | 命令 / 位置 | 结论 |
| --- | --- | --- |
| 密钥文件未入库 | `git log --all -- keystore.properties`、`git ls-files \| findstr keystore` |  |
| 口令未明文提交 | `keystore.properties` 只含 `storeFile` / `keyAlias`；口令在 `CYCLEALARM_STORE_PASSWORD` 环境变量或 `~/.gradle/gradle.properties` 的 `cyclealarm.*` |  |
| 组件导出面 | `AndroidManifest.xml` 的 `exported`：只有 `MainActivity` 与 `BootReceiver` 应为 true |  |
| PendingIntent 可变更性 | 全仓 PendingIntent 均带 `FLAG_IMMUTABLE` |  |
| 敏感数据落盘 | 备份文件为明文（含备注），仅在用户主动导出时产生；README 需声明 |  |
| 敏感信息进日志 | 全仓 grep `Log.*(password\|token\|secret)` |  |
| 权限最小化 | `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `SYSTEM_ALERT_WINDOW` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 各自用途写进 README |  |
| 依赖漏洞 | `.\gradlew.bat dependencyUpdates`（若已配置）或人工核对新增依赖 |  |
| R8 不漏 keep | release 包实际启动一次；`mapping.txt` 已归档 |  |

> **硬规则**：`keystore.properties` 里有明文口令 → 直接阻断，不允许以「已有问题」放行。

---

## 6. 未验证项（**每次至少写 3 条**，写不出来说明没认真审）

> 本节不是形式主义。这个项目五轮里所有难查的 bug，都是「看起来完成了、但从未被真正验证」的：

- [ ] `____`
- [ ] `____`
- [ ] `____`

---

## 7. 发布与回滚防护

- [ ] 先发 **GitHub pre-release**，观察 1~3 天（自用 + 至少 1 位真实用户）
      - 单人自用期允许直接 latest，但**涉及 DB 版本升级的版本必须先 pre-release**
- [ ] 观察期无「响铃不响 / 数据丢失 / 卡死」类反馈，再转 latest
- [ ] **回滚手段写清楚**：
      - 侧载用户装回旧版 = 可以（但若是 DB 迁移方向，**旧版读不了新库**，等于数据丢失）
      - 因此：**涉及 DB 版本升级的版本，回滚手段实际是「不升级」，必须提前说明**
- [ ] `mapping.txt` 已归档到该 Release（CI 产物 `cyclealarm-r8-mapping`）
- [ ] 签名的 keystore 与口令**在本次发版前有可用的离线备份**
- [ ] 该版本 tag 已打，且 tag 上的 CI 重跑过一次（`on.push.tags` 已配置）

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
.\gradlew.bat testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug assembleDirectDebug assembleDirectRelease assemblePlayRelease

# 报告
start app\build\reports\tests\testDirectDebugUnitTest\index.html
start app\build\reports\lint-results-directDebug.html

# 签名校验（确认用的是正确证书）
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\direct\release\app-direct-release.apk

# 看当前排期 / 白名单 / 关键 appops
adb shell dumpsys alarm | Select-String "cyclealarm"
adb shell dumpsys deviceidle whitelist | Select-String "cyclealarm"
adb shell appops get com.cyclealarm.app SYSTEM_ALERT_WINDOW
adb shell appops get com.cyclealarm.app USE_FULL_SCREEN_INTENT

# 进 Doze / 退出
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# 清 App 数据重来（会丢本地数据，谨慎）
adb shell pm clear com.cyclealarm.app
```

## 附录 B：已知未闭环项（截至 0.6.6，发版前逐条确认是否仍存在）

| # | 项 | 状态 |
| --- | --- | --- |
| 1 | v11 迁移去重只留 `MIN(id)`、不合并 `rungAt/ackedAt` | 仍存在 |
| 2 | 唯一索引 + `IGNORE` → 重复触发补不上 `rungAt` | 仍存在（影响面小） |
| 3 | V1 老备份无校验和 | 仍存在 |
| 4 | 自动备份写在私有目录、无恢复入口 | ✅ 已修（每日自动备份 + 设置页「恢复自动备份」，真机验证） |
| 5 | 完成页 `skippedWeeklyCount` 未接线 | ✅ 0.6.5 已修 |
| 6 | 日期闹钟「跳过下一次」仍误报漏响 | 仍存在 |
| 7 | 跨夏令时 `-24h` 算错 | 仍存在 |
| 8 | 提前提醒改「关闭」后旧预告仍会弹一次 | 仍存在 |
| 9 | Play flavor 只改 BuildConfig、manifest 未删声明 | ✅ 已修（manifest 真删 + CI 断言） |
| 10 | instrumentation 从未跑成功 | ✅ 已修（CI API 34/36 各 3 用例通过） |
| 11 | 迁移护栏 `current = 11` 硬编码 + 缺 `@Before` 重置 | ✅ 已修（schema 推导 + 重置 + 源码断言） |
| 12 | ColorOS 上 `USE_FULL_SCREEN_INTENT` 被 appop 拒绝，FSI 不可依赖 | 已知；靠悬浮窗兜底（0.6.6） |
| 13 | ColorOS 对未进电池白名单的应用拦截跨锁屏窗口 | 已知；健康横幅引导加白名单（0.6.6） |
| 14 | ColorOS 屏蔽第三方应用 logcat | 已知；诊断落 App 内（设置页「上次响铃」） |
| 15 | 本地曾把签名口令明文放在 `keystore.properties` | ✅ 已迁到 `~/.gradle/gradle.properties`（本轮审核发现并修复） |

---

*本清单为单人/侧载阶段的裁剪版，来源于五轮代码复审的实践。上架 Play 前需按 Play 政策补：target API 声明、`USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `FOREGROUND_SERVICE_SPECIAL_USE` 三项用途说明、隐私政策与 Data safety 表单、API 35/36 真机回归。*
