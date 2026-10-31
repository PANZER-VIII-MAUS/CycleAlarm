package com.cyclealarm.app.core

/**
 * 漏响判定的合并策略（纯函数）。
 *
 * 两路来源合并：
 * 1) 计算式：按日程算出「本该响」的时间点（覆盖关机导致事件没登记）；
 * 2) 事件式：响铃事件表里登记了、但 `rungAt` 为空（覆盖响铃服务启动失败）。
 *
 * 这里只做窗口过滤、去重与排序，不碰数据库，方便单测。
 */
object MissedAlarmMergePolicy {

    fun merge(
        computed: List<MissedAlarm>,
        fromEvents: List<MissedAlarm>,
        lastCheckAt: Long
    ): List<MissedAlarm> = (computed + fromEvents)
        .filter { it.expectedAt > lastCheckAt }
        .distinctBy { it.kind to it.expectedAt }
        .sortedBy { it.expectedAt }
}
