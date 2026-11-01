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
        stringResource(R.string.holiday_header_on, holidays.size)
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

