package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPreviewPolicyTest {

    @Test
    fun `real ring allows snooze and database writes`() {
        assertTrue(RingPreviewPolicy.allowSnooze(isPreview = false))
        assertTrue(RingPreviewPolicy.allowDatabaseWrites(isPreview = false))
    }

    @Test
    fun `preview forbids snooze and database writes`() {
        assertFalse(RingPreviewPolicy.allowSnooze(isPreview = true))
        assertFalse(RingPreviewPolicy.allowDatabaseWrites(isPreview = true))
    }
}
