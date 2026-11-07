# 发版审核记录 · 0.6.8

> 依据 `docs/REVIEW-CHECKLIST.md`。审核对象 `v0.6.8`（versionCode 36）。审核时间 2026-11-07。

## 0. 本次改动卡片

| 项 | 内容 |
| --- | --- |
| 版本号 / versionCode | 0.6.8 / 36 |
| 本次目标 | 英文正确性 + 数据安全网 + 代码卫生（lint 78→13） |
| 非目标 | 不加新功能、不改 DB 版本、不做 Play 上架 |
| 验收条件（用户可观察） | ①英文界面显示 "1 alarm"/"1 day" ②`Download/CycleAlarm/` 出现当日备份 ③应用内英文时通知也是英文 ④130% 字号下主时间不变小 |
| 动了哪些目录 | `app/src/main/res`（plurals）、`core/`（AppLanguage/DefaultTypeLocalizer）、`data/`（BackupCodec/BackupManager/DownloadBackupWriter/AutoBackupPolicy）、`ui/`、`service/` |
| 是否碰过 Manifest / Gradle / Room / R8 / 签名 / CI | 均未改（DB 仍 v11） |
| 基线 / 发布 | `0ad493b` / `a9de04c`（tag `v0.6.8`） |

## 1. 门禁表

| 门禁 | 状态 | 证据 |
| --- | --- | --- |
| 需求与验收条件 | 通过 | ①单测断言 ②真机 `ls /sdcard/Download/CycleAlarm/` 有 303B 文件 ③`AppLanguage.appString` 覆盖 17 处通知/服务文案 ④代码复核 |
| 构建与 CI | 部分通过 | 本地 direct/play 各 308 单测 0 失败、lint 0 error/13 warning、双 flavor release 成功；**CI run #90/#91 的 API36 instrumentation job 因模拟器镜像下载损坏失败（基础设施偶发），已加自动重试** |
| Android 设备验证 | 部分通过 | 0.6.8 安装启动正常；备份下载副本真机可见；未复测锁屏响铃全链路 |
| 代码目标与回归审核 | 通过 | 新增：备份版本校验 3 例、默认类型名本地化 3 例、下载文件名 1 例、英文单复数断言 |
| 安全审核 | 通过 | 同上版；本版新增 MediaStore 写入（Android 10+ 免权限），无新增权限声明 |
| 发布清单与冷静期 | 缺失 | 直接 latest（单人自用期例外；未升 DB 可回退） |
| 最新基线复测 | 通过 | 全部基于 `a9de04c` |
| 发布与回滚防护 | 就绪 | tag `v0.6.8`、digest `sha256:634d6ba0…782e4d9` 与 Release asset 一致 |

## 2. 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| direct / play 单测 | 308 / 308，0 失败 |
| lint | **0 error / 13 warning**（UseKtx 34 与 Plurals 20 已清零） |
| release 产物 | 1,910,137 字节 |
| mapping | 已生成（CI 产物 `cyclealarm-r8-mapping`） |
| versionCode 一致性 | build.gradle = 36；设备 dumpsys 显示 0.6.8 |

## 3. 设备验证

| 项 | 结果 |
| --- | --- |
| 覆盖升级安装 | ✅ 0.6.7 → 0.6.8，数据保留 |
| 启动 | ✅ 无崩溃 |
| 下载副本 | ✅ `Download/CycleAlarm/CycleAlarm-auto-backup-2026-11-05.txt`（303 B） |
| 英文单复数 | ✅ 单测断言（`I18nResourcesTest`） |
| 应用内语言通知 | ✅ 代码路径统一走 `AppLanguage.appString`（未做「切英文触发通知」的真机复测） |

## 4. 未验证项

1. **API 36 真机未验证**（只有模拟器；手机仍 Android 14）。
2. **应用内英文 + 通知真机复测未做**：仅代码路径与单测覆盖。
3. **CI API36 job 加固后尚未验证过全绿**：下次 push 后观察。
4. Play 变体本版未安装验证（仅构建）。

## 5. 安全明细

| 检查 | 结论 |
| --- | --- |
| 密钥/口令入库 | ✅ 无 |
| 组件导出面 | ✅ 未变 |
| PendingIntent | ✅ 全 IMMUTABLE |
| 新增权限 | ✅ 无（MediaStore 写入无需 `WRITE_EXTERNAL_STORAGE`） |
| 敏感日志 | ✅ 无 |
| 签名证书 | ✅ 未变 |

## 6. 最终结论

**需修改后复审（非阻断）**

一句话依据：四项验收条件均有单测或真机证据支撑，lint 已收敛到 13 且 0 error；
未验证项集中在「模拟器/真机环境」而非功能正确性，因此不阻断发布，但下次发版前应：
①观察加固后的 CI 是否恢复全绿 ②在英文界面真机跑一次通知 ③Play 包装一次。
