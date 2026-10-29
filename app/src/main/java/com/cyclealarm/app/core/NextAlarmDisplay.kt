package com.cyclealarm.app.core

import java.time.Instant
import java.time.ZoneId

object NextAlarmDisplay {
    fun countdownText(
        triggerAt: Long,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val remaining = (triggerAt - now).coerceAtLeast(0)
        if (remaining < 60_000L) return "即将响铃"

        val totalMinutes = (remaining + 59_999L) / 60_000L
        if (totalMinutes < 60) return "还有 $totalMinutes 分钟"

        val nowDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val triggerDate = Instant.ofEpochMilli(triggerAt).atZone(zoneId).toLocalDate()
        val dayLabel = when (triggerDate) {
            nowDate -> "今天"
            nowDate.plusDays(1) -> "明天"
            else -> "${triggerDate.monthValue} 月 ${triggerDate.dayOfMonth} 日"
        }
        // 超过 48 小时不再用「还有 154 小时」这种读不出意义的数字，改成按天
        if (totalMinutes >= 48 * 60) {
            val days = remaining / (24 * 60 * 60_000L)
            return "$dayLabel · 还有 $days 天"
        }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val remainingText = if (minutes > 0) {
            "还有 $hours 小时 $minutes 分"
        } else {
            "还有 $hours 小时"
        }
        return "$dayLabel · $remainingText"
    }
}
