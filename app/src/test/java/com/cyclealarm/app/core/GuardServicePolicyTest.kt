package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardServicePolicyTest {

    @Test
    fun `starts when enabled but not running`() {
        assertEquals(
            GuardServicePolicy.Action.START,
            GuardServicePolicy.actionFor(enabled = true, currentlyRunning = false)
        )
    }

    @Test
    fun `stops when disabled but running`() {
        assertEquals(
            GuardServicePolicy.Action.STOP,
            GuardServicePolicy.actionFor(enabled = false, currentlyRunning = true)
        )
    }

    @Test
    fun `keeps state when already consistent`() {
        assertEquals(
            GuardServicePolicy.Action.KEEP,
            GuardServicePolicy.actionFor(enabled = true, currentlyRunning = true)
        )
        assertEquals(
            GuardServicePolicy.Action.KEEP,
            GuardServicePolicy.actionFor(enabled = false, currentlyRunning = false)
        )
    }
}
