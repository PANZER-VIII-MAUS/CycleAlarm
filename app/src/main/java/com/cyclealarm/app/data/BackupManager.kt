package com.cyclealarm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.cyclealarm.app.core.AlarmResyncHelper
import android.util.Log

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
        val snoozes: Int
    )

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
        require(
            backup.settings != null || backup.alarms.isNotEmpty() ||
                backup.dateAlarms.isNotEmpty() || backup.dayPlans.isNotEmpty()
        ) { "备份文件里没有可恢复的数据" }

        val db = AppDatabase.get(context)
        db.withTransaction {
            db.alarmDao().deleteAll()
            db.dateAlarmDao().deleteAll()
            db.activityTypeDao().deleteAll()
            db.dayPlanDao().deleteAll()
            db.holidayDao().deleteAll()
            db.snoozeDao().deleteAll()

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
            snoozes = backup.snoozes.size
        )
    }
}
