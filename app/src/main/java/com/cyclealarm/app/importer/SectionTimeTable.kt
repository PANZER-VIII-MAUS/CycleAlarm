package com.cyclealarm.app.importer

import java.time.LocalTime

/**
 * 节次 → 上课时间。
 * 默认值针对常见上午 4 节 + 下午 4 节的作息，导入页里可以逐节修改。
 */
object SectionTimeTable {

    private val sectionPattern = Regex("""第?\s*(\d{1,2})\s*(?:[-–—~～至到]\s*\d{1,2})?\s*节""")

    fun default(): Map<Int, LocalTime> = linkedMapOf(
        1 to LocalTime.of(8, 20),
        2 to LocalTime.of(9, 15),
        3 to LocalTime.of(10, 10),
        4 to LocalTime.of(11, 5),
        5 to LocalTime.of(14, 0),
        6 to LocalTime.of(14, 55),
        7 to LocalTime.of(15, 50),
        8 to LocalTime.of(16, 45)
    )

    /** "第1-2节" → 1，"1-2节" → 1，"第3节" → 3；"3" 这种纯数字不算节次。 */
    fun parseSection(text: String): Int? {
        val m = sectionPattern.find(text.trim()) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in 1..99 }
    }

    /** "1=08:20;2=09:15" */
    fun encode(map: Map<Int, LocalTime>): String =
        map.toSortedMap().entries.joinToString(";") { (section, time) ->
            "$section=${time.withSecond(0).withNano(0)}"
        }

    fun decode(raw: String?): Map<Int, LocalTime> {
        if (raw.isNullOrBlank()) return default()
        val result = linkedMapOf<Int, LocalTime>()
        raw.split(';').forEach { part ->
            val key = part.substringBefore('=', "").trim().toIntOrNull()
            val value = part.substringAfter('=', "").trim()
            val time = runCatching { LocalTime.parse(value) }.getOrNull()
            if (key != null && time != null) result[key] = time
        }
        return if (result.isEmpty()) default() else result
    }
}
