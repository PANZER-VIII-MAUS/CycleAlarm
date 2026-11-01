package com.cyclealarm.app.core

/**
 * 「提前提醒」的纯逻辑：算出提醒时间与文案。
 * 提醒只是通知，不响铃；正点仍由原闹钟正常响。
 */
object PreAlertPolicy {

    /** 提前提醒分钟数的可选项。 */
    val OPTIONS = listOf(0, 5, 10, 15, 30)

    /**
     * @return 提醒触发时间；关闭（minutes<=0）或已经过了则返回 null
     */
    fun preAlertAt(triggerAt: Long, minutes: Int, now: Long): Long? {
        if (minutes <= 0) return null
        val at = triggerAt - minutes * 60_000L
        return at.takeIf { it > now }
    }

}
