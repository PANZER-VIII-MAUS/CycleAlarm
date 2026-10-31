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
        val switchedToWeekly: Boolean,
        /** 因未确认循环切换而被跳过的周条目数（正常 UI 路径为 0）。 */
        val skippedWeeklyCount: Int = 0
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

    /**
     * @param weeklySwitchConfirmed 调用方是否已明确同意「把循环方式改为每周」。
     *   UI 的确认卡片会传 true；其它调用方默认 false，此时周条目不会被写入，循环配置保持不变。
     */
    suspend fun write(
        entries: List<ImportedEntry>,
        overwrite: Boolean,
        weeklySwitchConfirmed: Boolean = false
    ): WriteResult {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        // 快照读取与编码放在事务外：编码是纯 CPU 工作，不该占着数据库锁
        val snapshot = Snapshot(
            alarms = db.alarmDao().getAll(),
            dateAlarms = db.dateAlarmDao().getAll(),
            settings = db.settingsDao().get()
        )
        val snapshotPayload = ImportSnapshotCodec.encode(snapshot)
        val transaction = db.withTransaction {
            db.importSnapshotDao().deleteExpired(now)
            db.importSnapshotDao().upsert(
                ImportSnapshotEntity(
                    payload = snapshotPayload,
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
            // 写入层门禁：未经确认不得改用户的循环配置（UI 只是其中一个调用方）
            val allowWeeklySwitch =
                ImportWeeklyPolicy.allowCycleSwitch(entries, weeklySwitchConfirmed)
            val weeklySwitch = if (allowWeeklySwitch) ensureWeekly(db) else false to emptyList()
            alarmsToCancel += weeklySwitch.second
            val weeklySource = if (allowWeeklySwitch) weeklyEntries else emptyList()

            val existingCycle = ImportWritePolicy.existingCycleKeys(
                snapshot.alarms,
                overwrite
            ).toMutableSet()
            val existingDated = ImportWritePolicy.existingDateKeys(
                snapshot.dateAlarms,
                overwrite
            ).toMutableSet()

            val newCycle = weeklySource.mapNotNull { entry ->
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
                    switchedToWeekly = weeklySwitch.first,
                    skippedWeeklyCount = if (allowWeeklySwitch) 0 else weeklyEntries.size
                ),
                alarmsToCancel = alarmsToCancel.distinctBy { it.id },
                dateAlarmsToCancel = dateAlarmsToCancel.distinctBy { it.id }
            )
        }

        cancelSchedules(transaction.alarmsToCancel, transaction.dateAlarmsToCancel)
        runCatching { AlarmResyncHelper.resyncAll(context, auditMissed = false) }
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
        // 注意：**不删除** day_plans。
        // 撤销快照不包含 day_plans，删了就再也恢复不了（三轮复审 §3.2 判定的数据丢失）。
        // 幽灵闹钟改由排期侧过滤：DayPlanAlarmSync 只处理 dayIndex < cycleLength 的安排。
        db.settingsDao().upsert(
            settings.copy(
                cycleType = CycleTypes.WEEKLY,
                cycleLength = 7,
                cycleStartEpochDay = monday
            )
        )
        return true to alarmsToCancel
    }

    /** 撤销结果：区分「没有快照」与「快照损坏」，UI 才能给出不同提示。 */
    sealed interface UndoResult {
        data object Success : UndoResult
        data object NoSnapshot : UndoResult
        data object Corrupted : UndoResult
    }

    private sealed interface UndoOutcome {
        data class Success(val undo: TransactionUndo) : UndoOutcome
        data object Corrupted : UndoOutcome
    }

    /**
     * 撤销：事务内恢复快照。
     * - 快照损坏时清掉它并返回 [UndoResult.Corrupted]，避免永久卡死撤销；
     * - 只取消「导入后新增」的闹钟排期，旧闹钟与它们的 snooze 不动（由 resync 重建）。
     */
    suspend fun undo(): UndoResult {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val outcome: UndoOutcome? = db.withTransaction {
            val stored = db.importSnapshotDao().get()
            if (stored == null || stored.expiresAt <= now) {
                db.importSnapshotDao().deleteAll()
                return@withTransaction null
            }

            val snapshot = runCatching { ImportSnapshotCodec.decode(stored.payload) }
                .getOrElse {
                    Log.e(TAG, "decode import snapshot failed, dropping it", it)
                    db.importSnapshotDao().deleteAll()
                    return@withTransaction UndoOutcome.Corrupted
                }

            val currentAlarms = db.alarmDao().getAll()
            val currentDated = db.dateAlarmDao().getAll()
            db.alarmDao().deleteAll()
            db.dateAlarmDao().deleteAll()
            snapshot.alarms.forEach { db.alarmDao().upsert(it) }
            db.dateAlarmDao().upsertAll(snapshot.dateAlarms)
            snapshot.settings?.let { db.settingsDao().upsert(it) }

            val planAlarmsToCancel = DayPlanAlarmSync.syncFromDatabaseOnly(db)
            db.importSnapshotDao().deleteAll()

            val removedAlarmIds = ImportUndoPolicy.idsToCancelAfterUndo(
                currentIds = currentAlarms.map { it.id },
                snapshotIds = snapshot.alarms.map { it.id }
            ).toSet()
            val removedDateIds = ImportUndoPolicy.idsToCancelAfterUndo(
                currentIds = currentDated.map { it.id },
                snapshotIds = snapshot.dateAlarms.map { it.id }
            ).toSet()
            UndoOutcome.Success(
                TransactionUndo(
                    alarmsToCancel = (
                        currentAlarms.filter { it.id in removedAlarmIds } + planAlarmsToCancel
                        ).distinctBy { it.id },
                    dateAlarmsToCancel = currentDated
                        .filter { it.id in removedDateIds }
                        .distinctBy { it.id }
                )
            )
        }

        return when (outcome) {
            is UndoOutcome.Success -> {
                cancelSchedules(outcome.undo.alarmsToCancel, outcome.undo.dateAlarmsToCancel)
                runCatching { AlarmResyncHelper.resyncAll(context, auditMissed = false) }
                    .onFailure { Log.e(TAG, "resync after undo failed", it) }
                UndoResult.Success
            }

            is UndoOutcome.Corrupted -> UndoResult.Corrupted
            null -> UndoResult.NoSnapshot
        }
    }

    suspend fun hasUndoSnapshot(now: Long = System.currentTimeMillis()): Boolean {
        val db = AppDatabase.get(context)
        // 只读检查：不需要写事务
        db.importSnapshotDao().deleteExpired(now)
        return db.importSnapshotDao().get() != null
    }

    private suspend fun cancelSchedules(
        alarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>
    ) {
        val db = AppDatabase.get(context)
        runCatching {
            db.withTransaction {
                alarms.forEach { alarm ->
                    db.snoozeDao().deleteByAlarm(AlarmScheduler.KIND_CYCLE, alarm.id.toLong())
                }
                dateAlarms.forEach { alarm ->
                    db.snoozeDao().deleteByAlarm(AlarmScheduler.KIND_DATE, alarm.id.toLong())
                }
            }
        }.onFailure { Log.e(TAG, "clear replaced snoozes failed", it) }
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
