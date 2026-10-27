package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWritePolicyTest {

    @Test
    fun `non overwrite import keeps existing keys for deduplication`() {
        val keys = ImportWritePolicy.existingCycleKeys(
            alarms = listOf(AlarmEntity(id = 1, dayIndex = 2, hour = 7, minute = 30)),
            overwrite = false
        )

        assertEquals(setOf(Triple(2, 7, 30)), keys)
    }

    @Test
    fun `overwrite import starts from an empty cycle key set`() {
        val keys = ImportWritePolicy.existingCycleKeys(
            alarms = listOf(AlarmEntity(id = 1, dayIndex = 2, hour = 7, minute = 30)),
            overwrite = true
        )

        assertTrue(keys.isEmpty())
    }

    @Test
    fun `overwrite import starts from an empty date key set`() {
        val keys = ImportWritePolicy.existingDateKeys(
            alarms = listOf(
                DateAlarmEntity(id = 1, epochDay = 20_000, hour = 8, minute = 20)
            ),
            overwrite = true
        )

        assertTrue(keys.isEmpty())
    }
}
