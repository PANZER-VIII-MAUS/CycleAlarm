package com.cyclealarm.app.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 迁移链的「生产配置」守卫。
 *
 * 背景：0.6.3 曾把 10→11 加进 [AppDatabase.ALL_MIGRATIONS]（测试用的就是它），
 * 但真实 `Room.databaseBuilder` 里是手写列表，漏掉了这一条 → 升级后打不开数据库。
 * 这里直接用**生产构建路径**（`AppDatabase.get`）打开一个 v10 旧库，保证以后不会再犯。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationChainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "cycle_alarm.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTest()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `all migrations from v4 to current version are registered`() {
        val versions = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val current = 11
        (4 until current).forEach { from ->
            assertTrue(
                "缺少 $from → ${from + 1} 的迁移",
                (from to from + 1) in versions
            )
        }
    }

    @Test
    fun `production builder upgrades a v10 database without losing data`() {
        helper.createDatabase(dbName, 10).use { db ->
            db.execSQL(
                "INSERT INTO alarms (id, dayIndex, hour, minute, enabled, mutedDuringHoliday, label, " +
                    "planGenerated, ringtoneUri, vibrate, rampVolume, preAlertMinutes) " +
                    "VALUES (1, 2, 7, 30, 1, 1, 'legacy', 0, NULL, 1, 0, 0)"
            )
        }

        // 走生产路径：Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)
        val db = AppDatabase.get(context)
        db.query("SELECT label, hour FROM alarms WHERE id = 1", null).use { cursor ->
            assertTrue("迁移后旧数据必须保留", cursor.moveToFirst())
            assertEquals("legacy", cursor.getString(0))
            assertEquals(7, cursor.getInt(1))
        }
        db.query("PRAGMA index_list(ring_events)", null).use { cursor ->
            val names = buildList {
                val idx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(idx))
            }
            assertTrue(
                "应存在唯一索引，实际: $names",
                "index_ring_events_alarmKind_alarmId_expectedAt" in names
            )
        }
    }
}
