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
internal fun DayPlanCard(
    dayText: String,
    isToday: Boolean,
    plan: DayPlanEntity?,
    planType: ActivityTypeEntity?,
    manualAlarms: List<AlarmEntity>,
    onOpenPlan: () -> Unit,
    onTogglePlan: (Boolean) -> Unit,
    onAddAlarm: () -> Unit,
    onEditManual: (AlarmEntity) -> Unit,
    onToggleManual: (AlarmEntity, Boolean) -> Unit,
    onDeleteManual: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    // 时间以安排自身时间为准（同步逻辑保证与闹钟一致）；非响铃状态显示「未安排」占位
    val ringsToday = plan != null && planType != null && planType.rings
    val showTime = plan != null && ringsToday
    val timeText = if (plan != null && showTime) {
        DisplayPrefs.formatClock(context, plan.hour, plan.minute)
    } else {
        stringResource(R.string.plan_unset)
    }
    val timeStyle = if (showTime) {
        MaterialTheme.typography.headlineMedium
    } else {
        // 占位文案：字号缩到 20sp / Medium，但保留 headlineMedium 的 lineHeight(36sp)，卡片高度不变
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
    val timeColor = if (showTime) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val baseSubtitle = when {
        plan == null || planType == null -> stringResource(R.string.plan_tap_to_set)
        !planType.rings -> stringResource(R.string.plan_rest)
        !plan.enabled -> stringResource(R.string.plan_paused)
        else -> planType.name
    }
    // 12 小时制时，把「上午/下午」挪到副标题前缀，避免大行被撑宽
    val amPm = if (plan != null && showTime) DisplayPrefs.amPmLabel(context, plan.hour) else null
    val subtitle = if (amPm != null) "$amPm · $baseSubtitle" else baseSubtitle
    // 开关语义 = 今天会不会响：休息/未安排置灰关闭；启用=开（可关掉暂停）；已暂停=关（可点开恢复）
    val switchOn = ringsToday && plan?.enabled == true
    val switchEnabled = ringsToday
    val planStateDescription = when {
        !ringsToday -> stringResource(R.string.day_not_ringing)
        switchOn -> stringResource(R.string.state_on)
        else -> stringResource(R.string.state_off)
    }
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = LocalExtraColors.current.switchOffTrack,
        checkedThumbColor = Color.White,
        uncheckedThumbColor = Color.White
    )
    val highlightColor = if (MaterialTheme.colorScheme.background == DarkBackground) {
        LocalExtraColors.current.primaryDark
    } else {
        MaterialTheme.colorScheme.primary
    }
    val cardModifier = if (isToday) {
        Modifier.border(1.5.dp, highlightColor, RoundedCornerShape(24.dp))
    } else {
        Modifier
    }

    CycleCard(
        modifier = cardModifier,
        onClick = onOpenPlan,
        onClickLabel = stringResource(R.string.cd_open_plan)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$dayText $baseSubtitle $timeText"
                        stateDescription = planStateDescription
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dayText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .size(6.dp)
                                .background(highlightColor, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                // 固定最小宽度：让「07:30」与「未安排」等各行中间列同宽、中心线一致
                Column(
                    modifier = Modifier.widthIn(min = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        timeText,
                        style = timeStyle,
                        color = timeColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onAddAlarm) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_alarm),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CycleSwitch(
                checked = switchOn,
                onCheckedChange = onTogglePlan,
                enabled = switchEnabled,
                colors = switchColors,
                modifier = Modifier.semantics {
                    stateDescription = planStateDescription
                }
            )
        }
        manualAlarms.forEach { alarm ->
            ManualAlarmRow(
                alarm = alarm,
                switchColors = switchColors,
                onEdit = { onEditManual(alarm) },
                onToggle = { onToggleManual(alarm, it) },
                onDelete = { onDeleteManual(alarm) }
            )
        }
    }
}


@Composable
internal fun ManualAlarmRow(
    alarm: AlarmEntity,
    switchColors: SwitchColors,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .semantics {
                    role = Role.Button
                    stateDescription = if (alarm.enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
                }
        ) {
            Text(
                "${DisplayPrefs.formatTime(context, alarm.hour, alarm.minute)} · " +
                    alarm.label.ifBlank { stringResource(R.string.alarm_fallback) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_delete_alarm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CycleSwitch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = switchColors,
            modifier = Modifier.semantics {
                stateDescription = if (alarm.enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
        )
    }
}

