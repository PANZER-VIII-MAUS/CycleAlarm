package com.cyclealarm.app.core

/** 按日期闹钟的显示状态：区分「已过期」与「假期静音导致本次不响」。 */
enum class DateAlarmState {
    DISABLED,
    EXPIRED,
    HOLIDAY_MUTED,
    PENDING
}

object DateAlarmStatus {

    /**
     * @param enabled 闹钟开关
     * @param mutedDuringHoliday 该闹钟是否勾选「假期静音」
     * @param holidayModeEnabled 假期模式总开关
     * @param inHoliday 今天（或它的触发日）是否落在假期区间
     * @param hasNextTrigger AlarmScheduler 算出来的下一次触发是否为 null
     */
    fun resolve(
        enabled: Boolean,
        mutedDuringHoliday: Boolean,
        holidayModeEnabled: Boolean,
        inHoliday: Boolean,
        hasNextTrigger: Boolean
    ): DateAlarmState = when {
        !enabled -> DateAlarmState.DISABLED
        hasNextTrigger -> DateAlarmState.PENDING
        mutedDuringHoliday && holidayModeEnabled && inHoliday -> DateAlarmState.HOLIDAY_MUTED
        else -> DateAlarmState.EXPIRED
    }

    fun label(state: DateAlarmState): String = when (state) {
        DateAlarmState.DISABLED -> "已停用"
        DateAlarmState.EXPIRED -> "已过期"
        DateAlarmState.HOLIDAY_MUTED -> "假期静音"
        DateAlarmState.PENDING -> "待响铃"
    }
}
