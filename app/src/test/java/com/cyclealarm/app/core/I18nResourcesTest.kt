package com.cyclealarm.app.core

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证中英文资源都存在且内容正确（响铃相关文案）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class I18nResourcesTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun contextWith(language: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale(language))
        return app.createConfigurationContext(config)
    }

    @Test
    fun `ring strings differ between chinese and english`() {
        val zh = contextWith("zh")
        val en = contextWith("en")

        assertEquals("停止", zh.getString(com.cyclealarm.app.R.string.ring_stop))
        assertEquals("Stop", en.getString(com.cyclealarm.app.R.string.ring_stop))
        assertEquals("闹钟", zh.getString(com.cyclealarm.app.R.string.ring_title))
        assertEquals("Alarm", en.getString(com.cyclealarm.app.R.string.ring_title))
    }

    @Test
    fun `formatted strings keep placeholders`() {
        val en = contextWith("en")
        assertEquals(
            "Snooze 10 min",
            en.getString(com.cyclealarm.app.R.string.ring_snooze_minutes, 10)
        )
        assertEquals(
            "Day 3",
            en.getString(com.cyclealarm.app.R.string.ring_day_format, 3)
        )
        assertEquals(
            "Missed 2 alarms: A, B",
            en.resources.getQuantityString(com.cyclealarm.app.R.plurals.missed_multi, 2, 2, "A, B")
        )
        // 单复数改造后，1 条文案必须是 "1 alarm" 而不是 "1 alarms"
        assertEquals(
            "Missed 1 alarm: A",
            en.resources.getQuantityString(com.cyclealarm.app.R.plurals.missed_multi, 1, 1, "A")
        )
    }

    @Test
    fun `settings screen strings are translated`() {
        val zh = contextWith("zh")
        val en = contextWith("en")

        assertEquals("设置", zh.getString(com.cyclealarm.app.R.string.settings_title))
        assertEquals("Settings", en.getString(com.cyclealarm.app.R.string.settings_title))
        assertEquals("循环方式", zh.getString(com.cyclealarm.app.R.string.row_cycle_mode))
        assertEquals("Cycle mode", en.getString(com.cyclealarm.app.R.string.row_cycle_mode))
        assertEquals(
            "3 days",
            en.resources.getQuantityString(com.cyclealarm.app.R.plurals.format_days, 3, 3)
        )
        assertEquals(
            "1 day",
            en.resources.getQuantityString(com.cyclealarm.app.R.plurals.format_days, 1, 1)
        )
    }

    @Test
    fun `main screen strings are translated`() {
        val zh = contextWith("zh")
        val en = contextWith("en")

        assertEquals("循环闹钟", zh.getString(com.cyclealarm.app.R.string.main_title))
        assertEquals("CycleAlarm", en.getString(com.cyclealarm.app.R.string.main_title))
        assertEquals("未安排", zh.getString(com.cyclealarm.app.R.string.plan_unset))
        assertEquals("Not planned", en.getString(com.cyclealarm.app.R.string.plan_unset))
        assertEquals(
            "4-day cycle · today is day 2 · tap to change",
            en.getString(com.cyclealarm.app.R.string.cycle_sub_custom, 4, 2)
        )
    }
}
