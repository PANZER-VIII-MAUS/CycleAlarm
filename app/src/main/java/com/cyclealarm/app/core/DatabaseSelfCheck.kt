package com.cyclealarm.app.core

import android.content.Context
import android.util.Log
import com.cyclealarm.app.data.AppDatabase

/**
 * 数据库结构自检。
 *
 * 迁移测试需要在设备上跑 Room 的 MigrationTestHelper（见立项 backlog），
 * 在那之前，这里用「每次升级后跑一次」的方式兜底：
 * - PRAGMA quick_check 检查库完整性；
 * - 逐表比对列集合与当前版本期望（与导出的 schema JSON 对齐）；
 * - 发现异常写入排期诊断，用户能在设置页看到。
 */
object DatabaseSelfCheck {
    private const val TAG = "DatabaseSelfCheck"
    private const val PREFS = "app_prefs"
    private const val KEY_CHECKED_VERSION = "db_checked_version"

    data class TableSpec(val name: String, val columns: Set<String>)

    /** 与 app/schemas/.../10.json 对齐的期望结构。改表必须同步改这里。 */
    val EXPECTED: List<TableSpec> = listOf(
        TableSpec(
            "alarms",
            setOf(
                "id", "dayIndex", "hour", "minute", "enabled", "mutedDuringHoliday",
                "label", "planGenerated", "ringtoneUri", "vibrate", "rampVolume",
                "preAlertMinutes"
            )
        ),
        TableSpec("activity_types", setOf("id", "name", "hour", "minute", "rings", "sortOrder")),
        TableSpec("day_plans", setOf("dayIndex", "typeId", "hour", "minute", "enabled")),
        TableSpec(
            "date_alarms",
            setOf("id", "epochDay", "hour", "minute", "enabled", "mutedDuringHoliday", "label")
        ),
        TableSpec("holidays", setOf("id", "startEpochDay", "endEpochDay", "enabled", "label")),
        TableSpec(
            "settings",
            setOf(
                "id", "cycleLength", "cycleStartEpochDay", "cycleType",
                "holidayModeEnabled", "plansSeeded"
            )
        ),
        TableSpec(
            "snoozes",
            setOf(
                "id", "kind", "alarmId", "dayIndex", "hour", "minute", "label",
                "triggerAt", "requestCode"
            )
        ),
        TableSpec("import_snapshots", setOf("id", "payload", "createdAt", "expiresAt")),
        TableSpec(
            "ring_events",
            setOf("id", "alarmKind", "alarmId", "label", "expectedAt", "rungAt", "ackedAt")
        )
    )

    /** 纯逻辑：实际结构 vs 期望结构，返回人类可读的问题列表（空=通过）。 */
    fun diff(actual: Map<String, Set<String>>): List<String> {
        val issues = mutableListOf<String>()
        EXPECTED.forEach { spec ->
            val columns = actual[spec.name]
            if (columns == null) {
                issues += "缺少表 ${spec.name}"
                return@forEach
            }
            val missing = spec.columns - columns
            if (missing.isNotEmpty()) {
                issues += "表 ${spec.name} 缺少列 ${missing.sorted().joinToString(",")}"
            }
        }
        return issues
    }

    /** 每个应用版本只自检一次；返回本次发现的问题（已升级过则为空）。 */
    suspend fun verifyOnce(context: Context, versionCode: Int): List<String> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_CHECKED_VERSION, -1) == versionCode) return emptyList()

        val issues = runCatching { inspect(context) }
            .onFailure { Log.e(TAG, "self check failed", it) }
            .getOrElse { listOf("自检执行失败：${it.message}") }

        if (issues.isEmpty()) {
            ResyncCheckpoint.recordDiagnostic(context, System.currentTimeMillis(), "库结构自检通过")
        } else {
            Log.w(TAG, "database structure issues: $issues")
            ResyncCheckpoint.recordDiagnostic(
                context,
                System.currentTimeMillis(),
                "库结构异常：${issues.first()}"
            )
        }
        prefs.edit().putInt(KEY_CHECKED_VERSION, versionCode).apply()
        return issues
    }

    private fun inspect(context: Context): List<String> {
        val db = AppDatabase.get(context)
        val raw = db.openHelper.writableDatabase
        val actual = mutableMapOf<String, Set<String>>()
        EXPECTED.forEach { spec ->
            val columns = mutableSetOf<String>()
            raw.query("PRAGMA table_info(`${spec.name}`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) columns += cursor.getString(nameIndex)
                }
            }
            if (columns.isNotEmpty()) actual[spec.name] = columns
        }
        val issues = diff(actual).toMutableList()
        runCatching {
            raw.query("PRAGMA quick_check").use { cursor ->
                if (cursor.moveToFirst() && cursor.getString(0) != "ok") {
                    issues += "完整性检查失败：${cursor.getString(0)}"
                }
            }
        }
        return issues
    }
}
