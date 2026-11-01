package com.cyclealarm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.ui.theme.CycleAlarmTheme

@Preview(name = "循环周期卡", showBackground = true)
@Composable
private fun CycleInfoCardPreview() {
    CycleAlarmTheme {
        CycleInfoCard(
            settings = AppSettingsEntity(
                id = 1,
                cycleLength = 6,
                cycleStartEpochDay = 20_000L,
                cycleType = CycleTypes.CUSTOM
            ),
            cycleLength = 6,
            todayDay = 3,
            onSettingsClick = {}
        )
    }
}

@Preview(name = "循环周期卡 · 每周", showBackground = true)
@Composable
private fun CycleInfoCardWeeklyPreview() {
    CycleAlarmTheme {
        CycleInfoCard(
            settings = AppSettingsEntity(
                id = 1,
                cycleLength = 7,
                cycleStartEpochDay = 20_000L,
                cycleType = CycleTypes.WEEKLY
            ),
            cycleLength = 7,
            todayDay = 2,
            onSettingsClick = {}
        )
    }
}
