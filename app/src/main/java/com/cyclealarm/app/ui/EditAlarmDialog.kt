package com.cyclealarm.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.service.RingService
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.ui.theme.timePickerContext
import kotlinx.coroutines.launch

@Composable
internal fun EditAlarmDialog(
    alarm: AlarmEntity,
    dayLabel: String,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var label by rememberSaveable(alarm.id) { mutableStateOf(alarm.label) }
    var hour by rememberSaveable(alarm.id) { mutableStateOf(alarm.hour) }
    var minute by rememberSaveable(alarm.id) { mutableStateOf(alarm.minute) }
    var enabled by rememberSaveable(alarm.id) { mutableStateOf(alarm.enabled) }
    var muted by rememberSaveable(alarm.id) { mutableStateOf(alarm.mutedDuringHoliday) }
    // v10：每闹钟响铃设置
    var ringtone by rememberSaveable(alarm.id) { mutableStateOf(alarm.ringtoneUri) }
    var vibrate by rememberSaveable(alarm.id) { mutableStateOf(alarm.vibrate) }
    var ramp by rememberSaveable(alarm.id) { mutableStateOf(alarm.rampVolume) }
    var preAlert by rememberSaveable(alarm.id) { mutableStateOf(alarm.preAlertMinutes) }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtone = picked?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_alarm_title, dayLabel)) },
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
                OutlinedButton(
                    onClick = {
                        ContextCompat.startForegroundService(
                            context,
                            RingService.ringIntent(
                                context,
                                alarm.id,
                                alarm.dayIndex,
                                alarm.hour,
                                alarm.minute,
                                label.trim().ifBlank { context.getString(R.string.alarm_fallback) } + context.getString(R.string.preview_suffix),
                                snoozed = false,
                                preview = true,
                                ringtoneUri = ringtone,
                                vibrate = vibrate,
                                rampVolume = ramp
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.preview_button))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.toggle_enable), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.toggle_enable_desc),
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
                            stringResource(R.string.holiday_mute_desc),
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
                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_ALARM
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                context.getString(R.string.ringtone_picker_title)
                            )
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            )
                            ringtone?.let {
                                runCatching { Uri.parse(it) }.getOrNull()?.let { uri ->
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                                }
                            }
                        }
                        runCatching { ringtonePicker.launch(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (ringtone == null) stringResource(R.string.ringtone_follow_system) else stringResource(R.string.ringtone_custom)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vibrate_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    CycleSwitch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it },
                        modifier = Modifier.semantics {
                            stateDescription = if (vibrate) context.getString(R.string.state_vibrate_on) else context.getString(R.string.state_vibrate_off)
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ramp_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.ramp_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CycleSwitch(
                        checked = ramp,
                        onCheckedChange = { ramp = it }
                    )
                }
                OutlinedButton(
                    onClick = {
                        preAlert = when (preAlert) {
                            0 -> 5
                            5 -> 10
                            10 -> 15
                            15 -> 30
                            else -> 0
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (preAlert == 0) stringResource(R.string.pre_alert_off) else stringResource(R.string.pre_alert_on, preAlert)
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
                            mutedDuringHoliday = muted,
                            ringtoneUri = ringtone,
                            vibrate = vibrate,
                            rampVolume = ramp,
                            preAlertMinutes = preAlert
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                // 新建（id == 0）时还没有落库，不提供「删除」
                if (alarm.id != 0) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

