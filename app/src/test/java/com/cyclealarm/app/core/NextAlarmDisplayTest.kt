package com.cyclealarm.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cyclealarm.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NextAlarmDisplayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun `less than one minute is imminent`() {
        val now = at(7, 30)
        assertEquals(
            context.getString(R.string.countdown_soon),
            NextAlarmDisplay.countdownText(context, now + 30_000, now, zone)
        )
    }

    @Test
    fun `within one hour omits day prefix`() {
        val now = at(7, 30)
        assertEquals(
            context.getString(R.string.countdown_minutes_left, 45),
            NextAlarmDisplay.countdownText(context, now + 45 * 60_000L, now, zone)
        )
    }

    @Test
    fun `today countdown includes hours and minutes`() {
        val now = at(7, 30)
        val expected = context.getString(R.string.countdown_today) + " · " +
            context.getString(R.string.countdown_hours_minutes, 2, 15)
        assertEquals(
            expected,
            NextAlarmDisplay.countdownText(context, now + (2 * 60 + 15) * 60_000L, now, zone)
        )
    }

    @Test
    fun `tomorrow countdown uses tomorrow prefix`() {
        val now = at(16, 18)
        val trigger = today.plusDays(1).atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
        val expected = context.getString(R.string.countdown_tomorrow) + " · " +
            context.getString(R.string.countdown_hours_minutes, 15, 12)
        assertEquals(expected, NextAlarmDisplay.countdownText(context, trigger, now, zone))
    }

    private fun at(hour: Int, minute: Int): Long =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `more than two days counts in days`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(3, ChronoUnit.DAYS).toEpochMilli()
        val datePrefix = context.getString(R.string.countdown_date, 11, 1)
        assertEquals(
            context.getString(R.string.countdown_days, datePrefix, 3),
            NextAlarmDisplay.countdownText(context, triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun `two days and one hour counts in days`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(49, ChronoUnit.HOURS).toEpochMilli()
        val datePrefix = context.getString(R.string.countdown_date, 10, 31)
        assertEquals(
            context.getString(R.string.countdown_days_hours, datePrefix, 2, 1),
            NextAlarmDisplay.countdownText(context, triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun `just under two days keeps hours for monotonic display`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(47, ChronoUnit.HOURS).toEpochMilli()
        val datePrefix = context.getString(R.string.countdown_date, 10, 31)
        assertEquals(
            context.getString(R.string.countdown_days_hours, datePrefix, 1, 23),
            NextAlarmDisplay.countdownText(context, triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }
}
