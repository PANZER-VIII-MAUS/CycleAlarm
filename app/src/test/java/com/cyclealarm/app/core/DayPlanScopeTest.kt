package com.cyclealarm.app.core

import com.cyclealarm.app.data.DayPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanScopeTest {

    private fun plan(day: Int) = DayPlanEntity(
        dayIndex = day,
        typeId = 1,
        hour = 7,
        minute = 30,
        enabled = true
    )

    @Test
    fun `plans beyond the cycle length are filtered out`() {
        val plans = listOf(plan(0), plan(6), plan(7), plan(9))
        val scoped = DayPlanScope.inCurrentCycle(plans, cycleLength = 7) { it.dayIndex }
        assertEquals(listOf(0, 6), scoped.map { it.dayIndex })
    }

    @Test
    fun `all plans are kept when cycle covers them`() {
        val plans = listOf(plan(0), plan(9))
        val scoped = DayPlanScope.inCurrentCycle(plans, cycleLength = 10) { it.dayIndex }
        assertEquals(2, scoped.size)
    }

    @Test
    fun `invalid cycle length does not drop data`() {
        assertTrue(DayPlanScope.inCurrentCycle(listOf(plan(9)), 0) { it.dayIndex }.isNotEmpty())
    }
}
