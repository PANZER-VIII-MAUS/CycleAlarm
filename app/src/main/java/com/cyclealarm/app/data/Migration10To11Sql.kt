package com.cyclealarm.app.data

/**
 * v10 → v11：给 `ring_events` 加唯一约束 `(alarmKind, alarmId, expectedAt)`。
 *
 * 旧实现用 `OnConflictStrategy.IGNORE` 但没有唯一索引（等于没用），snooze 每次触发都会
 * 再插一条同一 expectedAt 的事件，响铃历史出现重复行、漏响也可能被重复计数。
 *
 * 迁移步骤（顺序很重要）：
 * 1. **先合并**：同一组重复行里，只要任意一行 `rungAt`/`ackedAt` 非空，就并入保留行。
 *    旧版只保留 `MIN(id)` 会丢掉「另一行响过」的事实，历史页会把响过的显示成漏响（五轮复审 B#1）。
 * 2. 再删重复（保留 `MIN(id)` 那一行，此时它已带上合并后的时间）。
 * 3. 最后建唯一索引。
 */
object Migration10To11Sql {

    /** 把 rungAt / ackedAt 合并进每组保留行（MIN(id)），取组内最早的「响过/已处理」时间。 */
    const val MERGE_DUPLICATE_MARKS =
        "UPDATE `ring_events` SET " +
            "`rungAt` = (SELECT MIN(r.`rungAt`) FROM `ring_events` r " +
            "WHERE r.`alarmKind` = `ring_events`.`alarmKind` " +
            "AND r.`alarmId` = `ring_events`.`alarmId` " +
            "AND r.`expectedAt` = `ring_events`.`expectedAt`), " +
            "`ackedAt` = (SELECT MIN(a.`ackedAt`) FROM `ring_events` a " +
            "WHERE a.`alarmKind` = `ring_events`.`alarmKind` " +
            "AND a.`alarmId` = `ring_events`.`alarmId` " +
            "AND a.`expectedAt` = `ring_events`.`expectedAt`) " +
            "WHERE `id` IN (" +
            "SELECT MIN(`id`) FROM `ring_events` GROUP BY `alarmKind`, `alarmId`, `expectedAt`)"

    /** 同一 (kind, alarmId, expectedAt) 只保留 id 最小的一行。 */
    const val DELETE_DUPLICATES =
        "DELETE FROM `ring_events` WHERE `id` NOT IN (" +
            "SELECT MIN(`id`) FROM `ring_events` GROUP BY `alarmKind`, `alarmId`, `expectedAt`)"

    const val CREATE_UNIQUE_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_ring_events_alarmKind_alarmId_expectedAt` " +
            "ON `ring_events` (`alarmKind`, `alarmId`, `expectedAt`)"

    val statements: List<String> = listOf(
        MERGE_DUPLICATE_MARKS,
        DELETE_DUPLICATES,
        CREATE_UNIQUE_INDEX
    )
}
