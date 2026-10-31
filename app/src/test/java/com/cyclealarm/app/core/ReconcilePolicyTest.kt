package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconcilePolicyTest {

    @Test
    fun `no expectation means nothing to do`() {
        assertEquals(ReconcilePolicy.Action.NONE, ReconcilePolicy.decide(null, 1L, 0))
    }

    @Test
    fun `missing pending intents force a reschedule`() {
        assertEquals(
            ReconcilePolicy.Action.RESCHEDULE_MISSING,
            ReconcilePolicy.decide(expected = 100L, actual = 100L, missingCount = 2)
        )
    }

    @Test
    fun `no system alarm forces a reschedule`() {
        assertEquals(
            ReconcilePolicy.Action.RESCHEDULE_EMPTY,
            ReconcilePolicy.decide(expected = 100L, actual = null, missingCount = 0)
        )
    }

    @Test
    fun `later system schedule is rescheduled`() {
        assertEquals(
            ReconcilePolicy.Action.RESCHEDULE_LATER,
            ReconcilePolicy.decide(expected = 100L, actual = 200L, missingCount = 0)
        )
    }

    @Test
    fun `earlier stale alarm is only logged`() {
        assertEquals(
            ReconcilePolicy.Action.LOG_STALE,
            ReconcilePolicy.decide(expected = 200L, actual = 100L, missingCount = 0)
        )
    }

    @Test
    fun `matching schedule is a no-op`() {
        assertEquals(
            ReconcilePolicy.Action.NONE,
            ReconcilePolicy.decide(expected = 100L, actual = 100L, missingCount = 0)
        )
        assertNull(ReconcilePolicy.diagnosticText(ReconcilePolicy.Action.NONE))
    }
}
