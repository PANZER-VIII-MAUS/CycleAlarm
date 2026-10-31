package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextPolicyTest {

    private val now = 1_800_000_000_000L

    private fun next(hour: Int, minute: Int, triggerAt: Long) = NextAlarm(
        triggerAt = triggerAt,
        hour = hour,
        minute = minute,
        label = "早课",
        kind = NextAlarmKind.CYCLE,
        alarmId = 1
    )

    @Test
    fun `empty state shows guidance`() {
        val text = WidgetTextPolicy.render(next = null, now = now)

        assertEquals("暂无响铃", text.time)
        assertEquals(WidgetTextPolicy.EMPTY_SUBTITLE, text.subtitle)
    }

    @Test
    fun `24 hour format has no am pm suffix`() {
        val text = WidgetTextPolicy.render(next(7, 30, now + 60 * 60_000L), now, is24Hour = true)

        assertEquals("07:30", text.time)
        assertTrue(text.subtitle.isNotEmpty())
    }

    @Test
    fun `12 hour format appends chinese am pm`() {
        val am = WidgetTextPolicy.render(next(7, 30, now + 60 * 60_000L), now, is24Hour = false)
        val pm = WidgetTextPolicy.render(next(19, 30, now + 60 * 60_000L), now, is24Hour = false)

        assertEquals("7:30 上午", am.time)
        assertEquals("7:30 下午", pm.time)
    }

    @Test
    fun `date prefix is stripped for narrow widget`() {
        val now = java.time.LocalDateTime.of(2026, 10, 31, 12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val trigger = java.time.LocalDateTime.of(2026, 11, 2, 7, 30)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val text = WidgetTextPolicy.render(next(7, 30, trigger), now)

        assertTrue(text.subtitle.contains("天"))
        assertTrue(!text.subtitle.contains("月"))
    }

    @Test
    fun `countdown is reused from next alarm display`() {
        val text = WidgetTextPolicy.render(next(7, 30, now + 30 * 60_000L), now)

        assertEquals("30 分钟", text.subtitle)
    }

    @Test
    fun `compact countdown uses minutes hours and days`() {
        val base = 1_800_000_000_000L
        assertEquals("30 分钟", WidgetTextPolicy.compactCountdown(base + 30 * 60_000L, base))
        assertEquals("8 小时", WidgetTextPolicy.compactCountdown(base + 8 * 3_600_000L, base))
        assertEquals("1 天 8 时", WidgetTextPolicy.compactCountdown(base + 32 * 3_600_000L, base))
        assertEquals("2 天", WidgetTextPolicy.compactCountdown(base + 48 * 3_600_000L, base))
    }
}
