package com.cyclealarm.app.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleShareTextTest {

    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 4,
        cycleStartEpochDay = 20_000L,
        cycleType = CycleTypes.CUSTOM
    )

    @Test
    fun `header and cycle length are included`() {
        val text = ScheduleShareText.build(settings, emptyList(), emptyList())

        assertTrue(text.contains("循环闹钟"))
        assertTrue(text.contains("4 天一轮"))
        assertTrue(text.contains("周期闹钟：无"))
    }

    @Test
    fun `cycle alarms are sorted by day and time`() {
        val alarms = listOf(
            AlarmEntity(dayIndex = 1, hour = 20, minute = 0, label = "夜班"),
            AlarmEntity(dayIndex = 0, hour = 8, minute = 0, label = "白班")
        )

        val text = ScheduleShareText.build(settings, alarms, emptyList())

        val whiteIndex = text.indexOf("第 1 天 08:00 白班")
        val nightIndex = text.indexOf("第 2 天 20:00 夜班")
        assertTrue(whiteIndex in 0 until nightIndex)
    }

    @Test
    fun `daily mode is described without cycle length`() {
        val text = ScheduleShareText.build(
            settings.copy(cycleType = CycleTypes.DAILY),
            emptyList(),
            emptyList()
        )

        assertTrue(text.contains("循环：每天"))
    }

    @Test
    fun `date alarms are listed with dates`() {
        val dateAlarm = DateAlarmEntity(
            epochDay = java.time.LocalDate.of(2026, 11, 2).toEpochDay(),
            hour = 9,
            minute = 0,
            label = "考试"
        )
        val text = ScheduleShareText.build(settings, emptyList(), listOf(dateAlarm))

        assertTrue(text.contains("2026-11-02 09:00 考试"))
    }

    @Test
    fun `null settings yields a friendly note`() {
        val text = ScheduleShareText.build(null, emptyList(), emptyList())
        assertTrue(text.contains("还没有任何设置"))
    }
}
