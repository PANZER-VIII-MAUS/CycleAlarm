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

    data class BackupSummary(
        val alarms: Int,
        val dateAlarms: Int,
        val activityTypes: Int,
        val dayPlans: Int,
        val holidays: Int,
        val snoozes: Int,
        /** 恢复前自动保存的当前数据备份路径；保存失败时为 null。 */
        val autoBackupPath: String? = null
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

        // 恢复前先把「当前数据」落一份自动备份，误导入时还能救回来
        val autoBackupPath = runCatching { writeAutoBackup(context, export(context)) }.getOrNull()

        val db = AppDatabase.get(context)
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

        runCatching { AlarmResyncHelper.resyncAll(context, auditMissed = false) }
            .onFailure { Log.e(TAG, "resync after restore failed", it) }

        return BackupSummary(
            alarms = backup.alarms.size,
            dateAlarms = backup.dateAlarms.size,
            activityTypes = backup.activityTypes.size,
            dayPlans = backup.dayPlans.size,
            holidays = backup.holidays.size,
            snoozes = backup.snoozes.size,
            autoBackupPath = autoBackupPath
        )
    }

    /** 自动备份条目（恢复入口用）。 */
    data class AutoBackupInfo(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val savedAt: Long
    )

    /**
     * 每天首次打开应用时写一份自动备份（只保留最近 [AUTO_BACKUP_KEEP_DAILY] 份）。
     * 返回写入的文件路径；今天已经备份过则返回 null。
     */
    suspend fun writeDailyAutoBackup(context: Context): String? {
        val dir = File(context.filesDir, "auto-backups").apply { mkdirs() }
        val names = dir.listFiles()?.map { it.name } ?: emptyList()
        if (!AutoBackupPolicy.shouldWriteToday(names, System.currentTimeMillis())) return null
        val payload = runCatching { export(context) }.getOrNull() ?: return null
        val target = File(dir, AutoBackupPolicy.fileName(System.currentTimeMillis()))
        return runCatching {
            target.writeText(payload, Charsets.UTF_8)
            AutoBackupPolicy.expired(dir.listFiles()?.map { it.name } ?: emptyList(), AUTO_BACKUP_KEEP_DAILY)
                .forEach { stale -> File(dir, stale).delete() }
            target.absolutePath
        }.onFailure { Log.e(TAG, "daily auto backup failed", it) }.getOrNull()
    }

    /** 列出可恢复的自动备份（含「恢复前快照」与「每日备份」），最近的在前。 */
    fun listAutoBackups(context: Context): List<AutoBackupInfo> {
        val dir = File(context.filesDir, "auto-backups")
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { AutoBackupInfo(it, it.name, it.length(), it.lastModified()) }
    }

    fun readAutoBackup(info: AutoBackupInfo): String = info.file.readText(Charsets.UTF_8)

    /** 把 payload 写成 filesDir/auto-backups 下的文件，只保留最近 [AUTO_BACKUP_KEEP] 份。 */
    private fun writeAutoBackup(context: Context, payload: String): String {
        val dir = File(context.filesDir, "auto-backups").apply { mkdirs() }
        val file = File(dir, "backup-before-restore-${System.currentTimeMillis()}.txt")
        file.writeText(payload, Charsets.UTF_8)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(AUTO_BACKUP_KEEP)
            ?.forEach { it.delete() }
        return file.absolutePath
    }
}
