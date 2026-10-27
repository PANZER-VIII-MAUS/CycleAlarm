package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanSyncTest {

    private val classType = PlanType(id = 1, name = "上课", hour = 7, minute = 30, rings = true)
    private val selfType = PlanType(id = 2, name = "自习", hour = 8, minute = 30, rings = true)
    private val restType = PlanType(id = 3, name = "休息", hour = 0, minute = 0, rings = false)

    private fun planAlarm(
        id: Int,
        dayIndex: Int,
        hour: Int = 7,
        minute: Int = 30,
        label: String = "上课"
    ) = AlarmEntity(
        id = id,
        dayIndex = dayIndex,
        hour = hour,
        minute = minute,
        label = label,
        planGenerated = true
    )

    private fun manualAlarm(id: Int, dayIndex: Int, hour: Int, minute: Int) = AlarmEntity(
        id = id,
        dayIndex = dayIndex,
        hour = hour,
        minute = minute,
        label = "手动",
        planGenerated = false
    )

    @Test
    fun `empty data produces no ops`() {
        assertTrue(DayPlanSync.diff(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `only manual alarms produces no ops`() {
        val alarms = listOf(manualAlarm(1, 0, 7, 0), manualAlarm(2, 1, 8, 0))
        assertTrue(DayPlanSync.diff(emptyList(), listOf(classType), alarms).isEmpty())
    }

    @Test
    fun `new plan inserts alarm`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 7, 30, true)),
            types = listOf(classType),
            alarms = emptyList()
        )
        assertEquals(listOf(AlarmOp.Insert(0, 7, 30, "上课")), ops)
    }

    @Test
    fun `changed plan time updates alarm`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 6, 50, true)),
            types = listOf(classType),
            alarms = listOf(planAlarm(10, 0, 7, 30))
        )
        assertEquals(1, ops.size)
        val update = ops.first() as AlarmOp.Update
        assertEquals(10, update.alarmId)
        assertEquals(6, update.hour)
        assertEquals(50, update.minute)
    }

    @Test
    fun `rest type deletes plan alarm`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(5, 3, 0, 0, true)),
            types = listOf(restType),
            alarms = listOf(planAlarm(11, 5))
        )
        assertEquals(listOf(AlarmOp.Delete(11)), ops)
    }

    @Test
    fun `disabled plan deletes alarm`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(2, 1, 7, 30, false)),
            types = listOf(classType),
            alarms = listOf(planAlarm(12, 2))
        )
        assertEquals(listOf(AlarmOp.Delete(12)), ops)
    }

    @Test
    fun `renamed type updates label`() {
        val renamed = classType.copy(name = "专业核心课")
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 7, 30, true)),
            types = listOf(renamed),
            alarms = listOf(planAlarm(13, 0, 7, 30, "上课"))
        )
        val update = ops.first() as AlarmOp.Update
        assertEquals("专业核心课", update.label)
    }

    @Test
    fun `missing type deletes alarm`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 99, 7, 30, true)),
            types = listOf(classType),
            alarms = listOf(planAlarm(14, 0))
        )
        assertEquals(listOf(AlarmOp.Delete(14)), ops)
    }

    @Test
    fun `manual alarm on same minute skips insert`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 7, 30, true)),
            types = listOf(classType),
            alarms = listOf(manualAlarm(20, 0, 7, 30))
        )
        assertTrue(ops.isEmpty())
    }

    @Test
    fun `existing plan alarm conflicting with manual alarm is deleted`() {
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 7, 30, true)),
            types = listOf(classType),
            alarms = listOf(
                planAlarm(21, 0, 7, 30),
                manualAlarm(22, 0, 7, 30)
            )
        )
        assertEquals(listOf(AlarmOp.Delete(21)), ops)
    }

    @Test
    fun `mixed plans only touch plan generated alarms`() {
        val plans = listOf(
            PlanRow(0, 1, 7, 30, true),
            PlanRow(1, 2, 8, 30, true),
            PlanRow(5, 3, 0, 0, true)
        )
        val alarms = listOf(
            planAlarm(30, 0, 7, 30),
            manualAlarm(31, 1, 9, 0),
            planAlarm(32, 5)
        )
        val ops = DayPlanSync.diff(plans, listOf(classType, selfType, restType), alarms)
        assertEquals(2, ops.size)
        assertTrue(ops.contains(AlarmOp.Insert(1, 8, 30, "自习")))
        assertTrue(ops.contains(AlarmOp.Delete(32)))
    }

    @Test
    fun `diff is stable across identical runs`() {
        val plans = listOf(
            PlanRow(3, 1, 7, 30, true),
            PlanRow(1, 2, 8, 30, true)
        )
        val alarms = listOf(planAlarm(40, 3, 7, 0))
        val first = DayPlanSync.diff(plans, listOf(classType, selfType), alarms)
        val second = DayPlanSync.diff(plans, listOf(classType, selfType), alarms)
        assertEquals(first, second)
    }

    @Test
    fun `duplicate plan alarms for one day collapse into one`() {
        val alarms = listOf(planAlarm(50, 0, 7, 0), planAlarm(51, 0, 7, 0))
        val ops = DayPlanSync.diff(
            plans = listOf(PlanRow(0, 1, 7, 30, true)),
            types = listOf(classType),
            alarms = alarms
        )
        assertTrue(ops.contains(AlarmOp.Delete(51)))
        assertTrue(ops.any { it is AlarmOp.Update && it.alarmId == 50 })
    }
}
