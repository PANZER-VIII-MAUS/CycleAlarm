package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DateAlarmStatusTest {

    @Test
    fun `disabled wins over everything`() {
        assertEquals(
            DateAlarmState.DISABLED,
            DateAlarmStatus.resolve(
                enabled = false,
                mutedDuringHoliday = true,
                holidayModeEnabled = true,
                inHoliday = true,
                hasNextTrigger = false
            )
        )
    }

    @Test
    fun `pending when a trigger exists`() {
        assertEquals(
            DateAlarmState.PENDING,
            DateAlarmStatus.resolve(true, true, true, true, hasNextTrigger = true)
        )
    }

    @Test
    fun `holiday muted is not reported as expired`() {
        assertEquals(
            DateAlarmState.HOLIDAY_MUTED,
            DateAlarmStatus.resolve(true, true, true, inHoliday = true, hasNextTrigger = false)
        )
    }

    @Test
    fun `no trigger outside holiday is expired`() {
        assertEquals(
            DateAlarmState.EXPIRED,
            DateAlarmStatus.resolve(true, true, true, inHoliday = false, hasNextTrigger = false)
        )
    }
}
