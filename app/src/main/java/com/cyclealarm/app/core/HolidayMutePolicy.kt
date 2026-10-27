package com.cyclealarm.app.core

object HolidayMutePolicy {
    fun shouldMute(
        holidayModeEnabled: Boolean,
        alarmMutedDuringHoliday: Boolean,
        inHolidayRange: Boolean
    ): Boolean = holidayModeEnabled && alarmMutedDuringHoliday && inHolidayRange
}
