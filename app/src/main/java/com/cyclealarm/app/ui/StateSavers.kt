package com.cyclealarm.app.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity

val AlarmEntitySaver: Saver<AlarmEntity?, Any> = mapSaver(
    save = { alarm ->
        if (alarm == null) {
            mapOf("present" to false)
        } else {
            mapOf(
                "present" to true,
                "id" to alarm.id,
                "dayIndex" to alarm.dayIndex,
                "hour" to alarm.hour,
                "minute" to alarm.minute,
                "enabled" to alarm.enabled,
                "muted" to alarm.mutedDuringHoliday,
                "label" to alarm.label,
                "planGenerated" to alarm.planGenerated
            )
        }
    },
    restore = { values ->
        if (values["present"] != true) {
            null
        } else {
            AlarmEntity(
                id = values["id"] as Int,
                dayIndex = values["dayIndex"] as Int,
                hour = values["hour"] as Int,
                minute = values["minute"] as Int,
                enabled = values["enabled"] as Boolean,
                mutedDuringHoliday = values["muted"] as Boolean,
                label = values["label"] as String,
                planGenerated = values["planGenerated"] as Boolean
            )
        }
    }
)

val HolidayEntitySaver: Saver<HolidayEntity?, Any> = mapSaver(
    save = { holiday ->
        if (holiday == null) {
            mapOf("present" to false)
        } else {
            mapOf(
                "present" to true,
                "id" to holiday.id,
                "start" to holiday.startEpochDay,
                "end" to holiday.endEpochDay,
                "enabled" to holiday.enabled,
                "label" to holiday.label
            )
        }
    },
    restore = { values ->
        if (values["present"] != true) {
            null
        } else {
            HolidayEntity(
                id = values["id"] as Int,
                startEpochDay = values["start"] as Long,
                endEpochDay = values["end"] as Long,
                enabled = values["enabled"] as Boolean,
                label = values["label"] as String
            )
        }
    }
)

val DateAlarmEntitySaver: Saver<DateAlarmEntity?, Any> = mapSaver(
    save = { alarm ->
        if (alarm == null) {
            mapOf("present" to false)
        } else {
            mapOf(
                "present" to true,
                "id" to alarm.id,
                "epochDay" to alarm.epochDay,
                "hour" to alarm.hour,
                "minute" to alarm.minute,
                "enabled" to alarm.enabled,
                "muted" to alarm.mutedDuringHoliday,
                "label" to alarm.label
            )
        }
    },
    restore = { values ->
        if (values["present"] != true) {
            null
        } else {
            DateAlarmEntity(
                id = values["id"] as Int,
                epochDay = values["epochDay"] as Long,
                hour = values["hour"] as Int,
                minute = values["minute"] as Int,
                enabled = values["enabled"] as Boolean,
                mutedDuringHoliday = values["muted"] as Boolean,
                label = values["label"] as String
            )
        }
    }
)

val ActivityTypeEntitySaver: Saver<ActivityTypeEntity?, Any> = mapSaver(
    save = { type ->
        if (type == null) {
            mapOf("present" to false)
        } else {
            mapOf(
                "present" to true,
                "id" to type.id,
                "name" to type.name,
                "hour" to type.hour,
                "minute" to type.minute,
                "rings" to type.rings,
                "sortOrder" to type.sortOrder
            )
        }
    },
    restore = { values ->
        if (values["present"] != true) {
            null
        } else {
            ActivityTypeEntity(
                id = values["id"] as Int,
                name = values["name"] as String,
                hour = values["hour"] as Int,
                minute = values["minute"] as Int,
                rings = values["rings"] as Boolean,
                sortOrder = values["sortOrder"] as Int
            )
        }
    }
)
