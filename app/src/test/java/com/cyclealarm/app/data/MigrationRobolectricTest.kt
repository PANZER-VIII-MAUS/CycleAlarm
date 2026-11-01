package com.cyclealarm.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 真实迁移测试：用 Room 的 MigrationTestHelper 在 Robolectric 上跑迁移链，
 * 验证「旧库 + 迁移 = 新 schema」且数据保留。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationRobolectricTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `migrate 6 to 10 keeps alarms and adds ring events`() {
        // v6 建库并写入一条闹钟
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO alarms (id, dayIndex, hour, minute, enabled, mutedDuringHoliday, label, planGenerated) " +
                    "VALUES (1, 2, 7, 30, 1, 1, 'legacy', 0)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName, 10, true, *AppDatabase.ALL_MIGRATIONS
        )
        migrated.use { db ->
            db.query("SELECT dayIndex, hour, minute, label FROM alarms WHERE id = 1").use { cursor ->
                assertTrue("旧数据应保留", cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals(30, cursor.getInt(2))
                assertEquals("legacy", cursor.getString(3))
            }
            db.query("SELECT COUNT(*) FROM ring_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("PRAGMA table_info(alarms)").use { cursor ->
                val names = buildList {
                    val idx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) add(cursor.getString(idx))
                }
                assertTrue("应新增 preAlertMinutes", "preAlertMinutes" in names)
                assertTrue("应新增 ringtoneUri", "ringtoneUri" in names)
            }
        }
    }

    @Test
    fun `settings survive the migration`() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, cycleLength, cycleStartEpochDay, cycleType, holidayModeEnabled, plansSeeded) " +
                    "VALUES (1, 8, 20000, 'CUSTOM', 1, 0)"
            )
        }
        helper.runMigrationsAndValidate(dbName, 10, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT cycleLength, plansSeeded FROM settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(8, cursor.getInt(0))
            }
        }
    }
}
