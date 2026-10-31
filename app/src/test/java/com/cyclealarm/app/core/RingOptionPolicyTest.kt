package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingOptionPolicyTest {

    @Test
    fun `blank custom ringtone falls back to global`() {
        assertNull(RingOptionPolicy.resolveUri(null))
        assertNull(RingOptionPolicy.resolveUri("   "))
    }

    @Test
    fun `custom ringtone is kept as is`() {
        assertEquals("content://alarms/1", RingOptionPolicy.resolveUri("content://alarms/1"))
    }

    @Test
    fun `ramp starts from thirty percent and reaches full volume`() {
        assertEquals(0.3f, RingOptionPolicy.rampVolume(0), 0.001f)
        assertEquals(0.4f, RingOptionPolicy.rampVolume(1), 0.001f)
        assertEquals(1.0f, RingOptionPolicy.rampVolume(7), 0.001f)
        assertEquals(1.0f, RingOptionPolicy.rampVolume(50), 0.001f)
    }

    @Test
    fun `ramp takes seven steps to full volume`() {
        assertEquals(7, RingOptionPolicy.rampSteps())
    }
}
