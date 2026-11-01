package com.cyclealarm.app.core

import com.cyclealarm.app.data.RingEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 响铃事件在历史页里的三种状态。 */
enum class RingHistoryStatus {
    /** 响过，且用户处理了（停止 / 稍后）。 */
    ACKED,

    /** 响过，但用户没处理（响满上限自动停止）。 */
    UNHANDLED,

    /** 没有真的响（漏响）。 */
    MISSED
}

/**
 * 响铃历史页的纯展示逻辑：状态归类、文案、统计。
 * 与 Compose / Android 无关，可直接单测。
 */
object RingHistoryFormatter {

    fun statusOf(event: RingEventEntity): RingHistoryStatus = when {
        event.rungAt == null -> RingHistoryStatus.MISSED
        event.ackedAt == null -> RingHistoryStatus.UNHANDLED
        else -> RingHistoryStatus.ACKED
    }

    /** 状态对应的文案资源 id（展示时由 UI 用当前语言解析）。 */
    fun labelRes(status: RingHistoryStatus): Int = when (status) {
        RingHistoryStatus.ACKED -> com.cyclealarm.app.R.string.history_acked
        RingHistoryStatus.UNHANDLED -> com.cyclealarm.app.R.string.history_unhandled
        RingHistoryStatus.MISSED -> com.cyclealarm.app.R.string.history_missed
    }

    /** 例如「10-31 07:30」。 */
    fun timeText(
        expectedAt: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        formatter.timeZone = zone
        return formatter.format(Date(expectedAt))
    }

    data class Summary(val acked: Int, val unhandled: Int, val missed: Int) {
        val total: Int get() = acked + unhandled + missed
    }

    fun summary(events: List<RingEventEntity>): Summary {
        var acked = 0
        var unhandled = 0
        var missed = 0
        events.forEach { event ->
            when (statusOf(event)) {
                RingHistoryStatus.ACKED -> acked++
                RingHistoryStatus.UNHANDLED -> unhandled++
                RingHistoryStatus.MISSED -> missed++
            }
        }
        return Summary(acked, unhandled, missed)
    }
}
