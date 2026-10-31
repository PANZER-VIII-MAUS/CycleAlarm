package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteUndoPolicyTest {

    private val zone = ZoneId.systemDefault()
    // 固定日期：不让单测结果随运行当天变化（复审 §8.4）
    private val today = LocalDate.of(2026, 10, 31)
    private val now = today.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `cycle alarm restore keeps id and computed schedule`() {
        val snapshot = AlarmEntity(
            id = 42,
            dayIndex = 0,
            hour = 7,
            minute = 30,
            label = "上课"
        )
        val settings = AppSettingsEntity(
            cycleLength = 1,
            cycleStartEpochDay = today.toEpochDay()
        )
        val before = AlarmScheduler.nextTriggerFor(snapshot, settings, emptyList(), now)
        val restored = DeleteUndoPolicy.alarmForRestore(snapshot)

        assertEquals(42, restored.id)
        assertEquals(before, AlarmScheduler.nextTriggerFor(restored, settings, emptyList(), now))
    }

    @Test
    fun `date alarm restore keeps id and trigger`() {
        val snapshot = DateAlarmEntity(
            id = 9,
            epochDay = today.toEpochDay(),
            hour = 8,
            minute = 20
        )
        val settings = AppSettingsEntity(cycleStartEpochDay = today.toEpochDay())
        val restored = DeleteUndoPolicy.dateAlarmForRestore(snapshot)

        assertEquals(9, restored.id)
        assertEquals(
            AlarmScheduler.nextTriggerForDateAlarm(snapshot, settings, emptyList(), now),
            AlarmScheduler.nextTriggerForDateAlarm(restored, settings, emptyList(), now)
        )
    }

    @Test
    fun `holiday restore keeps identity and dates`() {
        val snapshot = HolidayEntity(
            id = 7,
            startEpochDay = today.toEpochDay(),
            endEpochDay = today.plusDays(2).toEpochDay(),
            label = "假期"
        )

        assertEquals(snapshot, DeleteUndoPolicy.holidayForRestore(snapshot))
    }
}
