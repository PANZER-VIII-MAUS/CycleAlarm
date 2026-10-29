package com.cyclealarm.app.data

import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWeeklyPolicyTest {

    @Test
    fun `weekly entries require explicit switch confirmation`() {
        assertTrue(
            ImportWeeklyPolicy.requiresWeeklySwitch(
                listOf(
                    ImportedEntry(ScheduleKind.WEEKLY, 1, null, 8, 0),
                    ImportedEntry(ScheduleKind.DATED, null, 20_000, 8, 0)
                )
            )
        )
    }

    @Test
    fun `dated only entries do not switch cycle configuration`() {
        assertFalse(
            ImportWeeklyPolicy.requiresWeeklySwitch(
                listOf(ImportedEntry(ScheduleKind.DATED, null, 20_000, 8, 0))
            )
        )
    }

    @Test
    fun `cycle switch requires both weekly entries and confirmation`() {
        val weekly = ImportedEntry(ScheduleKind.WEEKLY, 1, null, 8, 0)
        val dated = ImportedEntry(ScheduleKind.DATED, null, 20_000, 8, 0)
        assertTrue(ImportWeeklyPolicy.allowCycleSwitch(listOf(weekly), confirmed = true))
        assertFalse(ImportWeeklyPolicy.allowCycleSwitch(listOf(weekly), confirmed = false))
        assertFalse(ImportWeeklyPolicy.allowCycleSwitch(listOf(dated), confirmed = true))
        assertFalse(ImportWeeklyPolicy.allowCycleSwitch(emptyList(), confirmed = true))
    }
}
