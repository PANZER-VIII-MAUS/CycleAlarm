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
    fun `countdown is reused from next alarm display`() {
        val text = WidgetTextPolicy.render(next(7, 30, now + 30 * 60_000L), now)

        assertEquals("还有 30 分钟", text.subtitle)
    }
}
