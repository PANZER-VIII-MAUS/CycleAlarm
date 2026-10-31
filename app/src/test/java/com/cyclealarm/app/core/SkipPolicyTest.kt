package com.cyclealarm.app.core

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipPolicyTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val now: Long = LocalDateTime.of(2026, 10, 31, 7, 0)
        .atZone(zone).toInstant().toEpochMilli()

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, minute)
            .atZone(zone).toInstant().toEpochMilli()

    /** 模拟「每天 07:30」的下一次触发。 */
    private fun nextDaily(after: Long): Long = after + 24 * 60 * 60_000L

    @Test
    fun `no skips keeps the original trigger`() {
        val first = now
        assertEquals(first, SkipPolicy.effectiveTrigger(first, emptySet(), emptySet(), ::nextDaily))
    }

    @Test
    fun `skipped trigger moves to the next occurrence`() {
        val first = now
        val result = SkipPolicy.effectiveTrigger(first, setOf(first), emptySet(), ::nextDaily)
        assertEquals(nextDaily(first), result)
    }

    @Test
    fun `muted day skips every trigger on that day`() {
        val first = at(31, 7, 0)
        val second = at(31, 8, 30)
        val mutedDay = SkipPolicy.epochDayOf(first, zone)
        val result = SkipPolicy.effectiveTrigger(
            firstTrigger = first,
            skips = emptySet(),
            mutedDays = setOf(mutedDay),
            nextAfter = { after ->
                when (after) {
                    first -> second
                    second -> at(1, 7, 30)
                    else -> nextDaily(after)
                }
            }
        )

        // 同一天的第二条也要跳过，落到第二天
        assertEquals(at(1, 7, 30), result)
    }

    @Test
    fun `consecutive skips are consumed`() {
        val first = now
        val second = nextDaily(first)
        val result = SkipPolicy.effectiveTrigger(
            firstTrigger = first,
            skips = setOf(first, second),
            mutedDays = emptySet(),
            nextAfter = ::nextDaily
        )
        assertEquals(nextDaily(second), result)
    }

    @Test
    fun `no trigger stays null`() {
        assertNull(SkipPolicy.effectiveTrigger(null, emptySet(), emptySet(), ::nextDaily))
    }

    @Test
    fun `runaway guard stops the loop`() {
        val first = now
        val always = { after: Long -> nextDaily(after) }
        val manySkips = (0..20).map { first + it * 24 * 60 * 60_000L }.toSet()
        assertNull(
            SkipPolicy.effectiveTrigger(first, manySkips, emptySet(), always)
        )
    }
}
