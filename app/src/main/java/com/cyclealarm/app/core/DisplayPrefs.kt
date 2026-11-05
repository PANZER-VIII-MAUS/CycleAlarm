package com.cyclealarm.app.core

import androidx.core.content.edit
import com.cyclealarm.app.R
import android.content.Context
import java.util.Locale

/**
 * 显示偏好：时间用 12 还是 24 小时制。
 * 存 SharedPreferences，不动 Room schema。
 */
object DisplayPrefs {
    private const val NAME = "display_prefs"
    private const val KEY_24H = "is_24h"

    private fun prefs(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 默认 24 小时制（保持现状）。 */
    fun is24Hour(c: Context): Boolean = prefs(c).getBoolean(KEY_24H, true)

    fun set24Hour(c: Context, value: Boolean) =
        prefs(c).edit { putBoolean(KEY_24H, value) }

    /** 统一的时间格式化：24 小时制 "07:30"；12 小时制 "上午 7:30" / "下午 12:30"。 */
    fun formatTime(c: Context, hour: Int, minute: Int): String {
        return formatTime(
            is24Hour(c), hour, minute,
            c.getString(R.string.time_am), c.getString(R.string.time_pm)
        )
    }

    fun formatTime(is24Hour: Boolean, hour: Int, minute: Int): String =
        formatTime(is24Hour, hour, minute, "AM", "PM")

    /** 带自定义上下午标签的格式化（界面用资源传入当前语言）。 */
    fun formatTime(is24Hour: Boolean, hour: Int, minute: Int, amLabel: String, pmLabel: String): String {
        return if (is24Hour) {
            String.format(Locale.CHINA, "%02d:%02d", hour, minute)
        } else {
            val am = hour < 12
            val h12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.CHINA, "%s %d:%02d", if (am) amLabel else pmLabel, h12, minute)
        }
    }

    /** 24 小时制 "07:30"；12 小时制 "7:30"（不带上午/下午）。 */
    fun formatClock(c: Context, hour: Int, minute: Int): String =
        formatClock(is24Hour(c), hour, minute)

    fun formatClock(is24Hour: Boolean, hour: Int, minute: Int): String =
        if (is24Hour) String.format(Locale.CHINA, "%02d:%02d", hour, minute)
        else String.format(Locale.CHINA, "%d:%02d", hour12(hour), minute)

    fun hour12(hour: Int): Int = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    /** 12 小时制返回「上午」/「下午」；24 小时制返回 null。 */
    fun amPmLabel(c: Context, hour: Int): String? = if (is24Hour(c)) {
        null
    } else {
        c.getString(if (hour < 12) R.string.time_am else R.string.time_pm)
    }
}
