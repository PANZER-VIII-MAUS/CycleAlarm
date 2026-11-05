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
            // 第一行没响（旧实现只留它 → 响过的事实丢失）；第二/三行分别带 rungAt/ackedAt
            db.execSQL(
                "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                    "VALUES ('CYCLE', 5, 'dup-0', 100, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                    "VALUES ('CYCLE', 5, 'dup-1', 100, 111, NULL)"
            )
            db.execSQL(
                "INSERT INTO ring_events (alarmKind, alarmId, label, expectedAt, rungAt, ackedAt) " +
                    "VALUES ('CYCLE', 5, 'dup-2', 100, 111, 222)"
            )
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
            // 五轮复审 B#1：去重时必须合并 rungAt/ackedAt，
            // 否则「另一行响过」的事实被丢掉，历史页会把响过的显示成漏响
            db.query(
                "SELECT rungAt, ackedAt FROM ring_events WHERE alarmKind = 'CYCLE' AND alarmId = 5 " +
                    "AND expectedAt = 100"
            ).use { cursor ->
                assertTrue("保留行应存在", cursor.moveToFirst())
                assertEquals("合并后应保留「响过」的时间", 111L, cursor.getLong(0))
                assertEquals("合并后应保留「已处理」的时间", 222L, cursor.getLong(1))
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
