package com.cyclealarm.app.data

object Migration6To7Sql {
    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `snoozes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `alarmId` INTEGER NOT NULL, `dayIndex` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `triggerAt` INTEGER NOT NULL, `requestCode` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `import_snapshots` (`id` INTEGER NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_snoozes_triggerAt` ON `snoozes` (`triggerAt`)",
        "CREATE INDEX IF NOT EXISTS `index_snoozes_alarmId` ON `snoozes` (`alarmId`)",
        "CREATE INDEX IF NOT EXISTS `index_alarms_dayIndex_hour_minute` ON `alarms` (`dayIndex`, `hour`, `minute`)"
    )
}
