package com.cyclealarm.app.data

import android.content.Context
import com.cyclealarm.app.R

/**
 * 「排班分享」的纯文本生成：把当前周期与闹钟排成一段可粘贴的文字。
 */
object ScheduleShareText {

    fun build(
        context: Context,
        settings: AppSettingsEntity?,
        alarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>
    ): String = buildString {
        appendLine(context.getString(R.string.share_title_line))
        if (settings == null) {
            appendLine(context.getString(R.string.share_no_settings))
            return@buildString
        }
        val typeLabel = when (settings.cycleType) {
            CycleTypes.DAILY -> context.getString(R.string.cycle_daily)
            CycleTypes.WEEKLY -> context.getString(R.string.cycle_weekly)
            else -> context.getString(R.string.share_cycle_days, settings.cycleLength)
        }
        appendLine(context.getString(R.string.share_cycle_label, typeLabel))
        if (alarms.isEmpty()) {
            appendLine(context.getString(R.string.share_no_cycle_alarms))
        } else {
            appendLine(context.getString(R.string.share_cycle_alarms))
            alarms.sortedWith(compareBy({ it.dayIndex }, { it.hour }, { it.minute })).forEach { alarm ->
                appendLine(
                    context.getString(
                        R.string.share_cycle_alarm_line,
                        alarm.dayIndex + 1,
                        clock(alarm.hour, alarm.minute)
                    ) + alarm.label.let { if (it.isBlank()) "" else " $it" }
                )
            }
        }
        if (dateAlarms.isNotEmpty()) {
            appendLine(context.getString(R.string.share_date_alarms))
            dateAlarms.sortedWith(compareBy({ it.epochDay }, { it.hour }, { it.minute })).forEach { alarm ->
                appendLine(
                    "  ${day(alarm.epochDay)} ${clock(alarm.hour, alarm.minute)}" +
                        alarm.label.let { if (it.isBlank()) "" else " $it" }
                )
            }
        }
    }

    private fun clock(hour: Int, minute: Int): String =
        String.format(java.util.Locale.CHINA, "%02d:%02d", hour, minute)

    private fun day(epochDay: Long): String =
        java.time.LocalDate.ofEpochDay(epochDay).toString()
}
