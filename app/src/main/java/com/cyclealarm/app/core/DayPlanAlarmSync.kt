package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppDatabase

/**
 * 把 [DayPlanSync.diff] 算出的操作落到数据库与 AlarmManager 上。
 * 这里是薄壳：取消旧排期 → 写库 → 全量重排。
 */
object DayPlanAlarmSync {

    suspend fun apply(context: Context, ops: List<AlarmOp>) {
        val db = AppDatabase.get(context)

        ops.forEach { op ->
            when (op) {
                is AlarmOp.Delete -> {
                    db.alarmDao().getById(op.alarmId)?.let { alarm ->
                        AlarmScheduler.cancelAlarm(context, alarm)
                        db.alarmDao().deleteById(op.alarmId)
                    }
                }

                is AlarmOp.Insert -> {
                    db.alarmDao().upsert(
                        AlarmEntity(
                            dayIndex = op.dayIndex,
                            hour = op.hour,
                            minute = op.minute,
                            enabled = true,
                            // 安排闹钟不提供单独的假期静音开关：统一接受假期静音
                            mutedDuringHoliday = true,
                            label = op.label,
                            planGenerated = true
                        )
                    )
                }

                is AlarmOp.Update -> {
                    val existing = db.alarmDao().getById(op.alarmId) ?: return@forEach
                    AlarmScheduler.cancelAlarm(context, existing)
                    db.alarmDao().upsert(
                        existing.copy(
                            dayIndex = op.dayIndex,
                            hour = op.hour,
                            minute = op.minute,
                            label = op.label,
                            enabled = true,
                            mutedDuringHoliday = true,
                            planGenerated = true
                        )
                    )
                }
            }
        }

        AlarmResyncHelper.resyncAll(context)
    }

    /** 读取当前库内状态并跑一次 diff + apply（供各触发点复用）。 */
    suspend fun syncFromDatabase(context: Context) {
        val db = AppDatabase.get(context)
        val ops = DayPlanSync.diff(
            plans = db.dayPlanDao().getAll().map { DayPlanSync.toPlanRow(it) },
            types = db.activityTypeDao().getAll().map { DayPlanSync.toPlanType(it) },
            alarms = db.alarmDao().getAll()
        )
        apply(context, ops)
    }
}
