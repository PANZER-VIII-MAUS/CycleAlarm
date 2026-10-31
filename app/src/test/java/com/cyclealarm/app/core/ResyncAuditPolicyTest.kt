package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 漏响判定的降噪测试（三轮复审 §3.1 的四个误报场景 + 两个漏报场景）。
 * 所有时间都用系统默认时区换算，与实现同口径。
 */
class ResyncAuditPolicyTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val nowMillis: Long = LocalDateTime.of(2026, 10, 31, 12, 0)
        .atZone(zone).toInstant().toEpochMilli()
    private val lastCheck: Long = nowMillis - 30 * 60_000L

    private val settings = AppSettingsEntity(cycleLength = 1, cycleStartEpochDay = 20_000L)

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, 31, hour, minute)
            .atZone(zone).toInstant().toEpochMilli()

    private fun cycleAlarm(id: Int, hour: Int, minute: Int) = AlarmEntity(
        id = id,
        dayIndex = 0,
        hour = hour,
        minute = minute
    )

    private fun findMissed(
        cycle: List<AlarmEntity> = emptyList(),
        dates: List<DateAlarmEntity> = emptyList(),
        firedAts: List<Long> = emptyList()
    ) = ResyncAuditPolicy.findMissed(
        now = nowMillis,
        lastCheckAt = lastCheck,
        cycleAlarms = cycle,
        dateAlarms = dates,
        settings = settings,
        holidays = emptyList(),
        firedAts = firedAts
    )

    @Test
    fun `first launch never audits`() {
        assertFalse(ResyncAuditPolicy.shouldAudit(0L, nowMillis))
    }

    @Test
    fun `short gap is skipped`() {
        assertFalse(ResyncAuditPolicy.shouldAudit(nowMillis - 1_000L, nowMillis))
    }

    @Test
    fun `gap beyond lookback is ignored`() {
        assertFalse(
            ResyncAuditPolicy.shouldAudit(
                nowMillis - ResyncAuditPolicy.MAX_LOOKBACK_MS - 60_000L,
                nowMillis
            )
        )
    }

    @Test
    fun `alarm that rang is not reported`() {
        val alarm = cycleAlarm(1, 11, 45)
        assertTrue(findMissed(cycle = listOf(alarm)).any { it.expectedAt == at(11, 45) })
        assertTrue(
            findMissed(cycle = listOf(alarm), firedAts = listOf(at(11, 45))).isEmpty()
        )
    }

    @Test
    fun `snooze firing suppresses the original time`() {
        val alarm = cycleAlarm(1, 11, 45)
        // 11:45 正常响、11:50 稍后提醒再响 → 两个时间点都在列表里，不应误报
        val fired = listOf(at(11, 45), at(11, 50))
        assertTrue(findMissed(cycle = listOf(alarm), firedAts = fired).isEmpty())
    }

    @Test
    fun `two alarms in the same morning are both exempted`() {
        val a = cycleAlarm(1, 11, 40)
        val b = cycleAlarm(2, 11, 55)
        val fired = listOf(at(11, 40), at(11, 55))
        assertTrue(findMissed(cycle = listOf(a, b), firedAts = fired).isEmpty())
    }

    @Test
    fun `date alarm crossing days is reported`() {
        val dateAlarm = DateAlarmEntity(
            id = 9,
            epochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
            hour = 11,
            minute = 45,
            enabled = true,
            mutedDuringHoliday = false,
            label = "按日期"
        )
        val missed = findMissed(dates = listOf(dateAlarm))
        assertEquals(1, missed.size)
        assertEquals(at(11, 45), missed.first().expectedAt)
    }

    @Test
    fun `date alarm that rang is not reported`() {
        val dateAlarm = DateAlarmEntity(
            id = 9,
            epochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
            hour = 11,
            minute = 45,
            enabled = true,
            mutedDuringHoliday = false,
            label = "按日期"
        )
        assertTrue(findMissed(dates = listOf(dateAlarm), firedAts = listOf(at(11, 45))).isEmpty())
    }

    @Test
    fun `empty data yields nothing`() {
        assertTrue(findMissed().isEmpty())
    }
}
