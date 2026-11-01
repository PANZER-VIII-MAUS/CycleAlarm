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
    fun `pre alert text is built from resources in the receiver`() {
        // 文案已移到 AlarmReceiver + strings.xml（pre_alert_text），策略类只负责算时间
        assertNull(PreAlertPolicy.preAlertAt(0L, 0, 0L))
    }
}
