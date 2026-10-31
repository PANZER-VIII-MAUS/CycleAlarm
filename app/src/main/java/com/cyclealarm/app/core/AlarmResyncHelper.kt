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
        val now = System.currentTimeMillis()

        // 先读数据（含已过期的按日期闹钟）再做审计：否则「跨天的日期闹钟」会被提前清掉而漏报
        val cycleAlarms = db.alarmDao().getAll()
        val dateAlarms = db.dateAlarmDao().getAll()
        val settings = db.settingsDao().get()
        val holidays = db.holidayDao().getAll()

        if (auditMissed) {
            auditMissed(context, now, cycleAlarms, dateAlarms, settings, holidays)
        }

        // 审计完成后再清理已过期的按日期闹钟
        runCatching { db.dateAlarmDao().deleteBefore(LocalDate.now().toEpochDay()) }

        // 响铃事件只保留最近 7 天，避免无限增长
        runCatching { db.ringEventDao().deleteBefore(now - 7L * 24 * 60 * 60_000L) }

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
        // 排期变化后同步刷新桌面小组件（没有小组件时内部直接返回）
        runCatching { com.cyclealarm.app.widget.WidgetUpdater.updateAll(context) }
    }

    /**
     * 只做对账，不重排、不做漏响审计：供 NEXT_ALARM_CLOCK_CHANGED 使用。
     *
     * 自己重排后系统会再发一次该广播，此时「期望值 == 实际值」，对账什么都不做，
     * 因此不会形成循环。
     */
    suspend fun auditNextAlarm(context: Context) {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        reconcileNextAlarm(
            context = context,
            now = now,
            cycleAlarms = db.alarmDao().getAll(),
            dateAlarms = db.dateAlarmDao().getAll(),
            settings = db.settingsDao().get(),
            holidays = db.holidayDao().getAll()
        )
    }

    private suspend fun auditMissed(
        context: Context,
        now: Long,
        cycleAlarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>,
        settings: AppSettingsEntity?,
        holidays: List<HolidayEntity>
    ) {
        val lastCheck = ResyncCheckpoint.lastCheckAt(context)
        // 计算式判定：覆盖「设备关机，事件压根没登记」的情况
        val computed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = cycleAlarms,
            dateAlarms = dateAlarms,
            settings = settings,
            holidays = holidays,
            firedAts = ResyncCheckpoint.recentFires(context, now)
        )
        // 事件式判定：覆盖「登记了但没真的响」（响铃服务启动失败）
        val fromEvents = runCatching {
            AppDatabase.get(context).ringEventDao()
                .missedBetween(lastCheck.coerceAtLeast(0L), now)
                .map { MissedAlarm(it.label, it.expectedAt, it.alarmKind) }
        }.getOrDefault(emptyList())

        val missed = MissedAlarmMergePolicy.merge(computed, fromEvents, lastCheck)
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
        // 没有精确闹钟权限时排期会降级为不精确闹钟，nextAlarmClock 与期望值天然不一致，
        // 此时反复重排没有意义（三轮复审 §4.2）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !manager.canScheduleExactAlarms()
        ) {
            Log.w(TAG, "skip reconciliation: exact alarm permission not granted")
            return
        }
        val expected = expectedNextTrigger(now, cycleAlarms, dateAlarms, settings, holidays) ?: return
        val actual = manager.nextAlarmClock?.triggerTime
        if (actual == null) {
            // 系统里一个 alarm clock 都不剩 —— 这正是「系统丢闹钟」的典型表现，必须自愈
            Log.w(TAG, "no alarm clock in system while expected=$expected, rescheduling")
            ResyncCheckpoint.recordDiagnostic(context, now, "系统无闹钟，已重排")
            AlarmScheduler.rescheduleAll(context, cycleAlarms, dateAlarms, settings, holidays)
            return
        }
        if (actual > expected) {
            // 系统里的下一次比我们算的晚（或压根没有）→ 说明排期没落地，补一次
            Log.w(TAG, "next alarm later than expected ($actual > $expected), rescheduling")
            ResyncCheckpoint.recordDiagnostic(context, now, "系统排期偏晚，已重排")
            AlarmScheduler.rescheduleAll(context, cycleAlarms, dateAlarms, settings, holidays)
        } else if (actual < expected) {
            // 系统里有一个更早的闹钟（通常是历史遗留的 PendingIntent）。
            // 这里只记录：反复重排也清不掉它，而且会变成「每次回前台都重排」的噪声。
            Log.w(TAG, "stale earlier alarm in system ($actual < $expected), keeping schedule")
            ResyncCheckpoint.recordDiagnostic(context, now, "存在更早的历史闹钟（未动排期）")
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
