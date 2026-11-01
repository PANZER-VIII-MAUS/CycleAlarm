package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity

/** 模板里的一个班次：周期第 dayIndex 天、hour:minute 提醒、标签。 */
data class ShiftSlot(
    val dayIndex: Int,
    val hour: Int,
    val minute: Int,
    val labelRes: Int
)

data class ShiftTemplate(
    val key: String,
    val nameRes: Int,
    val descRes: Int,
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

    private fun slot(day: Int, hour: Int, minute: Int, labelRes: Int) =
        ShiftSlot(dayIndex = day, hour = hour, minute = minute, labelRes = labelRes)

    val ALL: List<ShiftTemplate> = listOf(
        ShiftTemplate(
            key = "white_night_rest_rest",
            nameRes = com.cyclealarm.app.R.string.shift_white_night_name,
            descRes = com.cyclealarm.app.R.string.shift_white_night_desc,
            cycleLength = 4,
            slots = listOf(
                slot(0, 8, 0, com.cyclealarm.app.R.string.shift_label_day),
                slot(1, 20, 0, com.cyclealarm.app.R.string.shift_label_night)
            )
        ),
        ShiftTemplate(
            key = "work_two_rest_two",
            nameRes = com.cyclealarm.app.R.string.shift_two_two_name,
            descRes = com.cyclealarm.app.R.string.shift_two_two_desc,
            cycleLength = 4,
            slots = listOf(
                slot(0, 8, 0, com.cyclealarm.app.R.string.shift_label_day),
                slot(1, 8, 0, com.cyclealarm.app.R.string.shift_label_day)
            )
        ),
        ShiftTemplate(
            key = "four_crew_three_shift",
            nameRes = com.cyclealarm.app.R.string.shift_four_crew_name,
            descRes = com.cyclealarm.app.R.string.shift_four_crew_desc,
            cycleLength = 8,
            slots = listOf(
                slot(0, 8, 0, com.cyclealarm.app.R.string.shift_label_day),
                slot(1, 8, 0, com.cyclealarm.app.R.string.shift_label_day),
                slot(2, 16, 0, com.cyclealarm.app.R.string.shift_label_middle),
                slot(3, 16, 0, com.cyclealarm.app.R.string.shift_label_middle),
                slot(4, 0, 0, com.cyclealarm.app.R.string.shift_label_night),
                slot(5, 0, 0, com.cyclealarm.app.R.string.shift_label_night)
            )
        )
    )

    fun byKey(key: String): ShiftTemplate? = ALL.firstOrNull { it.key == key }

    /** 把模板转成待插入的周期闹钟（id=0 表示新建）；标签按当前语言解析。 */
    fun toAlarms(context: android.content.Context, template: ShiftTemplate): List<AlarmEntity> =
        template.slots.map { slot ->
            AlarmEntity(
                dayIndex = slot.dayIndex,
                hour = slot.hour,
                minute = slot.minute,
                enabled = true,
                mutedDuringHoliday = true,
                label = context.getString(slot.labelRes)
            )
        }
}
