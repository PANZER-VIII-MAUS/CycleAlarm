package com.cyclealarm.app.core

/**
 * 安排的作用域：只有 `dayIndex < cycleLength` 的安排才参与排期。
 *
 * 背景：导入周课表会把循环改成 7 天。此前实现是**直接删掉**第 8 天以后的
 * `day_plans`（0.2.2 为修幽灵闹钟加的），但撤销快照不含 `day_plans` →
 * 用户的安排会永久丢失（三轮复审 §3.2）。改为「保留数据、排期时过滤」：
 * 切回自定义周期后这些安排原样恢复生效。
 */
object DayPlanScope {

    fun <T> inCurrentCycle(
        items: List<T>,
        cycleLength: Int,
        dayIndexOf: (T) -> Int
    ): List<T> = if (cycleLength <= 0) {
        items
    } else {
        items.filter { dayIndexOf(it) < cycleLength }
    }
}
