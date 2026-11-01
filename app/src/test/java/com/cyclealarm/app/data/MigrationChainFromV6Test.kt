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
 * 迁移链覆盖更多起点 + v10/v11 新列默认值断言（四轮复审 §4.2 盲区）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationChainFromV6Test {

    private val dbName = "migration-chain.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `migration chain works from v6`() {
        // 说明：v4/v5 的 schema JSON 从未导出（仓库最早只有 6.json），
        // MigrationTestHelper 无法从 v4/v5 建库；v4→5→6 这两步由
        // AppDatabaseMigrationChainTest 的「迁移已注册」断言间接覆盖。
        helper.createDatabase(dbName, 6).use { }
        helper.runMigrationsAndValidate(dbName, 11, true, *AppDatabase.ALL_MIGRATIONS).use { }
    }

    @Test
    fun `v10 ring options default to the old behaviour after migration`() {
        helper.createDatabase(dbName, 10).use { db ->
            db.execSQL(
                "INSERT INTO alarms (id, dayIndex, hour, minute, enabled, mutedDuringHoliday, label, " +
                    "planGenerated, ringtoneUri, vibrate, rampVolume, preAlertMinutes) " +
                    "VALUES (1, 2, 7, 30, 1, 1, 'legacy', 0, NULL, 1, 0, 0)"
            )
        }
        helper.runMigrationsAndValidate(dbName, 11, true, *AppDatabase.ALL_MIGRATIONS).use { db ->
            db.query(
                "SELECT ringtoneUri, vibrate, rampVolume, preAlertMinutes FROM alarms WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("旧闹钟铃声应为 null（跟随全局）", cursor.isNull(0))
                assertEquals("振动默认开", 1, cursor.getInt(1))
                assertEquals("渐强默认关", 0, cursor.getInt(2))
                assertEquals("提前提醒默认关", 0, cursor.getInt(3))
            }
        }
    }
}
