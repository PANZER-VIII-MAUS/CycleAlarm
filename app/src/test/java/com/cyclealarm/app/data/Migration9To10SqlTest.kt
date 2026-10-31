package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration9To10SqlTest {

    @Test
    fun `v10 only adds columns to alarms`() {
        val sql = Migration9To10Sql.statements
        assertEquals(4, sql.size)
        sql.forEach { statement ->
            assertTrue("必须是对 alarms 的 ALTER", statement.contains("`alarms`"))
            assertFalse("不允许删表", statement.contains("DROP", true))
            assertFalse("不允许删列", statement.contains("DROP COLUMN", true))
        }
    }

    @Test
    fun `new columns have behavior preserving defaults`() {
        assertTrue(Migration9To10Sql.ADD_VIBRATE.contains("DEFAULT 1"))
        assertTrue(Migration9To10Sql.ADD_RAMP_VOLUME.contains("DEFAULT 0"))
        assertTrue(Migration9To10Sql.ADD_PRE_ALERT.contains("DEFAULT 0"))
        assertTrue(Migration9To10Sql.ADD_RINGTONE.contains("TEXT"))
    }
}
