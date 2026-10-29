package com.cyclealarm.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSchemaTest {

    @Test
    fun `v6 schema is exported before v7 upgrade`() {
        val schema = schemaJson(version = 6)

        assertTrue(schema.contains("\"version\": 6"))
        assertTrue(schema.contains("\"tableName\": \"alarms\""))
    }

    @Test
    fun `v7 schema contains both new tables and required indices`() {
        val schema = schemaJson(version = 7)

        assertTrue(schema.contains("\"version\": 7"))
        assertTrue(schema.contains("\"tableName\": \"snoozes\""))
        assertTrue(schema.contains("\"tableName\": \"import_snapshots\""))
        assertTrue(schema.contains("\"name\": \"index_snoozes_triggerAt\""))
        assertTrue(schema.contains("\"name\": \"index_snoozes_alarmId\""))
        assertTrue(schema.contains("\"name\": \"index_alarms_dayIndex_hour_minute\""))
    }


    @Test
    fun `v8 schema keeps all v7 tables`() {
        val schema = schemaJson(version = 8)

        assertTrue(schema.contains("\"version\": 8"))
        assertTrue(schema.contains("\"tableName\": \"alarms\""))
        assertTrue(schema.contains("\"tableName\": \"snoozes\""))
        assertTrue(schema.contains("\"tableName\": \"day_plans\""))
    }

    private fun schemaJson(version: Int): String {
        val relative = "schemas/com.cyclealarm.app.data.AppDatabase/$version.json"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Schema JSON not found: ${candidates.joinToString()}")
        return file.readText()
    }
}
