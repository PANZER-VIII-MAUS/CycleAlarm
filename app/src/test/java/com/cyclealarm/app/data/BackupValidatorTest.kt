package com.cyclealarm.app.data

import com.cyclealarm.app.core.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupValidatorTest {

    private fun baseBackup(
        settings: AppSettingsEntity? = AppSettingsEntity(cycleLength = 6, cycleStartEpochDay = 20_000),
        alarms: List<AlarmEntity> = listOf(AlarmEntity(id = 1, dayIndex = 0, hour = 7, minute = 30)),
        dateAlarms: List<DateAlarmEntity> = listOf(DateAlarmEntity(id = 1, epochDay = 20_001, hour = 8, minute = 0)),
        types: List<ActivityTypeEntity> = listOf(ActivityTypeEntity(id = 1, name = "上课", hour = 8, minute = 0)),
        plans: List<DayPlanEntity> = listOf(DayPlanEntity(dayIndex = 0, typeId = 1, hour = 8, minute = 0)),
        holidays: List<HolidayEntity> = listOf(HolidayEntity(id = 1, startEpochDay = 20_000, endEpochDay = 20_002)),
        snoozes: List<SnoozeEntity> = emptyList()
    ) = BackupCodec.Backup(
        settings = settings,
        alarms = alarms,
        dateAlarms = dateAlarms,
        activityTypes = types,
        dayPlans = plans,
        holidays = holidays,
        snoozes = snoozes
    )

    @Test
    fun `valid backup passes`() {
        assertNull(BackupValidator.firstIssue(baseBackup()))
    }

    @Test
    fun `invalid hour is rejected`() {
        val backup = baseBackup(alarms = listOf(AlarmEntity(id = 1, dayIndex = 0, hour = 24, minute = 0)))
        assertEquals(BackupValidator.Issue.TIME, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `invalid minute is rejected`() {
        val backup = baseBackup(dateAlarms = listOf(DateAlarmEntity(id = 1, epochDay = 20_001, hour = 8, minute = 60)))
        assertEquals(BackupValidator.Issue.TIME, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `day index outside cycle is rejected`() {
        val backup = baseBackup(alarms = listOf(AlarmEntity(id = 1, dayIndex = 6, hour = 7, minute = 0)))
        assertEquals(BackupValidator.Issue.DAY_INDEX, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `cycle length out of range is rejected`() {
        val backup = baseBackup(settings = AppSettingsEntity(cycleLength = 0, cycleStartEpochDay = 20_000))
        assertEquals(BackupValidator.Issue.CYCLE_LENGTH, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `duplicate alarm ids are rejected`() {
        val backup = baseBackup(
            alarms = listOf(
                AlarmEntity(id = 1, dayIndex = 0, hour = 7, minute = 0),
                AlarmEntity(id = 1, dayIndex = 1, hour = 8, minute = 0)
            )
        )
        assertEquals(BackupValidator.Issue.DUPLICATE_ID, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `plan referencing missing type is rejected`() {
        val backup = baseBackup(plans = listOf(DayPlanEntity(dayIndex = 0, typeId = 99, hour = 8, minute = 0)))
        assertEquals(BackupValidator.Issue.PLAN_TYPE_REF, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `holiday with start after end is rejected`() {
        val backup = baseBackup(holidays = listOf(HolidayEntity(id = 1, startEpochDay = 20_010, endEpochDay = 20_000)))
        assertEquals(BackupValidator.Issue.HOLIDAY_RANGE, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `snooze with invalid kind is rejected`() {
        val backup = baseBackup(
            snoozes = listOf(
                SnoozeEntity(kind = "weekly", alarmId = 1, dayIndex = 0, hour = 7, minute = 0,
                    label = "", triggerAt = System.currentTimeMillis() + 60_000, requestCode = 1)
            )
        )
        assertEquals(BackupValidator.Issue.SNOOZE, BackupValidator.firstIssue(backup))
    }

    @Test
    fun `snooze with non positive trigger is rejected`() {
        val backup = baseBackup(
            snoozes = listOf(
                SnoozeEntity(kind = AlarmScheduler.KIND_CYCLE, alarmId = 1, dayIndex = 0, hour = 7, minute = 0,
                    label = "", triggerAt = 0L, requestCode = 1)
            )
        )
        assertEquals(BackupValidator.Issue.SNOOZE, BackupValidator.firstIssue(backup))
    }
}
