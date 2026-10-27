package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity

object AlarmEditPolicy {
    fun detachFromPlan(alarm: AlarmEntity): AlarmEntity =
        alarm.copy(planGenerated = false)
}
