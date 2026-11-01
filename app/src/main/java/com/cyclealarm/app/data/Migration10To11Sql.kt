package com.cyclealarm.app.data

/**
 * v10 → v11：给 `ring_events` 加唯一约束 `(alarmKind, alarmId, expectedAt)`。
 *
 * 旧实现用 `OnConflictStrategy.IGNORE` 但没有唯一索引（等于没用），snooze 每次触发都会
 * 再插一条同一 expectedAt 的事件，响铃历史出现重复行、漏响也可能被重复计数。
 *
 * 迁移步骤：先删历史重复（保留 id 最小的一条），再建唯一索引。
 */
object Migration10To11Sql {

    /** 同一 (kind, alarmId, expectedAt) 只保留 id 最小的一行。 */
    const val DELETE_DUPLICATES =
        "DELETE FROM `ring_events` WHERE `id` NOT IN (" +
            "SELECT MIN(`id`) FROM `ring_events` GROUP BY `alarmKind`, `alarmId`, `expectedAt`)"

    const val CREATE_UNIQUE_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_ring_events_alarmKind_alarmId_expectedAt` " +
            "ON `ring_events` (`alarmKind`, `alarmId`, `expectedAt`)"

    val statements: List<String> = listOf(DELETE_DUPLICATES, CREATE_UNIQUE_INDEX)
}
