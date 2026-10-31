package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftTemplateTest {

    @Test
    fun `templates cover the whole cycle or less`() {
        ShiftTemplates.ALL.forEach { template ->
            assertTrue("周期天数必须为正", template.cycleLength > 0)
            template.slots.forEach { slot ->
                assertTrue("dayIndex 越界: ${template.key}", slot.dayIndex in 0 until template.cycleLength)
            }
        }
    }

    @Test
    fun `keys are unique and lookup works`() {
        val keys = ShiftTemplates.ALL.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals("白夜休休", ShiftTemplates.byKey("white_night_rest_rest")?.name)
        assertNull(ShiftTemplates.byKey("nope"))
    }

    @Test
    fun `four crew template has six shifts in eight days`() {
        val template = ShiftTemplates.byKey("four_crew_three_shift")!!
        assertEquals(8, template.cycleLength)
        assertEquals(6, template.slots.size)
        assertEquals(listOf("白班", "白班", "中班", "中班", "夜班", "夜班"), template.slots.map { it.label })
    }

    @Test
    fun `alarms are built with template times and labels`() {
        val alarms = ShiftTemplates.toAlarms(ShiftTemplates.byKey("white_night_rest_rest")!!)

        assertEquals(2, alarms.size)
        assertEquals(8, alarms[0].hour)
        assertEquals("白班", alarms[0].label)
        assertEquals(20, alarms[1].hour)
        assertEquals(1, alarms[1].dayIndex)
        assertEquals(0, alarms[0].id)
    }
}
