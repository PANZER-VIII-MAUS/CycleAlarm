package com.cyclealarm.app.data

/**
 * 「排班分享」的纯文本生成：把当前周期与闹钟排成一段可粘贴的文字。
 * 不依赖 Android，可直接单测。
 */
object ScheduleShareText {

    /**
     * @param settings 当前设置（周期天数/方式）
     * @param alarms 周期闹钟（按 id 排序后输出）
     * @param dateAlarms 按日期闹钟
     */
    fun build(
        settings: AppSettingsEntity?,
        alarms: List<AlarmEntity>,
        dateAlarms: List<DateAlarmEntity>
    ): String = buildString {
        appendLine("我的闹钟排班（来自循环闹钟）")
        if (settings == null) {
            appendLine("（还没有任何设置）")
            return@buildString
        }
        val typeLabel = when (settings.cycleType) {
            CycleTypes.DAILY -> "每天"
            CycleTypes.WEEKLY -> "每周"
            else -> "${settings.cycleLength} 天一轮"
        }
        appendLine("循环：$typeLabel")
        if (alarms.isEmpty()) {
            appendLine("周期闹钟：无")
        } else {
            appendLine("周期闹钟：")
            alarms.sortedWith(compareBy({ it.dayIndex }, { it.hour }, { it.minute })).forEach { alarm ->
                appendLine(
                    "  第 ${alarm.dayIndex + 1} 天 ${clock(alarm.hour, alarm.minute)}" +
                        alarm.label.let { if (it.isBlank()) "" else " $it" }
                )
            }
        }
        if (dateAlarms.isNotEmpty()) {
            appendLine("按日期闹钟：")
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
