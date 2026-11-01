package com.cyclealarm.app.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.NextAlarm
import com.cyclealarm.app.core.NextAlarmDisplay
import com.cyclealarm.app.core.NextAlarmFinder
import com.cyclealarm.app.core.NextAlarmKind
import com.cyclealarm.app.core.cycleDayOf
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.data.DayPlanEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.service.RingService
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.ui.components.PillButton
import com.cyclealarm.app.ui.import.ImportActivity
import com.cyclealarm.app.ui.settings.SettingsActivity
import com.cyclealarm.app.ui.plan.PlanPickerDialog
import com.cyclealarm.app.ui.plan.PlanTimeDialog
import com.cyclealarm.app.ui.plan.PlanTypeManagerDialog
import com.cyclealarm.app.ui.theme.LocalExtraColors
import com.cyclealarm.app.ui.theme.DarkBackground
import com.cyclealarm.app.ui.theme.timePickerContext
import com.cyclealarm.app.viewmodel.AlarmViewModel
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

