package com.cyclealarm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.LocalizedFailure
import android.util.Log
import java.io.File

/**
 * 数据备份的导出 / 导入。
 *
 * 导入是「先解码校验、再一次性事务替换、最后重排」的三段式：
 * 解码失败时数据库完全不动，不会出现「删了一半又失败」的中间态。
 */
object BackupManager {
    private const val TAG = "BackupManager"

    /** 备份文件大小上限：超过视为损坏，避免把超大文件读进内存。 */
    private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024

    data class BackupSummary(
        val alarms: Int,
        val dateAlarms: Int,
        val activityTypes: Int,
        val dayPlans: Int,
        val holidays: Int,
        val snoozes: Int,
        /** 恢复前自动保存的当前数据备份路径；保存失败时为 null。 */
        val autoBackupPath: String? = null,
        /** 恢复后重排是否失败（失败时数据已恢复，但闹钟可能没排上，需要提示用户重试）。 */
        val rescheduleFailed: Boolean = false
    )

    /** 恢复前快照保留份数。 */
    private const val AUTO_BACKUP_KEEP = 3

    /** 每日自动备份保留份数（5 天，够覆盖「发现数据不对劲」的时间窗）。 */
    private const val AUTO_BACKUP_KEEP_DAILY = 5

    suspend fun export(context: Context): String {
        val db = AppDatabase.get(context)
        val backup = BackupCodec.Backup(
            settings = db.settingsDao().get(),
            alarms = db.alarmDao().getAll(),
            dateAlarms = db.dateAlarmDao().getAll(),
            activityTypes = db.activityTypeDao().getAll(),
            dayPlans = db.dayPlanDao().getAll(),
            holidays = db.holidayDao().getAll(),
            snoozes = db.snoozeDao().getAll(),
            exportedAt = System.currentTimeMillis()
        )
        return BackupCodec.encode(backup)
    }

    suspend fun import(context: Context, payload: String): BackupSummary {
        // 先解码：格式错误在这里就抛出，数据库保持原样
        val backup = BackupCodec.decode(payload)
        if (
            backup.settings == null && backup.alarms.isEmpty() &&
            backup.dateAlarms.isEmpty() && backup.dayPlans.isEmpty()
        ) {
            throw LocalizedFailure(R.string.err_backup_no_data)
        }

        // 业务约束校验：必须在动库之前（非法备份不允许写入，0.6.10 审查 Important）
        BackupValidator.firstIssue(backup)?.let { issue ->
            throw LocalizedFailure(issue.messageRes)
        }

        // 恢复前先把「当前数据」落一份自动备份，误导入时还能救回来
        val autoBackupPath = runCatching { writeAutoBackup(context, export(context)) }.getOrNull()

        val db = AppDatabase.get(context)
        // 恢复前先记下当前库里的 snooze：事务提交后要取消它们已武装的 PendingIntent
        val previousSnoozes = runCatching { db.snoozeDao().getAll() }.getOrDefault(emptyList())
        db.withTransaction {
            db.alarmDao().deleteAll()
            db.dateAlarmDao().deleteAll()
            db.activityTypeDao().deleteAll()
            db.dayPlanDao().deleteAll()
            db.holidayDao().deleteAll()
            db.snoozeDao().deleteAll()
            // 恢复语义 = 回到备份时刻：撤销点与响铃历史都属于「当前状态」，必须一起清掉
            db.importSnapshotDao().deleteAll()
            db.ringEventDao().deleteAll()

            backup.settings?.let { db.settingsDao().upsert(it) }
            backup.activityTypes.forEach { db.activityTypeDao().upsert(it) }
            backup.dayPlans.forEach { db.dayPlanDao().upsert(it) }
            backup.alarms.forEach { db.alarmDao().upsert(it) }
            backup.dateAlarms.forEach { db.dateAlarmDao().upsert(it) }
            backup.holidays.forEach { db.holidayDao().upsert(it) }
            backup.snoozes.forEach { runCatching { db.snoozeDao().insert(it) } }
        }

        // 事务已提交：先取消恢复前武装的旧 snooze，再按备份内容重排（否则旧稍后会在之后响）
        previousSnoozes.forEach { snooze ->
            runCatching { com.cyclealarm.app.core.AlarmScheduler.cancelStoredSnooze(context, snooze) }
                .onFailure { Log.e(TAG, "cancel old snooze failed", it) }
        }

        val resyncOk = runCatching { AlarmResyncHelper.resyncAll(context, auditMissed = false) }
            .onFailure { Log.e(TAG, "resync after restore failed", it) }
            .isSuccess

        // 事务成功后才清瞬时状态：放在事务前会因为事务失败白白清掉用户的跳过/静音
        com.cyclealarm.app.core.TemporaryMuteStore.clearAll(context)
        // 恢复 = 回到备份时刻：把漏响审计检查点推到「现在」，避免下次审计把恢复窗口
        // 内的旧事件报成「错过闹钟」
        runCatching {
            com.cyclealarm.app.core.ResyncCheckpoint.markChecked(context, System.currentTimeMillis())
        }

        return BackupSummary(
            alarms = backup.alarms.size,
            dateAlarms = backup.dateAlarms.size,
            activityTypes = backup.activityTypes.size,
            dayPlans = backup.dayPlans.size,
            holidays = backup.holidays.size,
            snoozes = backup.snoozes.size,
            autoBackupPath = autoBackupPath,
            // 排期失败不能静默当成「恢复成功」：UI 要提示用户重排（0.6.10 审查 Important）
            rescheduleFailed = !resyncOk
        )
    }

    /** 自动备份条目（恢复入口用）。 */
    enum class AutoBackupKind { DAILY, RESTORE }

    data class AutoBackupInfo(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val savedAt: Long,
        val kind: AutoBackupKind = AutoBackupKind.DAILY
    )

    /**
     * 每天首次打开应用时写一份自动备份（只保留最近 [AUTO_BACKUP_KEEP_DAILY] 份）。
     * 返回写入的文件路径；今天已经备份过则返回 null。
     */
    suspend fun writeDailyAutoBackup(context: Context): String? {
        val dir = dailyDir(context).apply { mkdirs() }
        migrateLegacyAutoBackups(context)
        val names = dir.listFiles()?.map { it.name } ?: emptyList()
        if (!AutoBackupPolicy.shouldWriteToday(names, System.currentTimeMillis())) return null
        val payload = runCatching { export(context) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val target = File(dir, AutoBackupPolicy.fileName(now))
        // 同步写一份到系统「下载」目录（可选功能，默认关：公共目录 = 明文暴露）
        runCatching {
            if (!com.cyclealarm.app.core.BackupPrefs.downloadCopyEnabled(context)) return@runCatching
            DownloadBackupWriter.writeIfAbsent(
                context,
                AutoBackupPolicy.downloadFileName(now),
                payload
            )
        }
        return runCatching {
            target.writeText(payload, Charsets.UTF_8)
            AutoBackupPolicy.expiredByPrefix(
                dir.listFiles()?.map { it.name } ?: emptyList(),
                AutoBackupPolicy.DAILY_PREFIX,
                AUTO_BACKUP_KEEP_DAILY
            ).forEach { stale -> File(dir, stale).delete() }
            target.absolutePath
        }.onFailure { Log.e(TAG, "daily auto backup failed", it) }.getOrNull()
    }

    /**
     * 同步「今天的下载副本」：即使私有备份今天已存在（例如刚升级到本版本），
     * 只要 Download/CycleAlarm 里还没有今天的文件，就补写一份。
     */
    suspend fun syncDailyDownloadBackup(context: Context): Boolean {
        if (!DownloadBackupWriter.isSupported()) return false
        if (!com.cyclealarm.app.core.BackupPrefs.downloadCopyEnabled(context)) return false
        val payload = runCatching { export(context) }.getOrNull() ?: return false
        return runCatching {
            DownloadBackupWriter.writeIfAbsent(
                context,
                AutoBackupPolicy.downloadFileName(System.currentTimeMillis()),
                payload
            )
        }.getOrDefault(false)
    }

    /** 列出可恢复的自动备份（每日备份 + 恢复前快照），最近的在前。 */
    fun listAutoBackups(context: Context): List<AutoBackupInfo> {
        migrateLegacyAutoBackups(context)
        val daily = (dailyDir(context).listFiles() ?: emptyArray()).filter { it.isFile }
        val restore = (restoreDir(context).listFiles() ?: emptyArray()).filter { it.isFile }
        val legacyRoot = (autoBackupRoot(context).listFiles() ?: emptyArray())
            .filter { it.isFile && AutoBackupPolicy.isRestoreName(it.name) }
        return (daily.map { it to AutoBackupKind.DAILY } +
            restore.map { it to AutoBackupKind.RESTORE } +
            legacyRoot.map { it to AutoBackupKind.RESTORE })
            .sortedByDescending { it.first.lastModified() }
            .map { (file, kind) ->
                AutoBackupInfo(file, file.name, file.length(), file.lastModified(), kind)
            }
    }

    /** 每日备份目录。 */
    fun dailyDir(context: Context): File = File(autoBackupRoot(context), "daily")

    /** 恢复前快照目录。 */
    fun restoreDir(context: Context): File = File(autoBackupRoot(context), "restore")

    private fun autoBackupRoot(context: Context): File =
        File(context.filesDir, "auto-backups").apply { mkdirs() }

    /** 把 0.6.8 及以前留在根目录的自动备份挪进对应子目录（只做一次，失败忽略）。 */
    private fun migrateLegacyAutoBackups(context: Context) {
        val root = autoBackupRoot(context)
        val legacy = (root.listFiles() ?: return).filter { it.isFile }
        if (legacy.isEmpty()) return
        legacy.forEach { file ->
            val target = when {
                file.name.startsWith(AutoBackupPolicy.DAILY_PREFIX) ->
                    File(dailyDir(context).apply { mkdirs() }, file.name)
                file.name.startsWith(AutoBackupPolicy.LEGACY_RESTORE_PREFIX) ->
                    File(restoreDir(context).apply { mkdirs() }, file.name)
                else -> null
            }
            if (target != null) runCatching { file.renameTo(target) }
        }
    }

    fun readAutoBackup(info: AutoBackupInfo): String {
        if (info.sizeBytes > MAX_BACKUP_BYTES) {
            throw com.cyclealarm.app.core.LocalizedFailure(com.cyclealarm.app.R.string.err_backup_too_large)
        }
        return info.file.readText(Charsets.UTF_8)
    }

    /**
     * 恢复前快照：写入独立目录，只淘汰同前缀文件。
     * 六轮复审 §4.1：以前按 lastModified 对**整目录** drop(3)，会把每日备份一起裁掉。
     */
    private fun writeAutoBackup(context: Context, payload: String): String {
        val dir = restoreDir(context).apply { mkdirs() }
        val file = File(dir, AutoBackupPolicy.restoreFileName(System.currentTimeMillis()))
        file.writeText(payload, Charsets.UTF_8)
        // 旧前缀 backup-before-restore-* 也参与同一保留额度（否则迁移来的旧文件永不淘汰）
        AutoBackupPolicy.expiredByPrefixes(
            dir.listFiles()?.map { it.name } ?: emptyList(),
            listOf(
                AutoBackupPolicy.RESTORE_PREFIX,
                AutoBackupPolicy.LEGACY_RESTORE_PREFIX
            ),
            AUTO_BACKUP_KEEP
        ).forEach { stale -> File(dir, stale).delete() }
        return file.absolutePath
    }
}
