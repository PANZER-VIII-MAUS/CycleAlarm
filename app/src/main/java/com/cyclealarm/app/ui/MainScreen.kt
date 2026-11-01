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

private fun weekdayRes(isoDay: Int): Int = when (isoDay) {
    1 -> R.string.week_mon
    2 -> R.string.week_tue
    3 -> R.string.week_wed
    4 -> R.string.week_thu
    5 -> R.string.week_fri
    6 -> R.string.week_sat
    else -> R.string.week_sun
}

/** 按循环方式生成某天的显示名：每天 / 周一..周日 / 第 X 天。 */
private fun dayLabel(
    context: android.content.Context,
    settings: AppSettingsEntity?,
    dayIndex: Int
): String = when (settings?.cycleType) {
    CycleTypes.DAILY -> context.getString(R.string.cycle_daily)
    CycleTypes.WEEKLY -> context.getString(weekdayRes((dayIndex % 7) + 1))
    else -> context.getString(R.string.ring_day_format, dayIndex + 1)
}

@Composable
private fun NextAlarmCard(
    nextAlarm: NextAlarm?,
    now: Long,
    settings: AppSettingsEntity?,
    modifier: Modifier = Modifier,
    onSkipNext: () -> Unit = {}
) {
    val context = LocalContext.current
    CycleCard(modifier = modifier) {
        if (nextAlarm == null || settings == null) {
            Text(
                text = stringResource(R.string.main_no_alarm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.main_no_alarm_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = DisplayPrefs.formatClock(
                                context, nextAlarm.hour, nextAlarm.minute
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            color = LocalExtraColors.current.navy
                        )
                        if (!DisplayPrefs.is24Hour(context)) {
                            Text(
                                text = DisplayPrefs.amPmLabel(context, nextAlarm.hour).orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = NextAlarmDisplay.countdownText(context, nextAlarm.triggerAt, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 倒计时变化时让 TalkBack 播报，但不要打断用户当前的朗读
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Text(
                    text = nextAlarmSourceLabel(nextAlarm, settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            if (!nextAlarm.isSnooze) {
                TextButton(
                    onClick = onSkipNext,
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text(stringResource(R.string.main_skip_next)) }
            }
        }
    }
}

@Composable
private fun nextAlarmSourceLabel(
    alarm: NextAlarm,
    settings: AppSettingsEntity
): String = when (alarm.kind) {
    NextAlarmKind.CYCLE -> {
        val triggerDate = Instant.ofEpochMilli(alarm.triggerAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        when (settings.cycleType) {
            CycleTypes.WEEKLY -> stringResource(weekdayRes(triggerDate.dayOfWeek.value))
            CycleTypes.DAILY -> stringResource(R.string.cycle_daily)
            else -> stringResource(
                R.string.ring_day_format,
                cycleDayOf(settings, triggerDate.toEpochDay())
            )
        }
    }

    NextAlarmKind.DATE -> stringResource(R.string.main_date_kind)
    NextAlarmKind.SNOOZE -> stringResource(R.string.main_snooze_kind)
}

/** 周一..周日（ISO dayOfWeek：1=周一）。 */
@Composable
private fun weekdayShort(dayOfWeek: Int): String = stringResource(weekdayRes(dayOfWeek))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AlarmViewModel = viewModel()) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val dateAlarms by viewModel.dateAlarms.collectAsStateWithLifecycle()
    val snoozes by viewModel.snoozes.collectAsStateWithLifecycle()
    val activityTypes by viewModel.activityTypes.collectAsStateWithLifecycle()
    val dayPlans by viewModel.dayPlans.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cycleLength by viewModel.cycleLength.collectAsStateWithLifecycle()
    val databaseError by viewModel.databaseError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    var refreshTick by rememberSaveable { mutableStateOf(0) }
    var todayEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }

    fun refreshUiClock() {
        todayEpochDay = LocalDate.now().toEpochDay()
        refreshTick++
    }

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        uiScope.launch {
            // 连删时替换上一条提示，避免排队等满 5 秒
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(5_000L) {
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = context.getString(R.string.action_undo),
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite
                )
            }
            when (result) {
                SnackbarResult.ActionPerformed -> onUndo()
                else -> snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
    }

    fun deletePlanWithUndo(day: Int) {
        uiScope.launch {
            viewModel.deleteDayPlanWithUndo(day, dayLabel(context, settings, day))
        }
    }

    // 待撤销状态放在 ViewModel：转屏/页面重建后 Snackbar 会重新出现，不会丢撤销机会
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingUndo) {
        val pending = pendingUndo ?: return@LaunchedEffect
        val message = when (pending) {
            is AlarmViewModel.PendingUndo.Plan -> pending.message
            is AlarmViewModel.PendingUndo.Type -> pending.message
        }
        showUndoSnackbar(message) { viewModel.undoPending() }
        viewModel.clearPendingUndo()
    }

    // 只在前台计时：后台空转的 60 秒循环没有意义，还会阻止进程休眠
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(60_000)
                refreshUiClock()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshUiClock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val prefs = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    var showPermissionGuide by rememberSaveable {
        mutableStateOf(!prefs.getBoolean("onboarding_done", false))
    }
    var editingAlarm by rememberSaveable(stateSaver = AlarmEntitySaver) {
        mutableStateOf<AlarmEntity?>(null)
    }
    var editingHoliday by rememberSaveable(stateSaver = HolidayEntitySaver) {
        mutableStateOf<HolidayEntity?>(null)
    }
    var planPickerDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var planTimeDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var showTypeManager by rememberSaveable { mutableStateOf(false) }
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    val plansByDay = remember(dayPlans) { dayPlans.associateBy { it.dayIndex } }
    val typesById = remember(activityTypes) { activityTypes.associateBy { it.id } }
    val typeUsage = remember(dayPlans) { dayPlans.groupingBy { it.typeId }.eachCount() }
    val now = remember(refreshTick) { System.currentTimeMillis() }
    val nextAlarm = remember(now, alarms, dateAlarms, snoozes, settings, holidays) {
        settings?.let { currentSettings ->
            NextAlarmFinder.find(
                now = now,
                cycleAlarms = alarms,
                dateAlarms = dateAlarms,
                snoozes = snoozes,
                settings = currentSettings,
                holidays = holidays
            )
        }
    }

    fun finishOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        showPermissionGuide = false
    }

    val currentSettings = settings
    val todayDay = currentSettings?.let { cycleDayOf(it, todayEpochDay) } ?: 1
    val todayText = if (currentSettings == null) {
        stringResource(R.string.main_today_first_day)
    } else {
        if (currentSettings.cycleType == CycleTypes.WEEKLY) {
            stringResource(
                R.string.main_today_weekday,
                stringResource(weekdayRes(((todayDay - 1) % 7) + 1))
            )
        } else {
            stringResource(R.string.main_today_day, todayDay)
        }
    }
    val todayIndex = todayDay - 1
    val orderedDayIndices = remember(cycleLength, todayIndex) {
        if (todayIndex in 0 until cycleLength) {
            listOf(todayIndex) + (0 until cycleLength).filter { it != todayIndex }
        } else {
            (0 until cycleLength).toList()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.main_title), style = MaterialTheme.typography.titleLarge)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { showTypeManager = true }) {
                        Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.cd_alarm_types))
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, ImportActivity::class.java)) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.cd_import_timetable))
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_cycle_settings))
                    }
                    IconButton(onClick = { showPermissionGuide = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.cd_permissions))
                    }
                }
            )
        },
        bottomBar = {
            // 底部固定主按钮：内容由 Scaffold 的 innerPadding 让位，不会遮挡最后一张卡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                PillButton(
                    text = stringResource(R.string.main_add_alarm),
                    onClick = { showDayPicker = true }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NextAlarmCard(
                nextAlarm = nextAlarm,
                now = now,
                settings = settings,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                onSkipNext = { nextAlarm?.let { viewModel.skipNextAlarm(it) } }
            )
            // 固定标题区：滚动列表时保持不动
            Text(
                text = todayText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "cycle") {
                    CycleInfoCard(
                        settings = settings,
                        cycleLength = cycleLength,
                        todayDay = todayDay,
                        onSettingsClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    )
                }

                item(key = "dateAlarms") {
                    DateAlarmSection(
                        alarms = dateAlarms,
                        settings = settings,
                        holidays = holidays,
                        onToggle = { alarm, enabled -> viewModel.setDateAlarmEnabled(alarm, enabled) },
                        onSave = { viewModel.saveDateAlarm(it) },
                        onDelete = { alarm ->
                            viewModel.deleteDateAlarm(alarm.id)
                            showUndoSnackbar(context.getString(R.string.snack_deleted_date_alarm)) {
                                viewModel.restoreDateAlarm(alarm)
                            }
                        }
                    )
                }

                items(
                    count = orderedDayIndices.size,
                    key = { index -> "day${orderedDayIndices[index]}" }
                ) { position ->
                    val dayIndex = orderedDayIndices[position]
                    val plan = plansByDay[dayIndex]
                    val type = plan?.let { typesById[it.typeId] }
                    val manualAlarms = alarms
                        .filter { !it.planGenerated && it.dayIndex == dayIndex }
                        .sortedBy { it.hour * 60 + it.minute }
                    DayPlanCard(
                        dayText = dayLabel(context, settings, dayIndex),
                        isToday = dayIndex == todayIndex,
                        plan = plan,
                        planType = type,
                        manualAlarms = manualAlarms,
                        onOpenPlan = {
                            // 总是先进安排选择器：既能直接换类型，也能从「当前安排」行进到改时间
                            planPickerDay = dayIndex
                        },
                        onTogglePlan = { enabled -> viewModel.setPlanEnabled(dayIndex, enabled) },
                        onAddAlarm = { viewModel.addAlarmToDay(dayIndex) },
                        onEditManual = { editingAlarm = it },
                        onToggleManual = { alarm, enabled -> viewModel.setEnabled(alarm, enabled) },
                        onDeleteManual = { alarm ->
                            viewModel.deleteAlarm(alarm.id)
                            showUndoSnackbar(context.getString(R.string.snack_deleted_alarm)) {
                                viewModel.restoreAlarm(alarm)
                            }
                        }
                    )
                }

                item(key = "holiday") {
                    HolidayCard(
                        holidayModeEnabled = settings?.holidayModeEnabled ?: true,
                        holidays = holidays,
                        onToggleMode = { viewModel.setHolidayMode(it) },
                        onImportChina = { viewModel.importChinaHolidays() },
                        onAddCustom = {
                            val today = LocalDate.now().toEpochDay()
                            editingHoliday = HolidayEntity(
                                startEpochDay = today,
                                endEpochDay = today,
                                enabled = true
                            )
                        },
                        onEdit = { editingHoliday = it },
                        onToggleHoliday = { h, enabled ->
                            viewModel.setHolidayEnabled(h.id, enabled)
                        },
                        onSetAllMuted = { viewModel.setAllAlarmsMutedDuringHoliday(it) },
                        onDeleteHoliday = { holiday ->
                            viewModel.deleteHoliday(holiday.id)
                            showUndoSnackbar(context.getString(R.string.snack_deleted_holiday)) {
                                viewModel.restoreHoliday(holiday)
                            }
                        }
                    )
                }

                item(key = "tip") {
                    Text(
                        text = stringResource(R.string.main_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showPermissionGuide) {
        PermissionGuideDialog(onDismiss = ::finishOnboarding)
    }

    databaseError?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.db_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearDatabaseError) { Text(stringResource(R.string.db_error_ok)) }
            }
        )
    }

    editingAlarm?.let { alarm ->
        EditAlarmDialog(
            alarm = alarm,
            dayLabel = dayLabel(context, settings, alarm.dayIndex),
            onDismiss = { editingAlarm = null },
            onSave = { saved ->
                viewModel.saveAlarm(saved)
                editingAlarm = null
            },
            onDelete = {
                viewModel.deleteAlarm(alarm.id)
                showUndoSnackbar(context.getString(R.string.snack_deleted_alarm)) {
                    viewModel.restoreAlarm(alarm)
                }
                editingAlarm = null
            }
        )
    }

    editingHoliday?.let { holiday ->
        HolidayEditDialog(
            holiday = holiday,
            onDismiss = { editingHoliday = null },
            onSave = { label, start, end ->
                viewModel.saveHoliday(
                    holiday.copy(
                        label = label,
                        startEpochDay = start,
                        endEpochDay = end
                    )
                )
                editingHoliday = null
            }
        )
    }

    planPickerDay?.let { day ->
        PlanPickerDialog(
            dayText = dayLabel(context, settings, day),
            types = activityTypes,
            currentPlan = plansByDay[day],
            onSelect = { type ->
                viewModel.setDayPlan(day, type.id)
                planPickerDay = null
            },
            onClear = {
                deletePlanWithUndo(day)
                planPickerDay = null
            },
            onEditCurrentTime = {
                planPickerDay = null
                planTimeDay = day
            },
            onManage = {
                planPickerDay = null
                showTypeManager = true
            },
            onDismiss = { planPickerDay = null }
        )
    }

    planTimeDay?.let { day ->
        val plan = plansByDay[day]
        if (plan != null) {
            PlanTimeDialog(
                title = stringResource(R.string.plan_time_title, dayLabel(context, settings, day)),
                hour = plan.hour,
                minute = plan.minute,
                onSave = { hour, minute ->
                    viewModel.updatePlanTime(day, hour, minute)
                    planTimeDay = null
                },
                onDeletePlan = {
                    deletePlanWithUndo(day)
                    planTimeDay = null
                },
                onDismiss = { planTimeDay = null }
            )
        }
    }

    if (showDayPicker) {
        AlertDialog(
            onDismissRequest = { showDayPicker = false },
            title = { Text(stringResource(R.string.main_add_alarm)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    repeat(cycleLength) { index ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDayPicker = false
                                    // 「添加闹钟」= 新增一条独立的手动闹钟（默认 07:00），
                                    // 改「安排时间」仍然走点卡片那条路径。
                                    editingAlarm = AlarmEntity(dayIndex = index, hour = 7, minute = 0)
                                }
                                .semantics { role = Role.Button }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dayLabel(context, settings, index),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (index == todayIndex) {
                                Text(
                                    stringResource(R.string.day_picker_today),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDayPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showTypeManager) {
        PlanTypeManagerDialog(
            types = activityTypes,
            usage = typeUsage,
            onAdd = { name, hour, minute, rings ->
                viewModel.addActivityType(name, hour, minute, rings)
            },
            onUpdate = { type, applyToExisting ->
                viewModel.updateActivityType(type, applyToExisting)
            },
            onDelete = { id ->
                uiScope.launch { viewModel.deleteActivityTypeWithUndo(id) }
            },
            onMove = { id, up -> viewModel.moveActivityType(id, up) },
            onDismiss = { showTypeManager = false }
        )
    }
}

@Composable
private fun HolidayCard(
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
                                    "${formatEpochDay(h.startEpochDay)} ~ ${formatEpochDay(h.endEpochDay)}" +
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

@Composable
private fun HolidayEditDialog(
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
                    Text(stringResource(R.string.holiday_start, formatEpochDay(start)))
                }
                OutlinedButton(
                    onClick = { pickEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.holiday_end, formatEpochDay(end)))
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

@Composable
private fun CycleInfoCard(
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

@Composable
private fun DayPlanCard(
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
private fun ManualAlarmRow(
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

@Composable
private fun EditAlarmDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayDateDialog(
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
