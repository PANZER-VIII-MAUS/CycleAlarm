package com.cyclealarm.app.core

import android.content.Intent

/** Pure decision for system events that require an alarm reschedule. */
object BootResyncPolicy {

    /** 系统下一次闹钟变化的广播（无公开常量，用字符串）。 */
    const val ACTION_NEXT_ALARM_CLOCK_CHANGED = "android.app.action.NEXT_ALARM_CLOCK_CHANGED"

    /**
     * 需要「整轮重排」的系统事件。
     *
     * 注意：NEXT_ALARM_CLOCK_CHANGED **不能**走整轮重排——App 自己重排会改变系统的
     * nextAlarmClock，从而再次触发该广播，形成「重排 → 广播 → 重排」死循环（0.2.3 实测刷屏）。
     * 该广播只做「对账」，见 AlarmResyncHelper.auditNextAlarm()。
     */
    fun shouldResync(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED

    /** 该广播是否只需对账。 */
    fun shouldAuditOnly(action: String?): Boolean = action == ACTION_NEXT_ALARM_CLOCK_CHANGED
}
