package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.data.AppDatabase
import androidx.room.withTransaction
import java.time.LocalDate

/** 从数据库读取全部配置并重建闹钟排期（周期闹钟 + 按日期闹钟）。 */
object AlarmResyncHelper {
    suspend fun resyncAll(context: Context) {
        val db = AppDatabase.get(context)
        // 一次性按日期闹钟：顺手清理已经过去的记录，避免越积越多
        db.dateAlarmDao().deleteBefore(LocalDate.now().toEpochDay())
        val now = System.currentTimeMillis()
        val snoozes = db.withTransaction {
            db.snoozeDao().deleteExpired(now)
            db.snoozeDao().getAll()
        }
        AlarmScheduler.rescheduleAll(
            context,
            db.alarmDao().getAll(),
            db.dateAlarmDao().getAll(),
            db.settingsDao().get(),
            db.holidayDao().getAll()
        )
        snoozes.forEach { snooze ->
            if (SnoozeTimePolicy.shouldRestore(snooze.triggerAt, now)) {
                AlarmScheduler.rescheduleStoredSnooze(context, snooze)
            } else {
                db.snoozeDao().deleteById(snooze.id)
            }
        }
    }
}
