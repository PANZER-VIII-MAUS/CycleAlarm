package com.cyclealarm.app.data

import android.util.Log

/**
 * 响铃事件的幂等登记。
 *
 * 背景（五轮复审 B#2）：唯一索引 + `OnConflictStrategy.IGNORE` 的组合会让「同一触发点再次登记」
 * 返回 -1，调用方拿不到行 id，随后的 `markRung` 无处可写 → 该次响铃永远显示成「漏响」。
 *
 * 规则：同一 (kind, alarmId, expectedAt) 只保留一行；再次登记时返回**已有行 id**，
 * 并把新传来的 label 补上（旧行 label 可能是空串）。
 */
object RingEventStore {

    private const val TAG = "RingEventStore"

    suspend fun register(
        dao: RingEventDao,
        alarmKind: String,
        alarmId: Int,
        label: String,
        expectedAt: Long
    ): Long {
        val event = RingEventEntity(
            alarmKind = alarmKind,
            alarmId = alarmId,
            label = label,
            expectedAt = expectedAt
        )
        val inserted = runCatching { dao.insert(event) }.getOrElse {
            Log.e(TAG, "insert ring event failed", it)
            return 0L
        }
        if (inserted > 0L) return inserted
        // 已存在同一触发点：返回已有行，避免 id=0 导致 rungAt 永远写不进去
        val existing = runCatching { dao.find(alarmKind, alarmId, expectedAt) }.getOrNull()
            ?: return 0L
        if (existing.label.isBlank() && label.isNotBlank()) {
            runCatching { dao.updateLabel(existing.id, label) }
        }
        return existing.id
    }
}
