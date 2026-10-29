package com.cyclealarm.app.data

import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind

object ImportWeeklyPolicy {
    fun requiresWeeklySwitch(entries: List<ImportedEntry>): Boolean =
        entries.any { it.kind == ScheduleKind.WEEKLY }

    /**
     * 写入层门禁：只有「确实有周条目」且「调用方已确认」时才允许把循环方式改为每周。
     *
     * UI 层的确认卡片只能拦住 UI 调用方；测试、批处理或将来新增的入口如果直接调
     * [AlarmImportWriter.write]，必须显式传 confirmed = true 才能改用户的循环配置。
     */
    fun allowCycleSwitch(entries: List<ImportedEntry>, confirmed: Boolean): Boolean =
        confirmed && requiresWeeklySwitch(entries)
}
