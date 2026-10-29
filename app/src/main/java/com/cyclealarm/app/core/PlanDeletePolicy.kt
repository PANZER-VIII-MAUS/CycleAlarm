package com.cyclealarm.app.core

import com.cyclealarm.app.data.DayPlanEntity

/**
 * 删除撤销的纯逻辑：删除前需要保存哪些数据。
 *
 * - 删除某天的安排：保存该 `day_plan` 行本身；
 * - 删除安排类型：保存类型行 + 所有引用它的 `day_plan` 行（删除会连带把它们清掉）。
 */
object PlanDeletePolicy {

    fun plansUsingType(all: List<DayPlanEntity>, typeId: Int): List<DayPlanEntity> =
        all.filter { it.typeId == typeId }
}
