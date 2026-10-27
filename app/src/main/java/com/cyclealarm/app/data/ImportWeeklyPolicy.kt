package com.cyclealarm.app.data

import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind

object ImportWeeklyPolicy {
    fun requiresWeeklySwitch(entries: List<ImportedEntry>): Boolean =
        entries.any { it.kind == ScheduleKind.WEEKLY }
}
