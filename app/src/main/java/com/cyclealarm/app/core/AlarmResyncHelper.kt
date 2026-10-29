package com.cyclealarm.app.core

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import java.time.LocalDate

/**
 * 从数据库读取全部配置并重建闹钟排期（周期闹钟 + 按日期闹钟）。
 *
 * 0.2.3 起多了两件事：
 * 1) 漏响审计：用 SharedPreferences 检查点判断「上次检查到现在」是否有应触发却没触发的闹钟；
 * 2) 排期对账：重排后与 AlarmManager.getNextAlarmClock() 比对，不一致立即重排一次并记日志。
 */
object AlarmResyncHelper {
    private const val TAG = "AlarmResyncHelper"

    suspend fun resyncAll(context: Context, auditMissed: Boolean = true) {
        val db = AppDatabase.get(context)
        // 一次性按日期闹钟：顺手清理已经过去的记录，避免越积越多
        db.dateAlarmDao().deleteBefore(LocalDate.now().toEpochDay())
        val now = System.currentTimeMillis()

        val cycleAlarms = db.alarmDao().getAll()
        val dateAlarms = db.dateAlarmDao().getAll()
        val settings = db.settingsDao().get()
        val holidays = db.holidayDao().getAll()

        if (auditMissed) {
            auditMissed(context, now, cycleAlarms, dateAlarms, settings, holidays)
        }

        val snoozes = db.withTransaction {
            db.snoozeDao().deleteExpired(now)
            db.snoozeDao().getAll()
        }

        AlarmScheduler.rescheduleAll(context, cycleAlarms, dateAlarms, settings, holidays)

        snoozes.forEach { snooze ->
            // 恢复前校验父闹钟仍然存在且启用，否则删掉记录（避免「删了闹钟还响」）
            val parentEnabled = when (snooze.kind) {
                AlarmScheduler.KIND_DATE ->
                    dateAlarms.any { it.id.toLong() == snooze.alarmId && it.enabled }
                else ->
                    cycleAlarms.any { it.id.toLong() == snooze.alarmId && it.enabled }
            }
            if (parentEnabled && SnoozeTimePolicy.shouldRestore(snooze.triggerAt, now)) {
                AlarmScheduler.rescheduleStoredSnooze(context, snooze)
            } else {
                db.snoozeDao().deleteById(snooze.id)
            }
        }

        reconcileNextAlarm(context, now, cycleAlarms, dateAlarms, settings, holidays)
        ResyncCheckpoint.markChecked(context, now)
    }

    private fun auditMissed(
        context: Context,
        now: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        val lastCheck = ResyncCheckpoint.lastCheckAt(context)
        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = cycleAlarms,
            dateAlarms = dateAlarms,
            settings = settings,
            holidays = holidays
        )
        if (missed.isEmpty()) return
        Log.w(TAG, "detected ${missed.size} missed alarms since $lastCheck")
        MissedAlarmNotifier.notifyMissed(context, missed)
        ResyncCheckpoint.recordMissed(context, now, missed.size)
    }

    /** 与系统侧 nextAlarmClock 对账：不一致说明排期没落下去，重排一次并记日志。 */
    private fun reconcileNextAlarm(
        context: Context,
        now: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val expected = expectedNextTrigger(now, cycleAlarms, dateAlarms, settings, holidays) ?: return
        val actual = manager.nextAlarmClock?.triggerTime ?: return
        if (actual != expected) {
            Log.w(TAG, "next alarm mismatch expected=$expected actual=$actual, rescheduling")
            AlarmScheduler.rescheduleAll(context, cycleAlarms, dateAlarms, settings, holidays)
        }
    }

    /** 所有启用闹钟里最近的一次触发；没有则 null。 */
    private fun expectedNextTrigger(
        now: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ): Long? {
        if (settings == null) return null
        var best: Long? = null
        cycleAlarms.filter { it.enabled }.forEach { alarm ->
            val next = AlarmScheduler.nextTriggerFor(alarm, settings, holidays, now)
            if (next != null && (best == null || next < best!!)) best = next
        }
        dateAlarms.filter { it.enabled }.forEach { alarm ->
            val next = AlarmScheduler.nextTriggerForDateAlarm(alarm, settings, holidays, now)
            if (next != null && (best == null || next < best!!)) best = next
        }
        return best
    }
}
