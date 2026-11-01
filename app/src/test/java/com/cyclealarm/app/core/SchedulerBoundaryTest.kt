package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 调度器边界：空设置、每周/每天模式、日期闹钟过去/静音。 */
class SchedulerBoundaryTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 1)
    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 6, 0)

    @Test
    fun `null settings behaves like a daily alarm`() {
        // 文档化行为：没有 settings 时按「每天」处理（首启/恢复数据缺失时的兜底）
        val alarm = AlarmEntity(dayIndex = 0, hour = 7, minute = 30)
        assertEquals(
            at(today, 7, 30),
            AlarmScheduler.nextTriggerFor(alarm, settings = null, holidays = emptyList(), now = now)
        )
    }

    @Test
    fun `null settings still allows a future date alarm`() {
        val alarm = DateAlarmEntity(
            epochDay = today.toEpochDay() + 1,
            hour = 9,
            minute = 0
        )
        assertNotNull(AlarmScheduler.nextTriggerForDateAlarm(alarm, settings = null, holidays = emptyList(), now = now))
    }

    @Test
    fun `past date alarm has no trigger`() {
        val alarm = DateAlarmEntity(epochDay = today.toEpochDay() - 1, hour = 9, minute = 0)
        assertNull(AlarmScheduler.nextTriggerForDateAlarm(alarm, settings = null, holidays = emptyList(), now = now))
    }

    @Test
    fun `single day cycle fires every day on day zero`() {
        val settings = AppSettingsEntity(
            cycleLength = 1,
            cycleStartEpochDay = today.toEpochDay(),
            cycleType = CycleTypes.DAILY
        )
        val alarm = AlarmEntity(dayIndex = 0, hour = 7, minute = 30)
        assertEquals(
            at(today, 7, 30),
            AlarmScheduler.nextTriggerFor(alarm, settings, emptyList(), now)
        )
        // dayIndex 超出周期长度仍然返回 null（不静默挪到下一天）
        assertNull(
            AlarmScheduler.nextTriggerFor(AlarmEntity(dayIndex = 5, hour = 7, minute = 30), settings, emptyList(), now)
        )
    }

    @Test
    fun `weekly mode only fires on the matching weekday`() {
        val settings = AppSettingsEntity(
            cycleLength = 7,
            cycleStartEpochDay = today.toEpochDay() - 2, // 今天 = 第 3 天（dayIndex 2）
            cycleType = CycleTypes.WEEKLY
        )
        val matchingDay = AlarmEntity(dayIndex = 4, hour = 7, minute = 30)
        val otherDay = AlarmEntity(dayIndex = 1, hour = 7, minute = 30)

        val trigger = AlarmScheduler.nextTriggerFor(matchingDay, settings, emptyList(), now)
        assertNotNull(trigger)
        assertEquals(2, (trigger!! - at(today, 0, 0)) / (24 * 60 * 60_000L))
        assertNotNull(AlarmScheduler.nextTriggerFor(otherDay, settings, emptyList(), now))
    }

    @Test
    fun `muted date alarm returns null but holiday disabled keeps it`() {
        val holiday = HolidayEntity(
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.toEpochDay() + 2
        )
        val alarm = DateAlarmEntity(
            epochDay = today.toEpochDay() + 1,
            hour = 9,
            minute = 0,
            mutedDuringHoliday = true
        )
        val enabledSettings = AppSettingsEntity(
            cycleLength = 6,
            cycleStartEpochDay = today.toEpochDay(),
            holidayModeEnabled = true
        )
        val disabledSettings = enabledSettings.copy(holidayModeEnabled = false)

        assertNull(
            AlarmScheduler.nextTriggerForDateAlarm(alarm, enabledSettings, listOf(holiday), now)
        )
        assertNotNull(
            AlarmScheduler.nextTriggerForDateAlarm(alarm, disabledSettings, listOf(holiday), now)
        )
    }
}
