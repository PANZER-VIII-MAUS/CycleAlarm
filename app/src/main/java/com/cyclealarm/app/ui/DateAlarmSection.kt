package com.cyclealarm.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DateAlarmState
import com.cyclealarm.app.core.DateAlarmStatus
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.ui.theme.timePickerContext
import com.cyclealarm.app.ui.DateAlarmEntitySaver
import java.util.Locale

/**
 * 按日期闹钟列表（课表/排班表按日期导入的一次性闹钟）。
 * 空列表时整块不显示，避免占用主界面空间。
 */
@Composable
fun DateAlarmSection(
    alarms: List<DateAlarmEntity>,
    settings: AppSettingsEntity?,
    holidays: List<HolidayEntity>,
    onToggle: (DateAlarmEntity, Boolean) -> Unit,
    onSave: (DateAlarmEntity) -> Unit,
    onDelete: (DateAlarmEntity) -> Unit
) {
    if (alarms.isEmpty()) return
    val context = LocalContext.current
    var editing by rememberSaveable(stateSaver = DateAlarmEntitySaver) {
        mutableStateOf<DateAlarmEntity?>(null)
    }

    CycleCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.date_alarms_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.date_alarms_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            alarms.forEach { alarm ->
                val next = AlarmScheduler.nextTriggerForDateAlarm(alarm, settings, holidays)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editing = alarm }
                            .semantics { role = Role.Button }
                    ) {
                        Text(
                            formatEpochDay(context, alarm.epochDay),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${DisplayPrefs.formatTime(context, alarm.hour, alarm.minute)} · " +
                                alarm.label.ifBlank { stringResource(R.string.date_alarm_fallback) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val state = DateAlarmStatus.resolve(
                            datePassed = alarm.epochDay < java.time.LocalDate.now().toEpochDay(),
                            enabled = alarm.enabled,
                            mutedDuringHoliday = alarm.mutedDuringHoliday,
                            holidayModeEnabled = settings?.holidayModeEnabled == true,
                            inHoliday = holidays.any {
                                it.enabled && alarm.epochDay in it.startEpochDay..it.endEpochDay
                            },
                            hasNextTrigger = next != null
                        )
                        Text(
                            stringResource(DateAlarmStatus.labelRes(state)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state == DateAlarmState.PENDING) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { onDelete(alarm) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_date_alarm))
                    }
                    CycleSwitch(
                        checked = alarm.enabled,
                        onCheckedChange = { onToggle(alarm, it) },
                        modifier = Modifier.semantics {
                            stateDescription = if (alarm.enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
                        }
                    )
                }
            }
        }
    }

    editing?.let { alarm ->
        DateAlarmEditDialog(
            alarm = alarm,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            }
        )
    }
}

@Composable
private fun DateAlarmEditDialog(
    alarm: DateAlarmEntity,
    onDismiss: () -> Unit,
    onSave: (DateAlarmEntity) -> Unit
) {
    val context = LocalContext.current
    var label by rememberSaveable(alarm.id) { mutableStateOf(alarm.label) }
    var hour by rememberSaveable(alarm.id) { mutableStateOf(alarm.hour) }
    var minute by rememberSaveable(alarm.id) { mutableStateOf(alarm.minute) }
    var enabled by rememberSaveable(alarm.id) { mutableStateOf(alarm.enabled) }
    var muted by rememberSaveable(alarm.id) { mutableStateOf(alarm.mutedDuringHoliday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_alarm_title, formatEpochDay(context, alarm.epochDay))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.edit_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            timePickerContext(context),
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            DisplayPrefs.is24Hour(context)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.edit_time, DisplayPrefs.formatTime(context, hour, minute)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.toggle_enable), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.date_alarm_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CycleSwitch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.semantics {
                            stateDescription = if (enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.holiday_mute_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.date_alarm_mute_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CycleSwitch(
                        checked = muted,
                        onCheckedChange = { muted = it },
                        modifier = Modifier.semantics {
                            stateDescription = if (muted) context.getString(R.string.state_holiday_mute_on) else context.getString(R.string.state_holiday_mute_off)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        alarm.copy(
                            label = label.trim(),
                            hour = hour,
                            minute = minute,
                            enabled = enabled,
                            mutedDuringHoliday = muted
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
