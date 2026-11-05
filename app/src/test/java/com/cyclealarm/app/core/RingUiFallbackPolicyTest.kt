package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 响铃界面兜底策略（真机定位：ColorOS 上 Activity 被 BAL 拦、FSI 被拒）。
 */
class RingUiFallbackPolicyTest {

    @Test
    fun `shows overlay when activity was blocked and overlay permitted`() {
        assertTrue(
            RingUiFallbackPolicy.shouldShowOverlay(
                activityVisible = false,
                overlayPermitted = true,
                preview = false
            )
        )
    }

    @Test
    fun `does not show overlay when the ring screen really appeared`() {
        assertFalse(
            RingUiFallbackPolicy.shouldShowOverlay(
                activityVisible = true,
                overlayPermitted = true,
                preview = false
            )
        )
    }

    @Test
    fun `does not show overlay without overlay permission`() {
        assertFalse(
            RingUiFallbackPolicy.shouldShowOverlay(
                activityVisible = false,
                overlayPermitted = false,
                preview = false
            )
        )
    }

    @Test
    fun `preview never shows the overlay`() {
        assertFalse(
            RingUiFallbackPolicy.shouldShowOverlay(
                activityVisible = false,
                overlayPermitted = true,
                preview = true
            )
        )
    }
}
