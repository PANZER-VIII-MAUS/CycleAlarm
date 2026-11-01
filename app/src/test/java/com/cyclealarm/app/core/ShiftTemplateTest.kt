package com.cyclealarm.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        assertEquals(
            com.cyclealarm.app.R.string.shift_white_night_name,
            ShiftTemplates.byKey("white_night_rest_rest")?.nameRes
        )
        assertNull(ShiftTemplates.byKey("nope"))
    }

    @Test
    fun `four crew template has six shifts in eight days`() {
        val template = ShiftTemplates.byKey("four_crew_three_shift")!!
        assertEquals(8, template.cycleLength)
        assertEquals(6, template.slots.size)
        assertEquals(
            listOf(
                com.cyclealarm.app.R.string.shift_label_day,
                com.cyclealarm.app.R.string.shift_label_day,
                com.cyclealarm.app.R.string.shift_label_middle,
                com.cyclealarm.app.R.string.shift_label_middle,
                com.cyclealarm.app.R.string.shift_label_night,
                com.cyclealarm.app.R.string.shift_label_night
            ),
            template.slots.map { it.labelRes }
        )
    }

    @Test
    fun `alarms are built with template times and labels`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val alarms = ShiftTemplates.toAlarms(context, ShiftTemplates.byKey("white_night_rest_rest")!!)

        assertEquals(2, alarms.size)
        assertEquals(8, alarms[0].hour)
        assertEquals(context.getString(com.cyclealarm.app.R.string.shift_label_day), alarms[0].label)
        assertEquals(20, alarms[1].hour)
        assertEquals(1, alarms[1].dayIndex)
        assertEquals(0, alarms[0].id)
    }
}
