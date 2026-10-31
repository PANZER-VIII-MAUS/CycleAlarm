package com.cyclealarm.app.core

import com.cyclealarm.app.data.RingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RingHistoryFormatterTest {

    private fun event(rungAt: Long?, ackedAt: Long?) = RingEventEntity(
        alarmKind = "CYCLE",
        alarmId = 1,
        label = "早课",
        expectedAt = 1_800_000_000_000L,
        rungAt = rungAt,
        ackedAt = ackedAt
    )

    @Test
    fun `missing rung time is a missed alarm`() {
        assertEquals(
            RingHistoryStatus.MISSED,
            RingHistoryFormatter.statusOf(event(rungAt = null, ackedAt = null))
        )
    }

    @Test
    fun `rang but not acknowledged is unhandled`() {
        assertEquals(
            RingHistoryStatus.UNHANDLED,
            RingHistoryFormatter.statusOf(event(rungAt = 1_800_000_000_000L, ackedAt = null))
        )
    }

    @Test
    fun `rang and acknowledged is acked`() {
        assertEquals(
            RingHistoryStatus.ACKED,
            RingHistoryFormatter.statusOf(
                event(rungAt = 1_800_000_000_000L, ackedAt = 1_800_000_001_000L)
            )
        )
    }

    @Test
    fun `summary counts every state`() {
        val events = listOf(
            event(rungAt = 1L, ackedAt = 2L),
            event(rungAt = 1L, ackedAt = null),
            event(rungAt = null, ackedAt = null),
            event(rungAt = null, ackedAt = null)
        )

        val summary = RingHistoryFormatter.summary(events)

        assertEquals(1, summary.acked)
        assertEquals(1, summary.unhandled)
        assertEquals(2, summary.missed)
        assertEquals(4, summary.total)
    }

    @Test
    fun `labels are user readable`() {
        assertEquals("已处理", RingHistoryFormatter.label(RingHistoryStatus.ACKED))
        assertEquals("响过未处理", RingHistoryFormatter.label(RingHistoryStatus.UNHANDLED))
        assertEquals("漏响", RingHistoryFormatter.label(RingHistoryStatus.MISSED))
    }
}
