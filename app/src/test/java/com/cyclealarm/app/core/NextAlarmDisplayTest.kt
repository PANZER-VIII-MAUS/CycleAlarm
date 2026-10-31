package com.cyclealarm.app.core

import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class NextAlarmDisplayTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun `less than one minute is imminent`() {
        val now = at(7, 30)
        assertEquals("即将响铃", NextAlarmDisplay.countdownText(now + 30_000, now, zone))
    }

    @Test
    fun `within one hour omits day prefix`() {
        val now = at(7, 30)
        assertEquals(
            "还有 45 分钟",
            NextAlarmDisplay.countdownText(now + 45 * 60_000L, now, zone)
        )
    }

    @Test
    fun `today countdown includes hours and minutes`() {
        val now = at(7, 30)
        assertEquals(
            "今天 · 还有 2 小时 15 分",
            NextAlarmDisplay.countdownText(now + (2 * 60 + 15) * 60_000L, now, zone)
        )
    }

    @Test
    fun `tomorrow countdown uses tomorrow prefix`() {
        val now = at(16, 18)
        val trigger = today.plusDays(1).atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(
            "明天 · 还有 15 小时 12 分",
            NextAlarmDisplay.countdownText(trigger, now, zone)
        )
    }

    private fun at(hour: Int, minute: Int): Long =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `more than two days counts in days`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(3, ChronoUnit.DAYS).toEpochMilli()
        assertEquals(
            "11 月 1 日 · 还有 3 天",
            NextAlarmDisplay.countdownText(triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun `two days and one hour counts in days`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(49, ChronoUnit.HOURS).toEpochMilli()
        assertEquals(
            "10 月 31 日 · 还有 2 天 1 小时",
            NextAlarmDisplay.countdownText(triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }

    @Test
    fun `just under two days keeps hours for monotonic display`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val triggerAt = now.plus(47, ChronoUnit.HOURS).toEpochMilli()
        assertEquals(
            "10 月 31 日 · 还有 1 天 23 小时",
            NextAlarmDisplay.countdownText(triggerAt, now.toEpochMilli(), ZoneId.of("UTC"))
        )
    }
}
