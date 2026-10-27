package com.cyclealarm.app.data

import android.content.Context
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DayPlanAlarmSync
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import java.time.LocalDate

/**
 * 导入结果 → 数据库的唯一写入入口。
 * 负责：写入前快照（供撤销）、WEEKLY 切换、去重、写库、重排。
 */
class AlarmImportWriter(private val context: Context) {

    data class Snapshot(
        val alarms: List<AlarmEntity>,
        val dateAlarms: List<DateAlarmEntity>,
        val settings: AppSettingsEntity?
    )

    data class WriteResult(
        val cycleCount: Int,
        val dateCount: Int,
        val snapshot: Snapshot,
        val switchedToWeekly: Boolean
    )

    suspend fun write(entries: List<ImportedEntry>, overwrite: Boolean): WriteResult {
        val db = AppDatabase.get(context)
        val snapshot = Snapshot(
            alarms = db.alarmDao().getAll(),
            dateAlarms = db.dateAlarmDao().getAll(),
            settings = db.settingsDao().get()
        )

        if (overwrite) {
            snapshot.alarms.forEach { AlarmScheduler.cancelAlarm(context, it) }
            snapshot.dateAlarms.forEach { AlarmScheduler.cancelDateAlarm(context, it) }
            snapshot.alarms.forEach { db.alarmDao().deleteById(it.id) }
            db.dateAlarmDao().deleteAll()
        }

        val weeklyEntries = entries.filter { it.kind == ScheduleKind.WEEKLY }
        val datedEntries = entries.filter { it.kind == ScheduleKind.DATED }
        val switched = if (weeklyEntries.isNotEmpty()) ensureWeekly(db) else false

        // 已存在的 (dayIndex/hour/minute)、(epochDay/hour/minute)，用于去重
        val existingCycle = db.alarmDao().getAll()
            .map { Triple(it.dayIndex, it.hour, it.minute) }
            .toMutableSet()
        val existingDated = db.dateAlarmDao().getAll()
            .map { Triple(it.epochDay, it.hour, it.minute) }
            .toMutableSet()

        val newCycle = weeklyEntries.mapNotNull { entry ->
            val weekday = entry.weekday ?: return@mapNotNull null
            val key = Triple(weekday - 1, entry.hour, entry.minute)
            if (!existingCycle.add(key)) return@mapNotNull null
            AlarmEntity(
                dayIndex = weekday - 1,
                hour = entry.hour,
                minute = entry.minute,
                enabled = true,
                mutedDuringHoliday = true,
                label = entry.title
            )
        }
        newCycle.forEach { db.alarmDao().upsert(it) }

        val newDated = datedEntries.mapNotNull { entry ->
            val epochDay = entry.epochDay ?: return@mapNotNull null
            val key = Triple(epochDay, entry.hour, entry.minute)
            if (!existingDated.add(key)) return@mapNotNull null
            DateAlarmEntity(
                epochDay = epochDay,
                hour = entry.hour,
                minute = entry.minute,
                enabled = true,
                mutedDuringHoliday = true,
                label = entry.title
            )
        }
        db.dateAlarmDao().upsertAll(newDated)

        // 覆盖导入会删掉「安排生成」的闹钟，这里重跑一次安排同步把它们补回来（幂等）
        DayPlanAlarmSync.syncFromDatabase(context)
        return WriteResult(newCycle.size, newDated.size, snapshot, switched)
    }

    /** 切到「每周」循环：第 1 天 = 本周一，长度固定 7；顺带清理第 8 天及以后的闹钟。 */
    private suspend fun ensureWeekly(db: AppDatabase): Boolean {
        val settings = db.settingsDao().get() ?: return false
        val today = LocalDate.now()
        val monday = today.toEpochDay() - (today.dayOfWeek.value - 1)
        val alreadyWeekly = settings.cycleType == CycleTypes.WEEKLY &&
            settings.cycleLength == 7 &&
            settings.cycleStartEpochDay == monday
        if (alreadyWeekly) return false

        db.alarmDao().getAll()
            .filter { it.dayIndex >= 7 }
            .forEach { AlarmScheduler.cancelAlarm(context, it) }
        db.alarmDao().deleteFromIndex(7)
        db.settingsDao().upsert(
            settings.copy(
                cycleType = CycleTypes.WEEKLY,
                cycleLength = 7,
                cycleStartEpochDay = monday
            )
        )
        return true
    }

    /** 撤销：清空当前数据 → 恢复快照（保留原 id）→ 重排。 */
    suspend fun undo(result: WriteResult) {
        val db = AppDatabase.get(context)
        val currentAlarms = db.alarmDao().getAll()
        val currentDated = db.dateAlarmDao().getAll()
        currentAlarms.forEach { AlarmScheduler.cancelAlarm(context, it) }
        currentDated.forEach { AlarmScheduler.cancelDateAlarm(context, it) }
        currentAlarms.forEach { db.alarmDao().deleteById(it.id) }
        db.dateAlarmDao().deleteAll()

        result.snapshot.alarms.forEach { db.alarmDao().upsert(it) }
        db.dateAlarmDao().upsertAll(result.snapshot.dateAlarms)
        result.snapshot.settings?.let { db.settingsDao().upsert(it) }

        // 撤销后同样重跑安排同步，保证安排与闹钟一致
        DayPlanAlarmSync.syncFromDatabase(context)
    }
}
