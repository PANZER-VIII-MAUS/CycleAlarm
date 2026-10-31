package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity

/** 模板里的一个班次：周期第 dayIndex 天、hour:minute 提醒、标签。 */
data class ShiftSlot(
    val dayIndex: Int,
    val hour: Int,
    val minute: Int,
    val label: String
)

data class ShiftTemplate(
    val key: String,
    val name: String,
    val description: String,
    val cycleLength: Int,
    val slots: List<ShiftSlot>
)

/**
 * 常见轮班制的模板（纯数据）。
 *
 * 应用模板会：把循环方式设为自定义、周期天数设为模板长度、并为每个班次建一条闹钟。
 * 模板只提供「一个周期」的排班，之后由循环自动重复。
 */
object ShiftTemplates {

    private fun slot(day: Int, hour: Int, minute: Int, label: String) =
        ShiftSlot(dayIndex = day, hour = hour, minute = minute, label = label)

    val ALL: List<ShiftTemplate> = listOf(
        ShiftTemplate(
            key = "white_night_rest_rest",
            name = "白夜休休",
            description = "4 天一轮：第 1 天白班 08:00，第 2 天夜班 20:00，第 3–4 天休息",
            cycleLength = 4,
            slots = listOf(
                slot(0, 8, 0, "白班"),
                slot(1, 20, 0, "夜班")
            )
        ),
        ShiftTemplate(
            key = "work_two_rest_two",
            name = "做二休二",
            description = "4 天一轮：第 1–2 天白班 08:00，第 3–4 天休息",
            cycleLength = 4,
            slots = listOf(
                slot(0, 8, 0, "白班"),
                slot(1, 8, 0, "白班")
            )
        ),
        ShiftTemplate(
            key = "four_crew_three_shift",
            name = "四班三运转",
            description = "8 天一轮：白班 ×2、中班 ×2、夜班 ×2、休息 ×2",
            cycleLength = 8,
            slots = listOf(
                slot(0, 8, 0, "白班"),
                slot(1, 8, 0, "白班"),
                slot(2, 16, 0, "中班"),
                slot(3, 16, 0, "中班"),
                slot(4, 0, 0, "夜班"),
                slot(5, 0, 0, "夜班")
            )
        )
    )

    fun byKey(key: String): ShiftTemplate? = ALL.firstOrNull { it.key == key }

    /** 把模板转成待插入的周期闹钟（id=0 表示新建）。 */
    fun toAlarms(template: ShiftTemplate): List<AlarmEntity> = template.slots.map { slot ->
        AlarmEntity(
            dayIndex = slot.dayIndex,
            hour = slot.hour,
            minute = slot.minute,
            enabled = true,
            mutedDuringHoliday = true,
            label = slot.label
        )
    }
}
