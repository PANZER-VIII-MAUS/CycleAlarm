package com.cyclealarm.app.core

import java.time.ZoneId
import java.time.Instant

data class SnoozeTarget(
    val triggerAt: Long,
    val hour: Int,
    val minute: Int
)

object SnoozeTimePolicy {
    fun target(
        now: Long,
        minutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SnoozeTarget {
        val triggerAt = now + minutes * 60_000L
        val local = Instant.ofEpochMilli(triggerAt).atZone(zoneId).toLocalTime()
        return SnoozeTarget(triggerAt, local.hour, local.minute)
    }

    fun shouldRestore(triggerAt: Long, now: Long): Boolean =
        triggerAt > now
}
