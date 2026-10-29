package com.cyclealarm.app.core

import android.content.Context

/**
 * 新装标记（SharedPreferences）。
 *
 * 「是否全新安装」不能只看数据库里两张表是否为空：用户可能把闹钟和安排都删光了，
 * 那种情况下不应该再注入默认计划。只有从未写过标记的设备才算全新安装。
 */
object InstallMarker {
    private const val NAME = "install_prefs"
    private const val KEY_MARKER = "install_marker_at"

    fun hasMarker(context: Context): Boolean =
        runCatching { prefs(context).getLong(KEY_MARKER, 0L) > 0L }.getOrDefault(false)

    fun markInstalled(context: Context) {
        runCatching {
            val now = System.currentTimeMillis()
            val current = prefs(context).getLong(KEY_MARKER, 0L)
            if (current <= 0L) {
                prefs(context).edit().putLong(KEY_MARKER, now).apply()
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
