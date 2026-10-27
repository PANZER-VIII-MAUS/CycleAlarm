package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.data.SnoozeEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmFinderTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, 10, 27)
    private val epochDay = date.toEpochDay()
    private val settings = AppSettingsEntity(
        cycleLength = 1,
        cycleStartEpochDay = epochDay
    )

    @Test
    fun `returns null when nothing is enabled or future`() {
        val now = millis(12, 0)
        assertNull(
            NextAlarmFinder.find(
                now,
                cycleAlarms = listOf(cycleAlarm(hour = 10, enabled = false)),
                dateAlarms = listOf(dateAlarm(epochDay, hour = 9, enabled = true)),
                snoozes = listOf(snooze(millis(11, 0))),
                settings = settings,
                holidays = emptyList()
            )
        )
    }

    @Test
    fun `finds nearest cycle alarm`() {
        val now = millis(9, 0)
        val result = NextAlarmFinder.find(
            now,
            cycleAlarms = listOf(cycleAlarm(hour = 10, minute = 0)),
            dateAlarms = emptyList(),
            snoozes = emptyList(),
            settings = settings,
            holidays = emptyList()
        )

        assertEquals(NextAlarmKind.CYCLE, result?.kind)
        assertEquals(millis(10, 0), result?.triggerAt)
        assertEquals("上课", result?.label)
    }

    @Test
    fun `uses date alarm and not closed or disabled cycle alarms`() {
        val now = millis(8, 0)
        val result = NextAlarmFinder.find(
            now,
            cycleAlarms = listOf(cycleAlarm(hour = 7, minute = 0)),
            dateAlarms = listOf(dateAlarm(epochDay, hour = 9, minute = 30)),
            snoozes = emptyList(),
            settings = settings,
            holidays = emptyList()
        )

        assertEquals(NextAlarmKind.DATE, result?.kind)
        assertEquals(millis(9, 30), result?.triggerAt)
        assertEquals("排班", result?.label)
    }

    @Test
    fun `nearer snooze wins over future cycle alarm`() {
        val now = millis(9, 0)
        val result = NextAlarmFinder.find(
            now,
            cycleAlarms = listOf(cycleAlarm(hour = 12)),
            dateAlarms = emptyList(),
            snoozes = listOf(snooze(millis(10, 15))),
            settings = settings,
            holidays = emptyList()
        )

        assertEquals(NextAlarmKind.SNOOZE, result?.kind)
        assertEquals(10, result?.hour)
        assertEquals(15, result?.minute)
        assertEquals("稍后提醒", result?.label)
        assertEquals(true, result?.isSnooze)
    }

    @Test
    fun `holiday mute delays cycle alarm to next day`() {
        val now = millis(7, 0)
        val result = NextAlarmFinder.find(
            now,
            cycleAlarms = listOf(cycleAlarm(hour = 8)),
            dateAlarms = emptyList(),
            snoozes = emptyList(),
            settings = settings.copy(holidayModeEnabled = true),
            holidays = listOf(
                HolidayEntity(
                    startEpochDay = epochDay,
                    endEpochDay = epochDay,
                    enabled = true
                )
            )
        )

        assertEquals(millisAt(date.plusDays(1), 8, 0), result?.triggerAt)
    }

    @Test
    fun `excludes trigger exactly equal to now`() {
        val now = millis(8, 0)
        val result = NextAlarmFinder.find(
            now,
            cycleAlarms = listOf(cycleAlarm(hour = 8)),
            dateAlarms = emptyList(),
            snoozes = emptyList(),
            settings = settings,
            holidays = emptyList()
        )

        assertEquals(millisAt(date.plusDays(1), 8, 0), result?.triggerAt)
    }

    private fun cycleAlarm(
        hour: Int,
        minute: Int = 0,
        enabled: Boolean = true
    ) = AlarmEntity(
        id = 1,
        dayIndex = 0,
        hour = hour,
        minute = minute,
        enabled = enabled,
        label = "上课"
    )

    private fun dateAlarm(
        day: Long,
        hour: Int,
        minute: Int = 0,
        enabled: Boolean = true
    ) = DateAlarmEntity(
        id = 1,
        epochDay = day,
        hour = hour,
        minute = minute,
        enabled = enabled,
        label = "排班"
    )

    private fun snooze(triggerAt: Long) = SnoozeEntity(
        id = 1,
        kind = AlarmScheduler.KIND_CYCLE,
        alarmId = 1,
        dayIndex = 0,
        hour = 9,
        minute = 0,
        label = "稍后提醒",
        triggerAt = triggerAt,
        requestCode = 100_001
    )

    private fun millis(hour: Int, minute: Int): Long =
        millisAt(date, hour, minute)

    private fun millisAt(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}
