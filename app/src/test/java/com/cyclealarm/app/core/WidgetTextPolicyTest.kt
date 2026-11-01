package com.cyclealarm.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetTextPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
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
        val text = WidgetTextPolicy.render(context, next = null, now = now)

        assertEquals(context.getString(R.string.widget_empty_title), text.time)
        assertEquals(context.getString(R.string.widget_empty_subtitle), text.subtitle)
    }

    @Test
    fun `24 hour format has no am pm suffix`() {
        val text = WidgetTextPolicy.render(context, next(7, 30, now + 60 * 60_000L), now, is24Hour = true)

        assertEquals("07:30", text.time)
        assertTrue(text.subtitle.isNotEmpty())
    }

    @Test
    fun `12 hour format appends localized am pm`() {
        val am = WidgetTextPolicy.render(context, next(7, 30, now + 60 * 60_000L), now, is24Hour = false)
        val pm = WidgetTextPolicy.render(context, next(19, 30, now + 60 * 60_000L), now, is24Hour = false)

        assertEquals("7:30 " + context.getString(R.string.time_am), am.time)
        assertEquals("7:30 " + context.getString(R.string.time_pm), pm.time)
    }

    @Test
    fun `date prefix is stripped for narrow widget`() {
        val nowMillis = java.time.LocalDateTime.of(2026, 10, 31, 12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val trigger = java.time.LocalDateTime.of(2026, 11, 2, 7, 30)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val text = WidgetTextPolicy.render(context, next(7, 30, trigger), nowMillis)

        assertEquals(context.getString(R.string.widget_days_hours, 1, 19), text.subtitle)
        assertTrue(!text.subtitle.contains("/11"))
    }

    @Test
    fun `countdown is reused from next alarm display`() {
        val text = WidgetTextPolicy.render(context, next(7, 30, now + 30 * 60_000L), now)

        assertEquals(context.getString(R.string.widget_minutes, 30), text.subtitle)
    }

    @Test
    fun `compact countdown uses minutes hours and days`() {
        val base = 1_800_000_000_000L
        assertEquals(
            context.getString(R.string.widget_minutes, 30),
            WidgetTextPolicy.compactCountdown(context, base + 30 * 60_000L, base)
        )
        assertEquals(
            context.getString(R.string.widget_hours, 8),
            WidgetTextPolicy.compactCountdown(context, base + 8 * 3_600_000L, base)
        )
        assertEquals(
            context.getString(R.string.widget_days_hours, 1, 8),
            WidgetTextPolicy.compactCountdown(context, base + 32 * 3_600_000L, base)
        )
        assertEquals(
            context.getString(R.string.widget_days, 2),
            WidgetTextPolicy.compactCountdown(context, base + 48 * 3_600_000L, base)
        )
    }
}
