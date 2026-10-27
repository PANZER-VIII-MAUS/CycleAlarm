package com.cyclealarm.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY dayIndex ASC, hour ASC, minute ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY dayIndex ASC, hour ASC, minute ASC")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()

    @Query("UPDATE alarms SET mutedDuringHoliday = :muted")
    suspend fun setAllMutedDuringHoliday(muted: Boolean)

    @Query("DELETE FROM alarms WHERE dayIndex >= :fromIndex")
    suspend fun deleteFromIndex(fromIndex: Int)
}

@Dao
interface DateAlarmDao {
    @Query("SELECT * FROM date_alarms ORDER BY epochDay ASC, hour ASC, minute ASC")
    fun observeAll(): Flow<List<DateAlarmEntity>>

    @Query("SELECT * FROM date_alarms ORDER BY epochDay ASC, hour ASC, minute ASC")
    suspend fun getAll(): List<DateAlarmEntity>

    @Query("SELECT * FROM date_alarms WHERE id = :id")
    suspend fun getById(id: Int): DateAlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: DateAlarmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(alarms: List<DateAlarmEntity>)

    @Query("DELETE FROM date_alarms WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM date_alarms")
    suspend fun deleteAll()

    /** 清理已经过去的一次性闹钟，避免记录越积越多。 */
    @Query("DELETE FROM date_alarms WHERE epochDay < :beforeEpochDay")
    suspend fun deleteBefore(beforeEpochDay: Long)
}

@Dao
interface ActivityTypeDao {
    @Query("SELECT * FROM activity_types ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ActivityTypeEntity>>

    @Query("SELECT * FROM activity_types ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<ActivityTypeEntity>

    @Query("SELECT * FROM activity_types WHERE id = :id")
    suspend fun getById(id: Int): ActivityTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(type: ActivityTypeEntity)

    @Query("DELETE FROM activity_types WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM activity_types")
    suspend fun count(): Int
}

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plans ORDER BY dayIndex ASC")
    fun observeAll(): Flow<List<DayPlanEntity>>

    @Query("SELECT * FROM day_plans ORDER BY dayIndex ASC")
    suspend fun getAll(): List<DayPlanEntity>

    @Query("SELECT * FROM day_plans WHERE dayIndex = :dayIndex")
    suspend fun getByDay(dayIndex: Int): DayPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: DayPlanEntity)

    @Query("DELETE FROM day_plans WHERE dayIndex = :dayIndex")
    suspend fun deleteByDay(dayIndex: Int)

    @Query("DELETE FROM day_plans WHERE dayIndex >= :fromIndex")
    suspend fun deleteFromIndex(fromIndex: Int)

    @Query("DELETE FROM day_plans WHERE typeId = :typeId")
    suspend fun deleteByType(typeId: Int)
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY startEpochDay ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays ORDER BY startEpochDay ASC")
    suspend fun getAll(): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE id = :id")
    suspend fun getById(id: Int): HolidayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(holiday: HolidayEntity)

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE holidays SET enabled = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}

@Dao
interface SnoozeDao {
    @Query("SELECT * FROM snoozes ORDER BY triggerAt ASC")
    suspend fun getAll(): List<SnoozeEntity>

    @Query("SELECT * FROM snoozes WHERE id = :id")
    suspend fun getById(id: Long): SnoozeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snooze: SnoozeEntity): Long

    @Query("DELETE FROM snoozes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM snoozes WHERE kind = :kind AND alarmId = :alarmId")
    suspend fun deleteByAlarm(kind: String, alarmId: Long)

    @Query("DELETE FROM snoozes WHERE triggerAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM snoozes")
    suspend fun deleteAll()
}

@Dao
interface ImportSnapshotDao {
    @Query("SELECT * FROM import_snapshots WHERE id = 1")
    suspend fun get(): ImportSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ImportSnapshotEntity)

    @Query("DELETE FROM import_snapshots WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM import_snapshots")
    suspend fun deleteAll()
}
