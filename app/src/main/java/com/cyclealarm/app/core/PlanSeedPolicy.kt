package com.cyclealarm.app.core

/** Pure upgrade policy for the one-time plan seed. */
object PlanSeedPolicy {
    fun shouldCreateDefaultPlans(alarmCount: Int, dayPlanCount: Int): Boolean =
        alarmCount == 0 && dayPlanCount == 0
}
