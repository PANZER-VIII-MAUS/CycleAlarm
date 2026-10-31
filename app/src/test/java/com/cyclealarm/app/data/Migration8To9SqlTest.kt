package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration8To9SqlTest {

    @Test
    fun `v9 only adds the ring events table`() {
        val sql = Migration8To9Sql.statements
        assertEquals(3, sql.size)
        sql.forEach { statement ->
            assertFalse("v9 不允许删表: $statement", statement.contains("DROP", true))
            assertFalse("v9 不允许改表: $statement", statement.contains("ALTER", true))
        }
    }

    @Test
    fun `ring events table has expected columns`() {
        val create = Migration8To9Sql.CREATE_TABLE
        listOf("id", "alarmKind", "alarmId", "label", "expectedAt", "rungAt", "ackedAt")
            .forEach { column -> assertTrue("缺少列 $column", create.contains("`$column`")) }
    }

    @Test
    fun `indexes cover expectedAt and alarm identity`() {
        assertTrue(Migration8To9Sql.CREATE_INDEX_EXPECTED.contains("`expectedAt`"))
        assertTrue(Migration8To9Sql.CREATE_INDEX_ALARM.contains("`alarmKind`"))
    }
}
