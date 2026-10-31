package com.cyclealarm.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 桌面小组件的纯文本逻辑：把「下次响铃」渲染成两行文字。
 * 与 Android/RemoteViews 无关，可直接单测。
 */
object WidgetTextPolicy {

    const val EMPTY_TITLE = "暂无响铃"
    const val EMPTY_SUBTITLE = "打开 App 添加或打开某一天"

    data class WidgetText(val time: String, val subtitle: String)

    fun timeText(
        hour: Int,
        minute: Int,
        is24Hour: Boolean = true,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm"
        val formatter = SimpleDateFormat(pattern, Locale.CHINA)
        formatter.timeZone = zone
        val calendar = java.util.Calendar.getInstance(zone).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
        }
        return formatter.format(Date(calendar.timeInMillis))
    }

    /**
     * @param next 下次响铃；null 表示当前没有开启的闹钟
     */
    fun render(
        next: NextAlarm?,
        now: Long,
        is24Hour: Boolean = true,
        zone: TimeZone = TimeZone.getDefault()
    ): WidgetText {
        if (next == null) {
            return WidgetText(time = EMPTY_TITLE, subtitle = EMPTY_SUBTITLE)
        }
        val time = timeText(next.hour, next.minute, is24Hour, zone)
        val amPm = if (is24Hour) {
            ""
        } else {
            val label = if (next.hour < 12) "上午" else "下午"
            " $label"
        }
        val countdown = NextAlarmDisplay.countdownText(next.triggerAt, now)
        // 小组件格子小：去掉日期前缀（「11 月 2 日 · 」），只留「还有 X」，避免被截断
        val compact = countdown.substringAfter("·", missingDelimiterValue = countdown).trim()
        return WidgetText(time = time + amPm, subtitle = compact)
    }
}
