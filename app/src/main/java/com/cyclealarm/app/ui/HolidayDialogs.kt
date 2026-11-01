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
internal fun HolidayEditDialog(
    holiday: HolidayEntity,
    onDismiss: () -> Unit,
    onSave: (label: String, startEpochDay: Long, endEpochDay: Long) -> Unit
) {
    val context = LocalContext.current
    var label by rememberSaveable(holiday.id) { mutableStateOf(holiday.label) }
    var start by rememberSaveable(holiday.id) { mutableStateOf(holiday.startEpochDay) }
    var end by rememberSaveable(holiday.id) { mutableStateOf(holiday.endEpochDay) }
    var pickStart by rememberSaveable(holiday.id) { mutableStateOf(false) }
    var pickEnd by rememberSaveable(holiday.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
                    Text(if (holiday.id == 0) stringResource(R.string.holiday_add) else stringResource(R.string.holiday_edit))
                },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.holiday_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { pickStart = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.holiday_start, formatEpochDay(context, start)))
                }
                OutlinedButton(
                    onClick = { pickEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.holiday_end, formatEpochDay(context, end)))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(label.trim().ifBlank { context.getString(R.string.holiday_custom_label) }, start, end)
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (pickStart) {
        HolidayDateDialog(
            title = stringResource(R.string.pick_start_date),
            initialEpochDay = start,
            onConfirm = { start = it },
            onDismiss = { pickStart = false }
        )
    }
    if (pickEnd) {
        HolidayDateDialog(
            title = stringResource(R.string.pick_end_date),
            initialEpochDay = end,
            onConfirm = { end = it },
            onDismiss = { pickEnd = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HolidayDateDialog(
    title: String,
    initialEpochDay: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = epochDayToDatePickerMillis(initialEpochDay)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val day = datePickerMillisToEpochDay(state.selectedDateMillis)
                    if (day != null) onConfirm(day)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    ) {
        DatePicker(state = state, title = { Text(title) })
    }
}
