package com.cyclealarm.app.core

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootResyncPolicyTest {

    @Test
    fun `system time change requires resync`() {
        assertTrue(BootResyncPolicy.shouldResync(Intent.ACTION_TIME_CHANGED))
    }

    @Test
    fun `boot upgrade and timezone changes require resync`() {
        assertTrue(BootResyncPolicy.shouldResync(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootResyncPolicy.shouldResync(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(BootResyncPolicy.shouldResync(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `unrelated and missing actions do not require resync`() {
        assertFalse(BootResyncPolicy.shouldResync(Intent.ACTION_SCREEN_ON))
        assertFalse(BootResyncPolicy.shouldResync(null))
    }
}
