package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.data.DateAlarmEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五轮复审附录 B #6/#7 的回归测试：
 * - 用户「跳过下一次」的按日期闹钟不能被报成漏响；
 * - 跨夏令时日的「原定时间」不能用减 24 小时算。
 */
class SkipAndDstRegressionTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private val settings = AppSettingsEntity(
        cycleLength = 7,
        cycleStartEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
        cycleType = CycleTypes.CUSTOM
    )

    @Test
    fun `skipped date alarm is not reported as missed`() {
        val today = LocalDate.of(2026, 11, 1)
        val now = at(today, 12, 0)
        val alarm = DateAlarmEntity(
            id = 7,
            epochDay = today.toEpochDay(),
            hour = 8,
            minute = 0,
            enabled = true,
            mutedDuringHoliday = false,
            label = "日期闹钟"
        )
        val lastCheck = at(today, 7, 0)

        val withoutSkip = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = emptyList(),
            dateAlarms = listOf(alarm),
            settings = settings,
            holidays = emptyList()
        )
        assertTrue("未跳过时应报漏响", withoutSkip.isNotEmpty())

        val withSkip = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = emptyList(),
            dateAlarms = listOf(alarm),
            settings = settings,
            holidays = emptyList(),
            isSkipped = { _, _, triggerAt -> triggerAt == at(today, 8, 0) }
        )
        assertTrue("用户明确跳过后不应报漏响，实际: $withSkip", withSkip.isEmpty())
    }

    @Test
    fun `skipped cycle alarm is not reported as missed`() {
        val today = LocalDate.of(2026, 11, 1)
        val now = at(today, 12, 0)
        val lastCheck = at(today, 7, 0)
        val alarm = AlarmEntity(id = 3, dayIndex = 0, hour = 8, minute = 0)

        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = lastCheck,
            cycleAlarms = listOf(alarm),
            dateAlarms = emptyList(),
            settings = settings,
            holidays = emptyList(),
            isSkipped = { kind, alarmId, triggerAt ->
                kind == AlarmScheduler.KIND_CYCLE && alarmId == 3 && triggerAt == at(today, 8, 0)
            }
        )
        assertTrue("跳过的周期闹钟不应报漏响，实际: $missed", missed.isEmpty())
    }

    @Test
    fun `dst spring forward does not shift the original time by an hour`() {
        // 美东 2026-03-08 02:00 春季跳变：23:55 的闹钟在 00:05 触发（跨午夜 snooze）
        val nyZone = ZoneId.of("America/New_York")
        val firedAt = LocalDate.of(2026, 3, 8)
            .atTime(0, 5)
            .atZone(nyZone)
            .toInstant()
            .toEpochMilli()

        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(nyZone))
            val expected = AlarmScheduler.expectedMillisOfOriginal(23, 55, firedAt)
            val wanted = LocalDate.of(2026, 3, 7)
                .atTime(23, 55)
                .atZone(nyZone)
                .toInstant()
                .toEpochMilli()
            assertEquals("跨夏令时切换的原定时间必须按本地日期回退一天", wanted, expected)
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun `legacy v1 backup is flagged as unverified`() {
        val v1 = "CYCLE_ALARM_BACKUP_V1\nA\t1\t7\t30\t1\t1\t0\t\t\t1\t0\t0"
        assertTrue(com.cyclealarm.app.data.BackupCodec.isLegacyUnverified(v1))
        val v2 = "CYCLE_ALARM_BACKUP_V2\t10\nE\t0\tdeadbeef"
        assertFalse(com.cyclealarm.app.data.BackupCodec.isLegacyUnverified(v2))
    }
}
