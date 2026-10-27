package com.cyclealarm.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration6To7SqlTest {

    @Test
    fun `migration SQL matches exported v7 schema`() {
        val schema = schemaJson()
        val expected = listOf(
            "CREATE TABLE IF NOT EXISTS `snoozes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `alarmId` INTEGER NOT NULL, `dayIndex` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `triggerAt` INTEGER NOT NULL, `requestCode` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `import_snapshots` (`id` INTEGER NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_snoozes_triggerAt` ON `snoozes` (`triggerAt`)",
            "CREATE INDEX IF NOT EXISTS `index_snoozes_alarmId` ON `snoozes` (`alarmId`)",
            "CREATE INDEX IF NOT EXISTS `index_alarms_dayIndex_hour_minute` ON `alarms` (`dayIndex`, `hour`, `minute`)"
        )

        expected.forEach { sql ->
            assertTrue("Missing migration SQL: $sql", Migration6To7Sql.statements.contains(sql))
            val schemaForm = sql
                .replace("`snoozes` (`", "`${'$'}{TABLE_NAME}` (`")
                .replace("`import_snapshots` (`", "`${'$'}{TABLE_NAME}` (`")
                .replace("ON `snoozes`", "ON `${'$'}{TABLE_NAME}`")
                .replace("ON `alarms`", "ON `${'$'}{TABLE_NAME}`")
            assertTrue("SQL is absent from schema JSON: $schemaForm", schema.contains(schemaForm))
        }
    }

    private fun schemaJson(): String {
        val relative = "schemas/com.cyclealarm.app.data.AppDatabase/7.json"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Schema JSON not found: ${candidates.joinToString()}")
    }
}
