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
}
