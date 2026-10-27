package com.cyclealarm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSnapshotCodecTest {

    @Test
    fun `round trips lists and settings including delimiters in labels`() {
        val snapshot = AlarmImportWriter.Snapshot(
            alarms = listOf(
                AlarmEntity(
                    id = 9,
                    dayIndex = 2,
                    hour = 7,
                    minute = 30,
                    label = "高数\t教室\nA",
                    planGenerated = true
                )
            ),
            dateAlarms = listOf(
                DateAlarmEntity(
                    id = 4,
                    epochDay = 20_000,
                    hour = 8,
                    minute = 20,
                    label = "排班|实验",
                    mutedDuringHoliday = false
                )
            ),
            settings = AppSettingsEntity(
                cycleLength = 6,
                cycleStartEpochDay = 19_998,
                cycleType = CycleTypes.CUSTOM,
                holidayModeEnabled = false,
                plansSeeded = true
            )
        )

        assertEquals(snapshot, ImportSnapshotCodec.decode(ImportSnapshotCodec.encode(snapshot)))
    }
}
