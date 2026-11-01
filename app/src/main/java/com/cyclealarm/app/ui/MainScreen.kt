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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.cyclealarm.app.core.EffectiveTriggerPolicy
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

/** 按循环方式生成某天的显示名：每天 / 周一..周日 / 第 X 天。 */
internal fun dayLabel(
    context: android.content.Context,
    settings: AppSettingsEntity?,
    dayIndex: Int
): String = when (settings?.cycleType) {
    CycleTypes.DAILY -> context.getString(R.string.cycle_daily)
    CycleTypes.WEEKLY -> context.getString(weekdayRes((dayIndex % 7) + 1))
    else -> context.getString(R.string.ring_day_format, dayIndex + 1)
}


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
    // 横屏/平板：内容不必拉满整屏，收窄到 640dp 居中更好读
    val configuration = LocalConfiguration.current
    // 横屏/矮屏：固定头部会把列表挤没，改成全部内容一起滚动
    val shortScreen = configuration.screenHeightDp < 480
    val contentPadding = if (configuration.screenWidthDp > 700) {
        (((configuration.screenWidthDp - 640) / 2).coerceAtLeast(20)).dp
    } else {
        20.dp
    }
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
                holidays = holidays,
                // 首页必须认「跳过下一次 / 今天临时静音」，否则会显示一条不会响的闹钟
                cycleTrigger = { alarm, from ->
                    EffectiveTriggerPolicy.nextCycleTrigger(context, alarm, currentSettings, holidays, from)
                },
                dateTrigger = { alarm, from ->
                    EffectiveTriggerPolicy.nextDateTrigger(context, alarm, currentSettings, holidays, from)
                }
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
                    .padding(horizontal = contentPadding, vertical = if (shortScreen) 6.dp else 12.dp)
            ) {
                PillButton(
                    text = stringResource(R.string.main_add_alarm),
                    onClick = { showDayPicker = true }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!shortScreen) {
                NextAlarmCard(
                    nextAlarm = nextAlarm,
                    now = now,
                    settings = settings,
                    modifier = Modifier.padding(start = contentPadding, end = contentPadding, top = 12.dp),
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
                        .padding(start = contentPadding, end = contentPadding, top = 8.dp, bottom = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = contentPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (shortScreen) {
                    item(key = "next-alarm") {
                        NextAlarmCard(
                            nextAlarm = nextAlarm,
                            now = now,
                            settings = settings,
                            modifier = Modifier.padding(horizontal = 0.dp),
                            onSkipNext = { nextAlarm?.let { viewModel.skipNextAlarm(it) } }
                        )
                    }
                    item(key = "today") {
                        Text(
                            text = todayText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }
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

