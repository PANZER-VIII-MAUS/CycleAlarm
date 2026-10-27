package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingLaunchValidatorTest {

    @Test
    fun `accepts complete cycle alarm extras`() {
        assertTrue(
            RingLaunchValidator.isValid(
                alarmId = 7,
                kind = "cycle",
                dayIndex = 2,
                hour = 7,
                minute = 30
            )
        )
    }

    @Test
    fun `accepts date alarm with legitimate day zero`() {
        assertTrue(
            RingLaunchValidator.isValid(
                alarmId = 8,
                kind = "date",
                dayIndex = 0,
                hour = 8,
                minute = 20
            )
        )
    }

    @Test
    fun `rejects missing or invalid alarm id`() {
        assertFalse(RingLaunchValidator.hasValidAlarmId(0))
        assertFalse(RingLaunchValidator.hasValidAlarmId(-1))
        assertFalse(
            RingLaunchValidator.isValid(
                alarmId = 0,
                kind = "cycle",
                dayIndex = 0,
                hour = 8,
                minute = 20
            )
        )
    }

    @Test
    fun `rejects missing required extras`() {
        assertFalse(RingLaunchValidator.isValid(1, null, 0, 8, 20))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", null, 8, 20))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, null, 20))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, 8, null))
    }

    @Test
    fun `rejects out of range time`() {
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, 24, 0))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, -1, 0))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, 8, 60))
        assertFalse(RingLaunchValidator.isValid(1, "cycle", 0, 8, -1))
    }
}
