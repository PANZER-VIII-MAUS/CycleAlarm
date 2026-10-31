package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreAlertPolicyTest {

    private val now = 1_800_000_000_000L
    private val trigger = now + 60 * 60_000L

    @Test
    fun `disabled when minutes is zero or negative`() {
        assertNull(PreAlertPolicy.preAlertAt(trigger, 0, now))
        assertNull(PreAlertPolicy.preAlertAt(trigger, -5, now))
    }

    @Test
    fun `pre alert fires minutes before the alarm`() {
        assertEquals(
            trigger - 15 * 60_000L,
            PreAlertPolicy.preAlertAt(trigger, 15, now)
        )
    }

    @Test
    fun `no pre alert when the moment already passed`() {
        assertNull(PreAlertPolicy.preAlertAt(now + 60_000L, 5, now))
    }

    @Test
    fun `text mentions minutes and clock time`() {
        assertEquals(
            "10 分钟后（07:30）：早课",
            PreAlertPolicy.text(7, 30, "早课", 10)
        )
        assertEquals(
            "10 分钟后（07:30）：闹钟",
            PreAlertPolicy.text(7, 30, "", 10)
        )
    }
}
