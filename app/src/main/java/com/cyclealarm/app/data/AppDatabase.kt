package com.cyclealarm.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlarmEntity::class,
        ActivityTypeEntity::class,
        DayPlanEntity::class,
        DateAlarmEntity::class,
        HolidayEntity::class,
        AppSettingsEntity::class,
        SnoozeEntity::class,
        ImportSnapshotEntity::class
    ],
    // v2：同一日期支持多条闹钟；v3：假期改为多段（法定节假日可一键导入）；
    // v4：循环方式选择（每天 / 每周 / 自定义 N 天）；
    // v5：按日期的一次性闹钟（课表/排班表按日期导入）；
    // v6：循环安排（安排类型 + 每天一个安排，自动生成闹钟）。
    // v7：持久化 snooze 与导入快照。必须走 Migration，禁止清空用户数据。
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun activityTypeDao(): ActivityTypeDao
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun dateAlarmDao(): DateAlarmDao
    abstract fun holidayDao(): HolidayDao
    abstract fun settingsDao(): SettingsDao
    abstract fun snoozeDao(): SnoozeDao
    abstract fun importSnapshotDao(): ImportSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v4 → v5：只新增 date_alarms 表，不动任何已有表与数据。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `date_alarms` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`mutedDuringHoliday` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL)"
                )
            }
        }

        /**
         * v5 → v6：新增安排相关两张表 + alarms 的 planGenerated 列 + settings 的 plansSeeded 列，
         * 并插入三个默认安排类型。只做 DDL 与默认类型，不搬动既有数据。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_types` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`rings` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_plans` (" +
                        "`dayIndex` INTEGER NOT NULL, " +
                        "`typeId` INTEGER NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`dayIndex`))"
                )
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `planGenerated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `plansSeeded` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT INTO `activity_types`(`name`,`hour`,`minute`,`rings`,`sortOrder`) VALUES " +
                        "('上课',7,30,1,0), ('自习',8,30,1,1), ('休息',0,0,0,2)"
                )
            }
        }

        /** v6 → v7：只新增 snoozes、import_snapshots 与必要索引。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migration6To7Sql.statements.forEach(db::execSQL)
            }
        }

        /** v7 → v8：纯数据回填（假期静音 + plansSeeded），不改表结构。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migration7To8Sql.statements.forEach(db::execSQL)
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cycle_alarm.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
