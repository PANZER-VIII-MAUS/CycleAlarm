package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 快照 V2：新增 day_plans / activity_types / batchId，并保持对 V1 的兼容。 */
class ImportSnapshotCodecV2Test {

    private val alarm = AlarmEntity(id = 7, dayIndex = 1, hour = 7, minute = 30, label = "上课")
    private val dateAlarm = DateAlarmEntity(
        id = 3,
        epochDay = 20_000L,
        hour = 9,
        minute = 15,
        label = "考试"
    )
    private val settings = AppSettingsEntity(
        id = 1,
        cycleLength = 7,
        cycleStartEpochDay = 19_900L,
        cycleType = CycleTypes.WEEKLY
    )
    private val type = ActivityTypeEntity(
        id = 2,
        name = "自习",
        hour = 8,
        minute = 30,
        rings = true,
        sortOrder = 1
    )
    private val plan = DayPlanEntity(dayIndex = 2, typeId = 2, hour = 8, minute = 30, enabled = true)

    private fun snapshot() = AlarmImportWriter.Snapshot(
        alarms = listOf(alarm),
        dateAlarms = listOf(dateAlarm),
        settings = settings,
        dayPlans = listOf(plan),
        activityTypes = listOf(type),
        batchId = 1_700_000_000_000L
    )

    @Test
    fun `v2 round trip keeps plans, types and batch id`() {
        val decoded = ImportSnapshotCodec.decode(ImportSnapshotCodec.encode(snapshot()))

        assertEquals(1_700_000_000_000L, decoded.batchId)
        assertEquals(listOf(plan), decoded.dayPlans)
        assertEquals(listOf(type), decoded.activityTypes)
        assertEquals(listOf(alarm), decoded.alarms)
        assertEquals(listOf(dateAlarm), decoded.dateAlarms)
        assertEquals(settings, decoded.settings)
    }

    @Test
    fun `v1 payload still decodes with empty plans and types`() {
        val v1 = buildString {
            appendLine("CYCLE_ALARM_SNAPSHOT_V1")
            append("A")
            append('\t').append(alarm.id)
            append('\t').append(alarm.dayIndex)
            append('\t').append(alarm.hour)
            append('\t').append(alarm.minute)
            append('\t').append("1")
            append('\t').append("1")
            append('\t').append("0")
            append('	').append(
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    alarm.label.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                )
            )
            appendLine()
        }

        val decoded = ImportSnapshotCodec.decode(v1)

        assertEquals(1, decoded.alarms.size)
        assertTrue(decoded.dayPlans.isEmpty())
        assertTrue(decoded.activityTypes.isEmpty())
        assertEquals(0L, decoded.batchId)
        assertNull(decoded.settings)
    }

    @Test
    fun `unknown header is rejected`() {
        val failed = runCatching { ImportSnapshotCodec.decode("CYCLE_ALARM_SNAPSHOT_V0\nA\t1") }
        assertTrue(failed.isFailure)
    }
}
