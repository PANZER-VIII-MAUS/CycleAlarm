package com.cyclealarm.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.core.NextAlarm
import com.cyclealarm.app.core.NextAlarmKind
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.ui.theme.CycleAlarmTheme

private val previewSettings = AppSettingsEntity(
    id = 1,
    cycleLength = 4,
    cycleStartEpochDay = 20_000L,
    cycleType = CycleTypes.CUSTOM
)

@Preview(name = "下次响铃 · 有闹钟", showBackground = true)
@Composable
private fun NextAlarmCardPreview() {
    CycleAlarmTheme {
        NextAlarmCard(
            nextAlarm = NextAlarm(
                triggerAt = 1_800_000_000_000L,
                hour = 7,
                minute = 30,
                label = "早课",
                kind = NextAlarmKind.CYCLE,
                alarmId = 1
            ),
            now = 1_800_000_000_000L - 8 * 60 * 60_000L,
            settings = previewSettings,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "下次响铃 · 空态", showBackground = true)
@Composable
private fun NextAlarmCardEmptyPreview() {
    CycleAlarmTheme {
        NextAlarmCard(
            nextAlarm = null,
            now = 1_800_000_000_000L,
            settings = previewSettings,
            modifier = Modifier.padding(16.dp)
        )
    }
}
