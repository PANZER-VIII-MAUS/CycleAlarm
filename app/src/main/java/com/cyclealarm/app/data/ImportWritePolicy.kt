package com.cyclealarm.app.data

/** Pure de-duplication policy for an import write. */
object ImportWritePolicy {
    fun existingCycleKeys(
        alarms: List<AlarmEntity>,
        overwrite: Boolean
    ): Set<Triple<Int, Int, Int>> = if (overwrite) {
        emptySet()
    } else {
        alarms.map { Triple(it.dayIndex, it.hour, it.minute) }.toSet()
    }

    fun existingDateKeys(
        alarms: List<DateAlarmEntity>,
        overwrite: Boolean
    ): Set<Triple<Long, Int, Int>> = if (overwrite) {
        emptySet()
    } else {
        alarms.map { Triple(it.epochDay, it.hour, it.minute) }.toSet()
    }
}
