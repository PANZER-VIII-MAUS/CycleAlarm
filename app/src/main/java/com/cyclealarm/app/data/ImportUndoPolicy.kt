package com.cyclealarm.app.data

/**
 * 撤销时的「清理范围」计算（纯逻辑）。
 *
 * 撤销 = 把数据恢复到导入前的快照。导入之后新建的闹钟会被移除，它们才是需要
 * 取消排期、清理 snooze 的对象；快照里本来就有的老闹钟会被恢复，不能连它们的
 * snooze 一起清掉（复审 §7.1 指出的副作用）。
 */
object ImportUndoPolicy {

    /** 需要取消排期的 id：现存但快照里没有的（即导入新建的）。 */
    fun idsToCancelAfterUndo(
        currentIds: Collection<Int>,
        snapshotIds: Collection<Int>
    ): List<Int> {
        val snapshot = snapshotIds.toSet()
        return currentIds.filterNot { it in snapshot }
    }
}
