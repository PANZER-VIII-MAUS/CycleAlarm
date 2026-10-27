package com.cyclealarm.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSeedPolicyTest {

    @Test
    fun `creates defaults only when alarms and plans are both empty`() {
        assertTrue(PlanSeedPolicy.shouldCreateDefaultPlans(alarmCount = 0, dayPlanCount = 0))
    }

    @Test
    fun `preserves existing alarms during upgrade`() {
        assertFalse(PlanSeedPolicy.shouldCreateDefaultPlans(alarmCount = 1, dayPlanCount = 0))
    }

    @Test
    fun `does not rebuild defaults when plans already exist`() {
        assertFalse(PlanSeedPolicy.shouldCreateDefaultPlans(alarmCount = 0, dayPlanCount = 1))
    }

    @Test
    fun `does not rebuild defaults when both tables contain data`() {
        assertFalse(PlanSeedPolicy.shouldCreateDefaultPlans(alarmCount = 2, dayPlanCount = 3))
    }
}
