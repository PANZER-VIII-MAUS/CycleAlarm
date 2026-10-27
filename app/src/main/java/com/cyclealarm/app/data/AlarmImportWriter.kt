package com.cyclealarm.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DayPlanAlarmSync
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import java.time.LocalDate

/**
 * 导入结果 → 数据库的唯一写入入口。
 * 数据库变更与导入快照位于同一事务；AlarmManager 操作只在事务提交后执行。
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
        val switchedToWeekly: Boolean
    )

    private data class TransactionWrite(
        val result: WriteResult,
        val alarmsToCancel: List<AlarmEntity>,
        val dateAlarmsToCancel: List<DateAlarmEntity>
    )

    private data class TransactionUndo(
        val alarmsToCancel: List<AlarmEntity>,
        val dateAlarmsToCancel: List<DateAlarmEntity>
    )

    suspend fun write(entries: List<ImportedEntry>, overwrite: Boolean): WriteResult {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val transaction = db.withTransaction {
            val snapshot = Snapshot(
                alarms = db.alarmDao().getAll(),
                dateAlarms = db.dateAlarmDao().getAll(),
                settings = db.settingsDao().get()
            )
            db.importSnapshotDao().deleteExpired(now)
            db.importSnapshotDao().upsert(
                ImportSnapshotEntity(
                    payload = ImportSnapshotCodec.encode(snapshot),
                    createdAt = now,
                    expiresAt = now + SNAPSHOT_LIFETIME_MS
                )
            )

            val alarmsToCancel = mutableListOf<AlarmEntity>()
            val dateAlarmsToCancel = mutableListOf<DateAlarmEntity>()
            if (overwrite) {
                alarmsToCancel += snapshot.alarms
                dateAlarmsToCancel += snapshot.dateAlarms
                db.alarmDao().deleteAll()
                db.dateAlarmDao().deleteAll()
            }

            val weeklyEntries = entries.filter { it.kind == ScheduleKind.WEEKLY }
            val datedEntries = entries.filter { it.kind == ScheduleKind.DATED }
            val weeklySwitch = if (weeklyEntries.isNotEmpty()) ensureWeekly(db) else false to emptyList()
            alarmsToCancel += weeklySwitch.second

            val existingCycle = ImportWritePolicy.existingCycleKeys(
                snapshot.alarms,
                overwrite
            ).toMutableSet()
            val existingDated = ImportWritePolicy.existingDateKeys(
                snapshot.dateAlarms,
                overwrite
            ).toMutableSet()

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

            // 覆盖导入可能删掉安排生成的闹钟；数据库同步仍在本事务内完成。
            alarmsToCancel += DayPlanAlarmSync.syncFromDatabaseOnly(db)

            TransactionWrite(
                result = WriteResult(
                    cycleCount = newCycle.size,
                    dateCount = newDated.size,
                    switchedToWeekly = weeklySwitch.first
                ),
                alarmsToCancel = alarmsToCancel.distinctBy { it.id },
                dateAlarmsToCancel = dateAlarmsToCancel.distinctBy { it.id }
            )
        }

        cancelSchedules(transaction.alarmsToCancel, transaction.dateAlarmsToCancel)
        runCatching { AlarmResyncHelper.resyncAll(context) }
            .onFailure { Log.e(TAG, "resync after import failed", it) }
        return transaction.result
    }

    /** 切到「每周」循环；只改数据库，排期由调用方在事务提交后处理。 */
    private suspend fun ensureWeekly(db: AppDatabase): Pair<Boolean, List<AlarmEntity>> {
        val settings = db.settingsDao().get() ?: return false to emptyList()
        val today = LocalDate.now()
        val monday = today.toEpochDay() - (today.dayOfWeek.value - 1)
        val alreadyWeekly = settings.cycleType == CycleTypes.WEEKLY &&
            settings.cycleLength == 7 &&
            settings.cycleStartEpochDay == monday
        if (alreadyWeekly) return false to emptyList()

        val alarmsToCancel = db.alarmDao().getAll().filter { it.dayIndex >= 7 }
        db.alarmDao().deleteFromIndex(7)
        db.settingsDao().upsert(
            settings.copy(
                cycleType = CycleTypes.WEEKLY,
                cycleLength = 7,
                cycleStartEpochDay = monday
            )
        )
        return true to alarmsToCancel
    }

    /** 撤销：在同一事务内读取快照、替换数据并清空快照。 */
    suspend fun undo(): Boolean {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val transaction = db.withTransaction {
            val stored = db.importSnapshotDao().get()
            if (stored == null || stored.expiresAt <= now) {
                db.importSnapshotDao().deleteAll()
                return@withTransaction null
            }

            val snapshot = ImportSnapshotCodec.decode(stored.payload)
            val currentAlarms = db.alarmDao().getAll()
            val currentDated = db.dateAlarmDao().getAll()
            db.alarmDao().deleteAll()
            db.dateAlarmDao().deleteAll()
            snapshot.alarms.forEach { db.alarmDao().upsert(it) }
            db.dateAlarmDao().upsertAll(snapshot.dateAlarms)
            snapshot.settings?.let { db.settingsDao().upsert(it) }

            val planAlarmsToCancel = DayPlanAlarmSync.syncFromDatabaseOnly(db)
            db.importSnapshotDao().deleteAll()
            TransactionUndo(
                alarmsToCancel = (currentAlarms + planAlarmsToCancel).distinctBy { it.id },
                dateAlarmsToCancel = currentDated.distinctBy { it.id }
            )
        } ?: return false

        cancelSchedules(transaction.alarmsToCancel, transaction.dateAlarmsToCancel)
        runCatching { AlarmResyncHelper.resyncAll(context) }
            .onFailure { Log.e(TAG, "resync after undo failed", it) }
        return true
    }

    suspend fun hasUndoSnapshot(now: Long = System.currentTimeMillis()): Boolean {
        val db = AppDatabase.get(context)
        return db.withTransaction {
            db.importSnapshotDao().deleteExpired(now)
            db.importSnapshotDao().get() != null
        }
    }

    suspend fun clearSnapshot() {
        AppDatabase.get(context).importSnapshotDao().deleteAll()
    }

    private suspend fun cancelSchedules(
        alarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>
    ) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            alarms.forEach { alarm ->
                db.snoozeDao().deleteByAlarm(AlarmScheduler.KIND_CYCLE, alarm.id.toLong())
            }
            dateAlarms.forEach { alarm ->
                db.snoozeDao().deleteByAlarm(AlarmScheduler.KIND_DATE, alarm.id.toLong())
            }
        }
        alarms.forEach { alarm ->
            runCatching { AlarmScheduler.cancelAlarm(context, alarm) }
                .onFailure { Log.e(TAG, "cancel imported alarm failed id=${alarm.id}", it) }
        }
        dateAlarms.forEach { alarm ->
            runCatching { AlarmScheduler.cancelDateAlarm(context, alarm) }
                .onFailure { Log.e(TAG, "cancel imported date alarm failed id=${alarm.id}", it) }
        }
    }

    private companion object {
        const val TAG = "AlarmImportWriter"
        const val SNAPSHOT_LIFETIME_MS = 24 * 60 * 60 * 1000L
    }
}
