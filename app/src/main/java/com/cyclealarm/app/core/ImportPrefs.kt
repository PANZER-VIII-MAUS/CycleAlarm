package com.cyclealarm.app.core

import android.content.Context
import com.cyclealarm.app.importer.SectionTimeTable
import java.time.LocalTime

/**
 * 导入相关偏好设置（节次时间表、提前量）。
 * 存 SharedPreferences，不动 Room schema。
 */
object ImportPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_SECTION_TIMES = "import_section_times"
    private const val KEY_LEAD_MINUTES = "import_lead_minutes"

    fun sectionTimes(context: Context): Map<Int, LocalTime> =
        SectionTimeTable.decode(prefs(context).getString(KEY_SECTION_TIMES, null))

    fun saveSectionTimes(context: Context, map: Map<Int, LocalTime>) {
        prefs(context).edit()
            .putString(KEY_SECTION_TIMES, SectionTimeTable.encode(map))
            .apply()
    }

    fun leadMinutes(context: Context): Int =
        prefs(context).getInt(KEY_LEAD_MINUTES, 20).coerceIn(0, 60)

    fun saveLeadMinutes(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_LEAD_MINUTES, value.coerceIn(0, 60)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
