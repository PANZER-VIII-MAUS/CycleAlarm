package com.cyclealarm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cyclealarm.app.R
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.ui.components.CycleCard

@Composable
internal fun CycleInfoCard(
    settings: AppSettingsEntity?,
    cycleLength: Int,
    todayDay: Int,
    onSettingsClick: () -> Unit
) {
    val subtitle = when (settings?.cycleType) {
        CycleTypes.DAILY -> stringResource(R.string.cycle_sub_daily)
        CycleTypes.WEEKLY -> stringResource(R.string.cycle_sub_weekly, stringResource(weekdayRes(((todayDay - 1) % 7) + 1)))
        else -> stringResource(R.string.cycle_sub_custom, cycleLength, todayDay)
    }
    CycleCard(onClick = onSettingsClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cycle_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSettingsClick) { Text(stringResource(R.string.settings_title)) }
        }
    }
}

