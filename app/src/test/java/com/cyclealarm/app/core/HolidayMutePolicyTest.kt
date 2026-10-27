package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayMutePolicyTest {

    @Test
    fun `mutes only when all three conditions are true`() {
        assertTrue(HolidayMutePolicy.shouldMute(true, true, true))
    }

    @Test
    fun `global holiday mode off prevents muting`() {
        assertFalse(HolidayMutePolicy.shouldMute(false, true, true))
    }

    @Test
    fun `per alarm mute off prevents muting`() {
        assertFalse(HolidayMutePolicy.shouldMute(true, false, true))
    }

    @Test
    fun `date outside holiday range prevents muting`() {
        assertFalse(HolidayMutePolicy.shouldMute(true, true, false))
    }
}
