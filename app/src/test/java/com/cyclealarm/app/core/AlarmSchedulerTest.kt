package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.AppSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 排期算法与 requestCode 命名空间的纯 JVM 测试（复审点名的最大盲区）。
 * 所有时间都用系统默认时区构造，和实现保持同一口径。
 */
class AlarmSchedulerTest {

    private val anchorDay: Long = LocalDate.of(2026, 10, 1).toEpochDay() // 周期第 1 天
    private val settings = AppSettingsEntity(cycleLength = 6, cycleStartEpochDay = anchorDay)

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun alarm(dayIndex: Int, hour: Int = 7, minute: Int = 30) = AlarmEntity(
        dayIndex = dayIndex,
        hour = hour,
        minute = minute
    )

    @Test
    fun `cycle and date namespaces never collide`() {
        assertNotEquals(
            AlarmScheduler.schedulerId(AlarmScheduler.KIND_CYCLE, 5),
            AlarmScheduler.schedulerId(AlarmScheduler.KIND_DATE, 5)
        )
    }

    @Test
    fun `schedulerId rejects ids outside the namespace`() {
        var rejected = false
        try {
            AlarmScheduler.schedulerId(AlarmScheduler.KIND_CYCLE, AlarmScheduler.MAX_ALARM_ID + 1)
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `snooze request code stays inside its own partition`() {
        val main = AlarmScheduler.schedulerId(AlarmScheduler.KIND_CYCLE, 3)
        val snooze = AlarmScheduler.snoozeRequestCode(AlarmScheduler.KIND_CYCLE, 3)
        assertTrue(snooze > main)
        assertEquals(main + 100_000, snooze)
    }

    @Test
    fun `next trigger lands on the matching cycle day`() {
        // 第 4 天（dayIndex=3）→ 2026-10-04 07:30
        val now = millisOf(2026, 10, 1, 8, 0)
        val next = AlarmScheduler.nextTriggerFor(alarm(3), settings, emptyList(), now)
        assertEquals(millisOf(2026, 10, 4, 7, 30), next)
    }

    @Test
    fun `today's time is used when it is still ahead`() {
        val now = millisOf(2026, 10, 4, 7, 0)
        val next = AlarmScheduler.nextTriggerFor(alarm(3), settings, emptyList(), now)
        assertEquals(millisOf(2026, 10, 4, 7, 30), next)
    }

    @Test
    fun `next trigger wraps to the following cycle`() {
        // 第 4 天的 07:30 已过 → 下一个周期（+6 天）
        val now = millisOf(2026, 10, 4, 8, 0)
        val next = AlarmScheduler.nextTriggerFor(alarm(3), settings, emptyList(), now)
        assertEquals(millisOf(2026, 10, 10, 7, 30), next)
    }

    @Test
    fun `day index beyond the cycle length yields null`() {
        val now = millisOf(2026, 10, 1, 8, 0)
        assertNull(AlarmScheduler.nextTriggerFor(alarm(6), settings, emptyList(), now))
    }

    @Test
    fun `holiday mute skips to the next cycle occurrence`() {
        val holiday = com.cyclealarm.app.data.HolidayEntity(
            startEpochDay = LocalDate.of(2026, 10, 4).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 10, 5).toEpochDay(),
            enabled = true
        )
        val now = millisOf(2026, 10, 1, 8, 0)
        val next = AlarmScheduler.nextTriggerFor(alarm(3), settings, listOf(holiday), now)
        assertEquals(millisOf(2026, 10, 10, 7, 30), next)
    }

    @Test
    fun `disabled alarm has no next trigger`() {
        val now = millisOf(2026, 10, 1, 8, 0)
        val disabled = alarm(3).copy(enabled = false)
        assertNull(AlarmScheduler.nextTriggerFor(disabled, settings, emptyList(), now))
    }
}
