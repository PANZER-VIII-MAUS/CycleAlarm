package com.cyclealarm.app.data

/**
 * v8 → v9：只新增 `ring_events` 表与两个索引，不改动任何既有表或数据。
 *
 * 响铃事件表用于把「漏响判定」从启发式时间戳换成「应有事件 + 是否真的响了」，
 * 同时也为后续的响铃历史页提供数据。
 */
object Migration8To9Sql {

    const val CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS `ring_events` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`alarmKind` TEXT NOT NULL, " +
            "`alarmId` INTEGER NOT NULL, " +
            "`label` TEXT NOT NULL, " +
            "`expectedAt` INTEGER NOT NULL, " +
            "`rungAt` INTEGER, " +
            "`ackedAt` INTEGER)"

    const val CREATE_INDEX_EXPECTED =
        "CREATE INDEX IF NOT EXISTS `index_ring_events_expectedAt` " +
            "ON `ring_events` (`expectedAt`)"

    const val CREATE_INDEX_ALARM =
        "CREATE INDEX IF NOT EXISTS `index_ring_events_alarmKind_alarmId` " +
            "ON `ring_events` (`alarmKind`, `alarmId`)"

    val statements: List<String> = listOf(
        CREATE_TABLE,
        CREATE_INDEX_EXPECTED,
        CREATE_INDEX_ALARM
    )
}
