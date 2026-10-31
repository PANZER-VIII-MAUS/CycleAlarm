package com.cyclealarm.app.data

/**
 * v9 → v10：给 `alarms` 增加「每闹钟独立响铃设置」四列（纯新增，带默认值，不动既有数据）。
 *
 * - ringtoneUri：铃声 URI；NULL = 使用全局铃声
 * - vibrate：是否振动（默认 1，与旧行为一致）
 * - rampVolume：是否渐强（默认 0，与旧行为一致）
 * - preAlertMinutes：提前提醒分钟数；0 = 关闭
 */
object Migration9To10Sql {

    const val ADD_RINGTONE = "ALTER TABLE `alarms` ADD COLUMN `ringtoneUri` TEXT"

    const val ADD_VIBRATE =
        "ALTER TABLE `alarms` ADD COLUMN `vibrate` INTEGER NOT NULL DEFAULT 1"

    const val ADD_RAMP_VOLUME =
        "ALTER TABLE `alarms` ADD COLUMN `rampVolume` INTEGER NOT NULL DEFAULT 0"

    const val ADD_PRE_ALERT =
        "ALTER TABLE `alarms` ADD COLUMN `preAlertMinutes` INTEGER NOT NULL DEFAULT 0"

    val statements: List<String> = listOf(
        ADD_RINGTONE,
        ADD_VIBRATE,
        ADD_RAMP_VOLUME,
        ADD_PRE_ALERT
    )
}
