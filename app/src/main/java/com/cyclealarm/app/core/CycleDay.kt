package com.cyclealarm.app.core

import com.cyclealarm.app.data.AppSettingsEntity

fun cycleDayOf(settings: AppSettingsEntity, epochDay: Long): Int =
    Math.floorMod(
        epochDay - settings.cycleStartEpochDay,
        settings.cycleLength.coerceAtLeast(1).toLong()
    ).toInt() + 1
