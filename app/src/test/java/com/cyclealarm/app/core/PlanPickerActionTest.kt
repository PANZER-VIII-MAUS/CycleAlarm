package com.cyclealarm.app.core

import com.cyclealarm.app.data.DayPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanPickerActionTest {

    private fun plan(typeId: Int) = DayPlanEntity(
        dayIndex = 0,
        typeId = typeId,
        hour = 7,
        minute = 30,
        enabled = true
    )

    @Test
    fun `no plan yet means choose the clicked type`() {
        assertEquals(
            PlanPickerAction.ChangePlan(3),
            PlanPickerAction.forTypeClick(null, 3)
        )
    }

    @Test
    fun `clicking current type opens time editor`() {
        assertEquals(
            PlanPickerAction.EditCurrentTime,
            PlanPickerAction.forTypeClick(plan(2), 2)
        )
    }

    @Test
    fun `clicking another type switches plan`() {
        assertEquals(
            PlanPickerAction.ChangePlan(5),
            PlanPickerAction.forTypeClick(plan(2), 5)
        )
    }
}
