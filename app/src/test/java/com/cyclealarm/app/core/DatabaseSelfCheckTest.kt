package com.cyclealarm.app.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSelfCheckTest {

    private fun fullSchema(): Map<String, Set<String>> =
        DatabaseSelfCheck.EXPECTED.associate { it.name to it.columns }

    @Test
    fun `complete schema passes`() {
        assertTrue(DatabaseSelfCheck.diff(fullSchema()).isEmpty())
    }

    @Test
    fun `missing table is reported`() {
        val actual = fullSchema().toMutableMap().apply { remove("ring_events") }
        val issues = DatabaseSelfCheck.diff(actual)
        assertTrue(issues.any { it.contains("ring_events") })
    }

    @Test
    fun `missing column is reported`() {
        val actual = fullSchema().toMutableMap()
        actual["alarms"] = actual.getValue("alarms") - "preAlertMinutes"
        val issues = DatabaseSelfCheck.diff(actual)
        assertTrue(issues.any { it.contains("preAlertMinutes") })
    }

    @Test
    fun `extra columns are tolerated`() {
        val actual = fullSchema().toMutableMap()
        actual["alarms"] = actual.getValue("alarms") + "futureColumn"
        assertTrue(DatabaseSelfCheck.diff(actual).isEmpty())
    }
}
