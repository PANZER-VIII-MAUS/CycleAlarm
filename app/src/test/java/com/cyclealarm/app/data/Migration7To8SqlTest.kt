package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration7To8SqlTest {

    @Test
    fun `v8 migration is data-only backfill`() {
        val sql = Migration7To8Sql.statements
        assertEquals(2, sql.size)
        sql.forEach { statement ->
            assertFalse("v8 不允许改表结构: $statement", statement.contains("CREATE TABLE", true))
            assertFalse("v8 不允许删表: $statement", statement.contains("DROP", true))
            assertFalse("v8 不允许删列: $statement", statement.contains("ALTER", true))
        }
    }

    @Test
    fun `v8 backfills plan alarm holiday mute`() {
        assertTrue(Migration7To8Sql.BACKFILL_HOLIDAY_MUTE.contains("mutedDuringHoliday"))
        assertTrue(Migration7To8Sql.BACKFILL_HOLIDAY_MUTE.contains("planGenerated"))
    }

    @Test
    fun `v8 marks plans as seeded for upgraded databases`() {
        assertTrue(Migration7To8Sql.MARK_PLANS_SEEDED.contains("plansSeeded"))
        assertTrue(Migration7To8Sql.MARK_PLANS_SEEDED.contains("1"))
    }
}
