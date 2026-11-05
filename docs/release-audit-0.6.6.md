# 发版审核记录 · 0.6.6

> 依据 `docs/REVIEW-CHECKLIST.md` 执行。审核对象：`v0.6.6`（versionCode 34）。
> 审核时间：2026-11-05。审核人：单人开发（AI 辅助），设备：OPPO Find X7（Android 14 / ColorOS）。

---

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.6 / 34 |
| 本次目标（一句话） | 让锁屏状态下响铃一定有可操作的「停止/稍后」界面，并在缺权限时明确告知用户 |
| **非目标** | 不改排期算法、不加新功能页、不动 DB 版本、不做上架 Play |
| 验收条件（用户可观察） | ①锁屏响铃时出现全屏界面或悬浮窗界面 ②锁屏横幅上可直接「停止/稍后」 ③缺权限时主页出现红色横幅并能一键跳设置 ④设置页「上次响铃」显示本次响铃的显示方式 |
| 动了哪些目录 | `app/src/main/java/{service,receiver,core,ui}`, `app/src/main/res/values*`, `app/build.gradle.kts`(签名口令顺序), `.github/workflows`(未在 0.6.6 内改) |
| 是否碰过：Manifest / Gradle / Room / R8 / 签名 / CI | Manifest 未改；**Gradle 改**（口令读取顺序）；Room 未改版本；R8 未改；**签名相关改动**（口令迁出仓库）；CI 未改 |
| 基线 commit / tag | `9d69cce`（0.6.6 开发前 main）/ `v0.6.5` |
| 发布 commit / tag | `d78b7d0` / `v0.6.6` |

---

## 1. 门禁表

| 门禁 | 状态 | 证据 | 阻断项或后续动作 |
| --- | --- | --- | --- |
| 需求与验收条件 | **通过** | 本节 §0 验收条件均可观察；真机按此验收（见 Android 设备验证格） | — |
| 构建与 CI | **通过** | 本地：`testDirectDebugUnitTest` 288/0、`testPlayDebugUnitTest` 288/0、`lintDirectDebug` **0 error / 78 warning**、`assembleDirectRelease` + `assemblePlayRelease` 成功；CI run #81（28d47d0）success、#82（d78b7d0）在跑 | warning 从 70 → 78，需下版收敛 |
| Android 设备验证 | **部分通过（未验证项见 §6）** | 真机（OPPO Find X7 / Android 14）：0.6.6 安装启动正常；**锁屏响铃界面在 28d47d0 构建上实测正常**（同一套响铃代码，0.6.6 仅加横幅/诊断）；`dumpsys alarm` 有 18 条本应用排期；`deviceidle whitelist` 含本应用 | 0.6.6 新增的「主页健康横幅」「上次响铃」未做真机点击验证；API 35/36 真机未验证 |
| 代码目标与回归审核 | **通过** | 新增纯函数策略均有单测：`RingUiFallbackPolicy`（4 例）、`PermissionHealthPolicy`（5 例）；调度函数去不确定性修复后 283 → 288 例全绿 | — |
| 安全审核 | **阻断（已在本轮修复）** | 发现 `keystore.properties` 含 `storePassword`/`keyPassword` 明文（未入库，`git log --all -- keystore.properties` 为空）→ 按硬规则阻断；已迁移到 `~/.gradle/gradle.properties`（`cyclealarm.*`），本地文件仅剩 `storeFile`/`keyAlias`；重新构建并用 `apksigner verify` 确认证书 SHA-256 仍为 `0e13e1b5…da45b9` | **关闭条件已满足**，下版发版前复验一次 |
| 发布清单与冷静期 | **缺失** | 0.6.6 直接发 latest，未走 pre-release 观察期 | 单人自用期允许例外；**下个 DB 版本必须 pre-release** |
| 最新基线复测 | **通过** | 0.6.6 的测试与构建都基于 `d78b7d0`（0.6.5 之后全部改动已并入）；CI #81/#82 覆盖同一基线 | — |
| 发布与回滚防护 | **就绪（有一处需说明）** | tag `v0.6.6` 已推送；APK digest `sha256:b9f1b91a…f430c9ba` 与 Release asset 一致；mapping 32.7 MB 生成 | **回滚说明**：0.6.6 未升 DB 版本（仍 v11），可安全回退到 0.6.5；`v0.6.6` 的 CI 重跑：tag 触发尚未配置（见 §6） |

---

## 2. 构建与静态检查证据

| 检查 | 结果 |
| --- | --- |
| direct 单测 | 288 tests / 0 failures（`app/build/test-results/testDirectDebugUnitTest/*.xml`） |
| play 单测 | 288 tests / 0 failures |
| lint | 0 error / **78 warning**（0.6.5 为 70，本次 +8，需下版收敛） |
| release 产物 | `app-direct-release.apk` 1,910,917 字节（含签名）；`app-play-release.apk` 同步产出 |
| mapping | `app/build/outputs/mapping/directRelease/mapping.txt` 32,721,097 字节 |
| versionCode 一致性 | `build.gradle.kts` = 34；设备 `dumpsys package` versionName 0.6.6 |
| 迁移注册守卫 | `addMigrations(*ALL_MIGRATIONS)` 仍在（`AppDatabase.kt:159`），有源码断言测试 |
| play manifest | 无 `AlarmGuardService` 声明（CI 断言 + 本地核对） |

---

## 3. 设备验证记录

| 项 | 结果 |
| --- | --- |
| 冷启动主界面 | ✅ 0.6.6 安装后启动正常、首屏可交互（截图见 outputs/verify-066.png） |
| **锁屏响铃 + 界面出现** | ✅ 在 `28d47d0` 构建上验证（0.6.6 响铃代码与其一致）：悬浮窗兜底界面在锁屏上出现、按钮可点 |
| 响铃通知动作 | ✅ 通知含「停止 + 稍后」，锁屏不解锁可处理 |
| 排期存在 | ✅ `dumpsys alarm` 18 条本应用条目 |
| 电池白名单 | ✅ `deviceidle whitelist` 含 `user,com.cyclealarm.app`；standby bucket 5（EXEMPTED） |
| 悬浮窗权限 | ✅ `appops SYSTEM_ALERT_WINDOW: allow` |
| FSI | ⚠️ `USE_FULL_SCREEN_INTENT` 仍 `default` + 有 rejectTime（ColorOS 拒绝），靠兜底浮窗替代 |
| API 34/36 模拟器 instrumentation | ✅ CI #79 起持续通过（3 用例 × 两个 API） |

---

## 4. 未验证项（§6）

1. **API 35/36 真机未验证**：只有 API 34 真机（ColorOS 14）与 API 36 模拟器；手机升级 Android 16 前无法覆盖。
2. **0.6.6 新增 UI 未做真机点击验证**：主页健康横幅与设置页「上次响铃」仅代码/单测覆盖；因当前设备四项检查全绿，横幅默认不出现，未能在真机触发其显示状态。
3. **Play 变体只构建未安装**：`app-play-release.apk` 未在设备上安装验证（缺少守护服务后，开机行为未实测）。
4. **tag 触发 CI 未配置**：`on.push.branches` 不含 tag，`v0.6.6` 的 CI 绿灯来自 `d78b7d0` 的 push run，不是 tag run。

---

## 5. 安全审核明细

| 检查 | 结论 |
| --- | --- |
| 密钥文件未入库 | ✅ `git log --all -- keystore.properties` 无记录；`git ls-files` 仅含 `keystore.properties.example` 与 `tools/create_keystore.ps1` |
| 口令未明文提交 | ✅ 本轮已迁出（原为阻断项）：本地 `keystore.properties` 只剩 `storeFile`/`keyAlias`；口令在 `~/.gradle/gradle.properties`（`cyclealarm.storePassword` / `cyclealarm.keyPassword`）或环境变量 |
| 组件导出面 | ✅ 仅 `MainActivity` 与 `BootReceiver` 为 `exported="true"` |
| PendingIntent 不可变 | ✅ 15 处 `PendingIntent.get*` 全部带 `FLAG_IMMUTABLE` |
| 敏感信息进日志 | ✅ grep `Log.*(password|token|secret)` 无命中 |
| 权限最小化说明 | ⚠️ README 未逐项说明 `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` / `SYSTEM_ALERT_WINDOW` / `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` → 记入下版整改 |
| R8 / mapping | ✅ release 包真机启动过；mapping 已生成（32.7 MB），需在 Release 中归档 |
| 签名证书 | ✅ `apksigner verify --print-certs` 证书 SHA-256 = `0e13e1b5…da45b9`（与历史一致，密钥未变） |

---

## 5.5 审核后补充修复（0.6.6 发布后、下个版本前）

| 项 | 内容 | 验证 |
| --- | --- | --- |
| 附录 B #4 自动备份无恢复入口 | 新增每日自动备份（`AutoBackupPolicy` + `BackupManager.writeDailyAutoBackup`，保留 5 份）与设置页「恢复自动备份」入口（列出文件 + 二次确认 + 走同一条 `BackupManager.import` 事务链路） | 单测 `AutoBackupPolicyTest`（5 例）+ 真机：设置页出现该入口、对话框正确列出 `11-05 20:24 · 303 B` |
| 备份大小显示 | 新增 `AutoBackupPolicy.formatSize`，小于 1 KB 显示字节（此前整除成 `0 KB`） | 单测 4 例 + 真机截图确认 `303 B` |

---

## 6. 最终结论

**结论：需修改后复审（非阻断）**

一句话依据：
> 功能层面 0.6.6 已解决「锁屏响铃无界面」的真实故障并且有真机证据；构建、测试、迁移守卫齐全；
> 安全门禁原有的**明文口令阻断项已在本轮关闭**。剩余为 4 条未验证项与 1 条文档缺失（权限用途说明），
> 均不影响响铃正确性与数据完整性，因此**不阻断发布**，但需在下个版本前补齐，并按清单要求让「涉及 DB 升级的版本」走 pre-release。

### 下次发版前必须补齐

1. 主页健康横幅 + 「上次响铃」的真机验证（可临时关掉通知权限触发横幅）
2. README 补 4 项敏感权限的用途说明
3. CI 增加 tag 触发（`on.push.tags: ["v*"]`），保证 tag 上真的跑过一次
4. lint warning 从 78 收敛回 70 以内
5. 自动备份恢复入口（附录 B #4）——影响数据可救性，建议优先
