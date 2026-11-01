package com.cyclealarm.app.core

import com.cyclealarm.app.R
import java.time.Instant
import java.time.ZoneId

object NextAlarmDisplay {
    fun countdownText(
        context: android.content.Context,
        triggerAt: Long,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val remaining = (triggerAt - now).coerceAtLeast(0)
        if (remaining < 60_000L) return context.getString(R.string.countdown_soon)

        val totalMinutes = (remaining + 59_999L) / 60_000L
        if (totalMinutes < 60) return context.getString(R.string.countdown_minutes_left, totalMinutes)

        val nowDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val triggerDate = Instant.ofEpochMilli(triggerAt).atZone(zoneId).toLocalDate()
        val dayLabel = when (triggerDate) {
            nowDate -> context.getString(R.string.countdown_today)
            nowDate.plusDays(1) -> context.getString(R.string.countdown_tomorrow)
            else -> context.getString(
                R.string.countdown_date,
                triggerDate.monthValue,
                triggerDate.dayOfMonth
            )
        }
        // 超过 48 小时不再用「还有 154 小时」这种读不出意义的数字，改成按天
        if (totalMinutes >= 24 * 60) {
            // 超过 24 小时按「N 天 M 小时」显示：既单调，也不会出现「47 小时」跳到「2 天」的怪数字
            val dayMs = 24 * 60 * 60_000L
            val days = remaining / dayMs
            val hoursLeft = (remaining % dayMs) / (60 * 60_000L)
            return if (hoursLeft > 0L) {
                context.getString(R.string.countdown_days_hours, dayLabel, days, hoursLeft)
            } else {
                context.getString(R.string.countdown_days, dayLabel, days)
            }
        }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val remainingText = if (minutes > 0) {
            context.getString(R.string.countdown_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.countdown_hours, hours)
        }
        return "$dayLabel · $remainingText"
    }
}
