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
        hasNextTrigger: Boolean,
        /** 这条按日期闹钟的日期已经过去（含被假期静音后过期的情况）。 */
        datePassed: Boolean = false
    ): DateAlarmState = when {
        !enabled -> DateAlarmState.DISABLED
        // 日期已过就应该显示「已过期」：否则历史假期里被静音的闹钟会永远显示「假期静音」
        datePassed -> DateAlarmState.EXPIRED
        hasNextTrigger -> DateAlarmState.PENDING
        mutedDuringHoliday && holidayModeEnabled && inHoliday -> DateAlarmState.HOLIDAY_MUTED
        else -> DateAlarmState.EXPIRED
    }

    /** 状态文案资源 id（界面按当前语言解析）。 */
    fun labelRes(state: DateAlarmState): Int = when (state) {
        DateAlarmState.DISABLED -> com.cyclealarm.app.R.string.state_disabled
        DateAlarmState.EXPIRED -> com.cyclealarm.app.R.string.state_expired
        DateAlarmState.HOLIDAY_MUTED -> com.cyclealarm.app.R.string.state_holiday_muted
        DateAlarmState.PENDING -> com.cyclealarm.app.R.string.state_pending
    }
}
