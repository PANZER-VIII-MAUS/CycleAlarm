package com.cyclealarm.app.core

/** Validates the data required before a ring screen or snooze request is created. */
object RingLaunchValidator {
    fun hasValidAlarmId(alarmId: Int): Boolean =
        alarmId > 0

    fun isValid(
        alarmId: Int,
        kind: String?,
        dayIndex: Int?,
        hour: Int?,
        minute: Int?
    ): Boolean =
        hasValidAlarmId(alarmId) &&
            !kind.isNullOrBlank() &&
            dayIndex != null && dayIndex >= 0 &&
            hour != null && hour in 0..23 &&
            minute != null && minute in 0..59
}
