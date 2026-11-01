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
 * v10 → v11：`ring_events` 去重 + (alarmKind, alarmId, expectedAt) 唯一索引。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration10To11RobolectricTest {

    private val dbName = "migration-10-11.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `duplicate ring events are removed and the unique index is enforced`() {
        helper.createDatabase(dbName, 10).use { db ->
            // 模拟旧版 snooze 重复触发：同一 (kind, alarmId, expectedAt) 插了三条
            repeat(3) { index ->
                db.execSQL(
                    "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                        "VALUES ('CYCLE', 5, 'dup-$index', 100, NULL, NULL)"
                )
            }
            // 另一条时间不同的正常事件
            db.execSQL(
                "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                    "VALUES ('CYCLE', 5, 'other', 400, NULL, NULL)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 11, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM ring_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("重复的同一次事件只保留一行，加上另一条时间不同的事件", 2, cursor.getInt(0))
            }
            // 唯一索引真的存在且生效：再插同一事件应失败
            var failed = false
            try {
                db.execSQL(
                    "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                        "VALUES ('CYCLE', 5, 'dup-again', 100, NULL, NULL)"
                )
            } catch (expected: Exception) {
                failed = true
            }
            assertTrue("唯一索引应阻止重复插入", failed)
        }
    }
}
