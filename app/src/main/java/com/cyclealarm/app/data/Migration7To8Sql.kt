package com.cyclealarm.app.data

/**
 * v7 → v8：**纯数据回填**，不改任何表结构。
 *
 * 1) 历史上「关闭假期模式」会把每条闹钟的 mutedDuringHoliday 抹成 0（0.1.0 已修机制但不回填），
 *    这里把「由安排生成」的闹钟恢复为静音（这类闹钟本就恒为 true）；
 * 2) plansSeeded 置 1：老库升级后绝不能再走「空库重建」分支，避免误注入默认计划。
 */
object Migration7To8Sql {

    const val BACKFILL_HOLIDAY_MUTE =
        "UPDATE `alarms` SET `mutedDuringHoliday` = 1 WHERE `planGenerated` = 1"

    const val MARK_PLANS_SEEDED =
        "UPDATE `settings` SET `plansSeeded` = 1"

    val statements: List<String> = listOf(BACKFILL_HOLIDAY_MUTE, MARK_PLANS_SEEDED)
}
