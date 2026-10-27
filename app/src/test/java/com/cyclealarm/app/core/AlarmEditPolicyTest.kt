package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmEditPolicyTest {

    @Test
    fun `saving a generated alarm detaches it from future plan updates`() {
        val original = AlarmEntity(
            id = 7,
            dayIndex = 1,
            hour = 7,
            minute = 30,
            planGenerated = true
        )

        val edited = AlarmEditPolicy.detachFromPlan(
            original.copy(hour = 8, minute = 0, label = "手动修改")
        )

        assertEquals(original.id, edited.id)
        assertEquals(8, edited.hour)
        assertEquals("手动修改", edited.label)
        assertFalse(edited.planGenerated)
    }
}
