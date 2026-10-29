package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResyncAuditPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `first launch never audits`() {
        assertFalse(ResyncAuditPolicy.shouldAudit(lastCheckAt = 0L, now = now))
    }

    @Test
    fun `short gap is skipped to avoid rescanning on every resume`() {
        assertFalse(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - ResyncAuditPolicy.CHECK_INTERVAL_MS / 2,
                now = now
            )
        )
    }

    @Test
    fun `normal gap triggers audit`() {
        assertTrue(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - 30 * 60_000L,
                now = now
            )
        )
    }

    @Test
    fun `gap beyond lookback window is ignored`() {
        assertFalse(
            ResyncAuditPolicy.shouldAudit(
                lastCheckAt = now - ResyncAuditPolicy.MAX_LOOKBACK_MS - 60_000L,
                now = now
            )
        )
    }

    @Test
    fun `empty data yields no missed alarms`() {
        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = now - 30 * 60_000L,
            cycleAlarms = emptyList(),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `null settings yields no missed alarms`() {
        val missed = ResyncAuditPolicy.findMissed(
            now = now,
            lastCheckAt = now - 30 * 60_000L,
            cycleAlarms = emptyList(),
            dateAlarms = emptyList(),
            settings = null,
            holidays = emptyList()
        )
        assertTrue(missed.isEmpty())
    }
}
