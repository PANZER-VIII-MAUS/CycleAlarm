package com.cyclealarm.app.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.components.CycleSwitch

@Composable
internal fun HolidayCard(
    holidayModeEnabled: Boolean,
    holidays: List<HolidayEntity>,
    onToggleMode: (Boolean) -> Unit,
    onImportChina: () -> Unit,
    onAddCustom: () -> Unit,
    onEdit: (HolidayEntity) -> Unit,
    onToggleHoliday: (HolidayEntity, Boolean) -> Unit,
    onSetAllMuted: (Boolean) -> Unit,
    onDeleteHoliday: (HolidayEntity) -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val summary = if (holidayModeEnabled) {
        pluralStringResource(R.plurals.holiday_header_on, holidays.size, holidays.size)
    } else {
        stringResource(R.string.holiday_header_off)
    }

    CycleCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) context.getString(R.string.state_expanded) else context.getString(R.string.state_collapsed)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (holidayModeEnabled) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (expanded) {
                            stringResource(R.string.cd_collapse_holiday)
                        } else {
                            stringResource(R.string.cd_expand_holiday)
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.holiday_enable), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.holiday_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CycleSwitch(
                        checked = holidayModeEnabled,
                        onCheckedChange = onToggleMode,
                        modifier = Modifier.semantics {
                            stateDescription = if (holidayModeEnabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
                        }
                    )
                }

                if (holidays.isEmpty()) {
                    Text(
                        stringResource(R.string.holiday_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    holidays.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onEdit(h) }
                                    .semantics { role = Role.Button }
                            ) {
                                Text(
                                    h.label.ifBlank { stringResource(R.string.holiday_custom_label) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${formatEpochDay(context, h.startEpochDay)} ~ ${formatEpochDay(context, h.endEpochDay)}" +
                                        when {
                                            !holidayModeEnabled -> stringResource(R.string.holiday_suffix_master_off)
                                            !h.enabled -> stringResource(R.string.holiday_suffix_disabled)
                                            else -> ""
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onEdit(h) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_holiday))
                            }
                            IconButton(onClick = { onDeleteHoliday(h) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_holiday))
                            }
                            CycleSwitch(
                                checked = h.enabled,
                                onCheckedChange = { onToggleHoliday(h, it) },
                                modifier = Modifier.semantics {
                                    stateDescription = when {
                                        !holidayModeEnabled -> context.getString(R.string.holiday_master_off)
                                        h.enabled -> context.getString(R.string.holiday_state_on)
                                        else -> context.getString(R.string.holiday_state_disabled)
                                    }
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImportChina,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.holiday_import))
                    }
                    OutlinedButton(
                        onClick = onAddCustom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.holiday_add_custom))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSetAllMuted(true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.holiday_mute_all_on))
                    }
                    OutlinedButton(
                        onClick = { onSetAllMuted(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.holiday_mute_all_off))
                    }
                }
                Text(
                    stringResource(R.string.holiday_mute_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.holiday_data_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

