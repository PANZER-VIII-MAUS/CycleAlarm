package com.cyclealarm.app.core

import com.cyclealarm.app.data.DayPlanEntity

/**
 * 安排选择器一次点击应该产生什么动作。
 *
 * 规则：没有安排 → 选类型；点了当前类型 → 进「改时间」；点了别的类型 → 换类型。
 * 抽成纯逻辑，避免把"当前类型"的判定散落到 UI 里且无法测试。
 */
sealed interface PlanPickerAction {
    /** 把这一天改成目标类型（时间取该类型的默认值）。 */
    data class ChangePlan(val typeId: Int) : PlanPickerAction

    /** 当前类型被再次点击：进入改时间对话框。 */
    data object EditCurrentTime : PlanPickerAction

    companion object {
        fun forTypeClick(current: DayPlanEntity?, clickedTypeId: Int): PlanPickerAction =
            if (current != null && current.typeId == clickedTypeId) {
                EditCurrentTime
            } else {
                ChangePlan(clickedTypeId)
            }
    }
}
