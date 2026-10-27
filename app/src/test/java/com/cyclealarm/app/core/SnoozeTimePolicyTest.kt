package com.cyclealarm.app.core

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeTimePolicyTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `calculates target time for the next snooze`() {
        val now = LocalDate.of(2026, 10, 27)
            .atTime(17, 10)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            SnoozeTarget(now + 10 * 60_000L, hour = 17, minute = 20),
            SnoozeTimePolicy.target(now, minutes = 10, zoneId = zone)
        )
    }

    @Test
    fun `target time wraps across midnight`() {
        val now = LocalDate.of(2026, 10, 27)
            .atTime(23, 55)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val target = SnoozeTimePolicy.target(now, minutes = 10, zoneId = zone)

        assertEquals(0, target.hour)
        assertEquals(5, target.minute)
    }

    @Test
    fun `only future snoozes are restored`() {
        assertTrue(SnoozeTimePolicy.shouldRestore(triggerAt = 101, now = 100))
        assertFalse(SnoozeTimePolicy.shouldRestore(triggerAt = 100, now = 100))
        assertFalse(SnoozeTimePolicy.shouldRestore(triggerAt = 99, now = 100))
    }
}
