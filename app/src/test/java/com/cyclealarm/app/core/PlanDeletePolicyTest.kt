package com.cyclealarm.app.core

import com.cyclealarm.app.data.DayPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDeletePolicyTest {

    private fun plan(day: Int, typeId: Int) = DayPlanEntity(
        dayIndex = day,
        typeId = typeId,
        hour = 7,
        minute = 30,
        enabled = true
    )

    @Test
    fun `only plans of the deleted type are captured`() {
        val plans = listOf(plan(0, 1), plan(1, 2), plan(2, 1))
        val captured = PlanDeletePolicy.plansUsingType(plans, typeId = 1)
        assertEquals(listOf(0, 2), captured.map { it.dayIndex })
    }

    @Test
    fun `unused type captures nothing`() {
        val plans = listOf(plan(0, 1))
        assertTrue(PlanDeletePolicy.plansUsingType(plans, typeId = 9).isEmpty())
    }
}
