package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 8,
        cycleStartEpochDay = 20_500L,
        cycleType = CycleTypes.CUSTOM,
        holidayModeEnabled = true,
        plansSeeded = true
    )
    private val alarm = AlarmEntity(id = 1, dayIndex = 2, hour = 7, minute = 30, label = "早课")
    private val dateAlarm = DateAlarmEntity(
        id = 2,
        epochDay = 20_510L,
        hour = 9,
        minute = 0,
        label = "考试"
    )
    private val type = ActivityTypeEntity(
        id = 3,
        name = "自习",
        hour = 8,
        minute = 15,
        rings = true,
        sortOrder = 1
    )
    private val plan = DayPlanEntity(dayIndex = 2, typeId = 3, hour = 8, minute = 15, enabled = true)
    private val holiday = HolidayEntity(
        id = 5,
        startEpochDay = 20_600L,
        endEpochDay = 20_607L,
        enabled = true,
        label = "国庆 2026"
    )
    private val snooze = SnoozeEntity(
        id = 9,
        kind = "CYCLE",
        alarmId = 1,
        dayIndex = 2,
        hour = 7,
        minute = 30,
        label = "早课",
        triggerAt = 1_800_000_000_000L,
        requestCode = 100_001
    )

    private fun backup() = BackupCodec.Backup(
        settings = settings,
        alarms = listOf(alarm),
        dateAlarms = listOf(dateAlarm),
        activityTypes = listOf(type),
        dayPlans = listOf(plan),
        holidays = listOf(holiday),
        snoozes = listOf(snooze),
        exportedAt = 1_700_000_000_000L
    )

    @Test
    fun `backup round trip keeps every table`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup()))

        assertEquals(1_700_000_000_000L, decoded.exportedAt)
        assertEquals(settings, decoded.settings)
        assertEquals(listOf(alarm), decoded.alarms)
        assertEquals(listOf(dateAlarm), decoded.dateAlarms)
        assertEquals(listOf(type), decoded.activityTypes)
        assertEquals(listOf(plan), decoded.dayPlans)
        assertEquals(listOf(holiday), decoded.holidays)
        assertEquals(listOf(snooze), decoded.snoozes)
    }

    @Test
    fun `empty backup keeps an explicit header`() {
        val payload = BackupCodec.encode(
            BackupCodec.Backup(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )

        assertTrue(payload.startsWith("CYCLE_ALARM_BACKUP_V1"))
        val decoded = BackupCodec.decode(payload)
        assertTrue(decoded.alarms.isEmpty())
        assertEquals(null, decoded.settings)
    }

    @Test
    fun `foreign file is rejected`() {
        val failed = runCatching { BackupCodec.decode("hello world") }
        assertTrue(failed.isFailure)
    }
}
