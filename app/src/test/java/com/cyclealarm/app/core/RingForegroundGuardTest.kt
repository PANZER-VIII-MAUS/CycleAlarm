package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `RingForegroundGuard.attempt` 的纯 JVM 行为（不含 SharedPreferences 部分）。 */
class RingForegroundGuardTest {

    @Test
    fun `successful attempt reports foregrounded`() {
        var ran = false
        val outcome = RingForegroundGuard.attempt { ran = true }
        assertTrue(ran)
        assertTrue(outcome.foregrounded)
        assertNull(outcome.error)
    }

    @Test
    fun `security exception is captured instead of crashing`() {
        val outcome = RingForegroundGuard.attempt { throw SecurityException("denied") }
        assertFalse(outcome.foregrounded)
        assertTrue(outcome.error is SecurityException)
    }

    @Test
    fun `runtime exception is captured and reported by class name`() {
        val outcome = RingForegroundGuard.attempt {
            throw IllegalStateException("missing foreground service type")
        }
        assertFalse(outcome.foregrounded)
        assertEquals("IllegalStateException", outcome.error?.javaClass?.simpleName)
    }

    @Test
    fun `error is not swallowed`() {
        var propagated = false
        try {
            RingForegroundGuard.attempt { throw StackOverflowError() }
        } catch (e: StackOverflowError) {
            propagated = true
        }
        assertTrue(propagated)
    }
}
