package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppDatabase
import androidx.room.withTransaction

/**
 * 把 [DayPlanSync.diff] 算出的操作落到数据库与 AlarmManager 上。
 * 这里是薄壳：取消旧排期 → 写库 → 全量重排。
 */
object DayPlanAlarmSync {

    suspend fun apply(context: Context, ops: List<AlarmOp>) {
        val db = AppDatabase.get(context)
        val alarmsToCancel = db.withTransaction {
            applyDatabaseOnly(db, ops)
        }
        alarmsToCancel.forEach { AlarmScheduler.cancelAlarm(context, it) }
        AlarmResyncHelper.resyncAll(context)
    }

    /** 只做数据库变更；返回需要在事务提交后取消排期的旧闹钟。 */
    suspend fun applyDatabaseOnly(
        db: AppDatabase,
        ops: List<AlarmOp>
    ): List<AlarmEntity> {
        val alarmsToCancel = mutableListOf<AlarmEntity>()
        ops.forEach { op ->
            when (op) {
                is AlarmOp.Delete -> {
                    db.alarmDao().getById(op.alarmId)?.let { alarm ->
                        alarmsToCancel += alarm
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
                    alarmsToCancel += existing
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
        return alarmsToCancel.distinctBy { it.id }
    }

    /** 读取当前库内状态并跑一次 diff + apply（供各触发点复用）。 */
    suspend fun syncFromDatabase(context: Context) {
        val db = AppDatabase.get(context)
        apply(context, databaseSyncOps(db))
    }

    /** 在一个既有事务内同步数据库，不触发 AlarmManager。 */
    suspend fun syncFromDatabaseOnly(db: AppDatabase): List<AlarmEntity> {
        val ops = databaseSyncOps(db)
        return applyDatabaseOnly(db, ops)
    }

    private suspend fun databaseSyncOps(db: AppDatabase): List<AlarmOp> {
        val cycleLength = db.settingsDao().get()?.cycleLength ?: 0
        // 只让当前周期内的安排参与排期：切到 7 天后，第 8 天以后的安排保留在库里但不生成闹钟
        val scopedPlans = DayPlanScope.inCurrentCycle(
            items = db.dayPlanDao().getAll(),
            cycleLength = cycleLength,
            dayIndexOf = { it.dayIndex }
        )
        return DayPlanSync.diff(
            plans = scopedPlans.map { DayPlanSync.toPlanRow(it) },
            types = db.activityTypeDao().getAll().map { DayPlanSync.toPlanType(it) },
            alarms = db.alarmDao().getAll()
        )
    }
}
