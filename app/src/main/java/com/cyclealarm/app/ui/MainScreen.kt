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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.cyclealarm.app.ui.components.PillButton
import com.cyclealarm.app.ui.import.ImportActivity
import com.cyclealarm.app.ui.settings.SettingsActivity
import com.cyclealarm.app.ui.plan.PlanPickerDialog
import com.cyclealarm.app.ui.plan.PlanTimeDialog
import com.cyclealarm.app.ui.plan.PlanTypeManagerDialog
import com.cyclealarm.app.ui.theme.LocalExtraColors
import com.cyclealarm.app.ui.theme.DarkBackground
import com.cyclealarm.app.viewmodel.AlarmViewModel
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 按循环方式生成某天的显示名：每天 / 周一..周日 / 第 X 天。 */
private fun dayLabel(settings: AppSettingsEntity?, dayIndex: Int): String = when (settings?.cycleType) {
    CycleTypes.DAILY -> "每天"
    CycleTypes.WEEKLY -> weekLabels[dayIndex % weekLabels.size]
    else -> "第 ${dayIndex + 1} 天"
}

@Composable
private fun NextAlarmCard(
    nextAlarm: NextAlarm?,
    now: Long,
    settings: AppSettingsEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    CycleCard(modifier = modifier) {
        if (nextAlarm == null || settings == null) {
            Text(
                text = "暂无开启的闹钟",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "去下面打开任意一天",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DisplayPrefs.formatClock(context, nextAlarm.hour, nextAlarm.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = LocalExtraColors.current.navy
                    )
                    Text(
                        text = NextAlarmDisplay.countdownText(nextAlarm.triggerAt, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = nextAlarmSourceLabel(nextAlarm, settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun nextAlarmSourceLabel(
    alarm: NextAlarm,
    settings: AppSettingsEntity
): String = when (alarm.kind) {
    NextAlarmKind.CYCLE -> {
        val triggerDay = Instant.ofEpochMilli(alarm.triggerAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        "第 ${cycleDayOf(settings, triggerDay)} 天"
    }

    NextAlarmKind.DATE -> "按日期"
    NextAlarmKind.SNOOZE -> "稍后提醒"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AlarmViewModel = viewModel()) {
    val alarms by viewModel.alarms.collectAsState()
    val dateAlarms by viewModel.dateAlarms.collectAsState()
    val snoozes by viewModel.snoozes.collectAsState()
    val activityTypes by viewModel.activityTypes.collectAsState()
    val dayPlans by viewModel.dayPlans.collectAsState()
    val holidays by viewModel.holidays.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cycleLength by viewModel.cycleLength.collectAsState()
    val databaseError by viewModel.databaseError.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(60_000)
            refreshTick++
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val prefs = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    var showPermissionGuide by remember {
        mutableStateOf(!prefs.getBoolean("onboarding_done", false))
    }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var editingHoliday by remember { mutableStateOf<HolidayEntity?>(null) }
    var planPickerDay by remember { mutableStateOf<Int?>(null) }
    var planTimeDay by remember { mutableStateOf<Int?>(null) }
    var deletePlanDay by remember { mutableStateOf<Int?>(null) }
    var showTypeManager by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }

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

    val todayText = remember(settings) {
        val s = settings
        if (s == null) {
            "今天是第 1 天"
        } else {
            val len = s.cycleLength.coerceAtLeast(1)
            val day = Math.floorMod(LocalDate.now().toEpochDay() - s.cycleStartEpochDay, len.toLong())
                .toInt() + 1
            if (s.cycleType == CycleTypes.WEEKLY) {
                "今天是 ${weekLabels[(day - 1) % weekLabels.size]}"
            } else {
                "今天是第 $day 天"
            }
        }
    }
    val todayIndex = remember(settings) {
        val s = settings ?: return@remember 0
        val len = s.cycleLength.coerceAtLeast(1)
        Math.floorMod(LocalDate.now().toEpochDay() - s.cycleStartEpochDay, len.toLong()).toInt()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("循环闹钟", style = MaterialTheme.typography.titleLarge)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { showTypeManager = true }) {
                        Icon(Icons.Outlined.Schedule, contentDescription = "安排类型")
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, ImportActivity::class.java)) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "从课表导入")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "周期设置")
                    }
                    IconButton(onClick = { showPermissionGuide = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "权限设置")
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
                    text = "添加闹钟",
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
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
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
                        onDeleteHoliday = { viewModel.deleteHoliday(it) }
                    )
                }

                item(key = "cycle") {
                    CycleInfoCard(
                        settings = settings,
                        cycleLength = cycleLength,
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
                        onDelete = { viewModel.deleteDateAlarm(it) }
                    )
                }

                items(count = cycleLength, key = { index -> "day$index" }) { dayIndex ->
                    val plan = plansByDay[dayIndex]
                    val type = plan?.let { typesById[it.typeId] }
                    val manualAlarms = alarms
                        .filter { !it.planGenerated && it.dayIndex == dayIndex }
                        .sortedBy { it.hour * 60 + it.minute }
                    DayPlanCard(
                        dayText = dayLabel(settings, dayIndex),
                        isToday = dayIndex == todayIndex,
                        plan = plan,
                        planType = type,
                        manualAlarms = manualAlarms,
                        onOpenPlan = {
                            // 两类点击分流：无安排 → 选安排；有安排 → 改这一天的安排时间
                            if (plan == null) planPickerDay = dayIndex else planTimeDay = dayIndex
                        },
                        onTogglePlan = { enabled -> viewModel.setPlanEnabled(dayIndex, enabled) },
                        onAddAlarm = { viewModel.addAlarmToDay(dayIndex) },
                        onEditManual = { editingAlarm = it },
                        onToggleManual = { alarm, enabled -> viewModel.setEnabled(alarm, enabled) },
                        onDeleteManual = { viewModel.deleteAlarm(it.id) }
                    )
                }

                item(key = "tip") {
                    Text(
                        text = "提示：循环天数固定后，只能在对应的第 X 天里添加闹钟；" +
                            "同一天可加多个闹钟；右上角设置可修改循环方式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
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
            title = { Text("无法打开数据库") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearDatabaseError) { Text("知道了") }
            }
        )
    }

    editingAlarm?.let { alarm ->
        EditAlarmDialog(
            alarm = alarm,
            dayLabel = dayLabel(settings, alarm.dayIndex),
            onDismiss = { editingAlarm = null },
            onSave = { saved ->
                viewModel.saveAlarm(saved)
                editingAlarm = null
            },
            onDelete = {
                viewModel.deleteAlarm(alarm.id)
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
            dayText = dayLabel(settings, day),
            types = activityTypes,
            currentPlan = plansByDay[day],
            onSelect = { type ->
                viewModel.setDayPlan(day, type.id)
                planPickerDay = null
            },
            onClear = {
                viewModel.clearDayPlan(day)
                planPickerDay = null
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
                title = "${dayLabel(settings, day)} 的安排时间",
                hour = plan.hour,
                minute = plan.minute,
                onSave = { hour, minute ->
                    viewModel.updatePlanTime(day, hour, minute)
                    planTimeDay = null
                },
                onDeletePlan = {
                    viewModel.clearDayPlan(day)
                    planTimeDay = null
                },
                onDismiss = { planTimeDay = null }
            )
        }
    }

    deletePlanDay?.let { day ->
        AlertDialog(
            onDismissRequest = { deletePlanDay = null },
            title = { Text("删除安排闹钟") },
            text = { Text("删除后这一天会变为「未安排」。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDayPlan(day)
                    deletePlanDay = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { deletePlanDay = null }) { Text("取消") }
            }
        )
    }

    if (showDayPicker) {
        AlertDialog(
            onDismissRequest = { showDayPicker = false },
            title = { Text("添加闹钟") },
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
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dayLabel(settings, index),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (index == todayIndex) {
                                Text(
                                    "今天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDayPicker = false }) { Text("取消") }
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
            onDelete = { viewModel.deleteActivityType(it) },
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
    onDeleteHoliday: (Int) -> Unit
) {
    CycleCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("假期模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开启后假期内的闹钟自动静默；关闭时会一并关闭所有闹钟的假期静音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = holidayModeEnabled, onCheckedChange = onToggleMode)
            }

            if (holidays.isEmpty()) {
                Text(
                    "还没有假期段。可一键导入法定节假日，或手动添加自定义区间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                holidays.forEach { h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (holidayModeEnabled) 1f else 0.45f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onEdit(h) }
                        ) {
                            Text(
                                h.label.ifBlank { "自定义假期" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${formatEpochDay(h.startEpochDay)} ~ ${formatEpochDay(h.endEpochDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = { onEdit(h) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑这段假期")
                        }
                        IconButton(onClick = { onDeleteHoliday(h.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除这段假期")
                        }
                        Switch(
                            checked = h.enabled,
                            onCheckedChange = { onToggleHoliday(h, it) }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportChina,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入法定节假日")
                }
                OutlinedButton(
                    onClick = onAddCustom,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("＋自定义假期")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSetAllMuted(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("全部开启假期静音")
                }
                OutlinedButton(
                    onClick = { onSetAllMuted(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("全部关闭假期静音")
                }
            }
            Text(
                "对下方所有闹钟生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "内置 2025/2026 官方数据，导入时自动跳过已过去的日期；总开关关闭时整段假期都不生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun HolidayEditDialog(
    holiday: HolidayEntity,
    onDismiss: () -> Unit,
    onSave: (label: String, startEpochDay: Long, endEpochDay: Long) -> Unit
) {
    var label by remember(holiday.id) { mutableStateOf(holiday.label) }
    var start by remember(holiday.id) { mutableStateOf(holiday.startEpochDay) }
    var end by remember(holiday.id) { mutableStateOf(holiday.endEpochDay) }
    var pickStart by remember(holiday.id) { mutableStateOf(false) }
    var pickEnd by remember(holiday.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (holiday.id == 0) "添加假期" else "编辑假期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称（如：国庆 2026）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { pickStart = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始 ${formatEpochDay(start)}")
                }
                OutlinedButton(
                    onClick = { pickEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("结束 ${formatEpochDay(end)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(label.trim().ifBlank { "自定义假期" }, start, end)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (pickStart) {
        HolidayDateDialog(
            title = "选择开始日期",
            initialEpochDay = start,
            onConfirm = { start = it },
            onDismiss = { pickStart = false }
        )
    }
    if (pickEnd) {
        HolidayDateDialog(
            title = "选择结束日期",
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
    onSettingsClick: () -> Unit
) {
    val todayEpoch = LocalDate.now().toEpochDay()
    val todayDay = settings?.let {
        Math.floorMod(todayEpoch - it.cycleStartEpochDay, it.cycleLength.toLong()) + 1
    } ?: 1
    val subtitle = when (settings?.cycleType) {
        CycleTypes.DAILY -> "每天重复 · 点击调整循环设置"
        CycleTypes.WEEKLY -> "每周重复 · 今天是${weekLabels[((todayDay - 1) % weekLabels.size).toInt()]} · 点击调整"
        else -> "$cycleLength 天一轮 · 今天是第 $todayDay 天 · 点击调整"
    }
    CycleCard(onClick = onSettingsClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("循环周期", style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            TextButton(onClick = onSettingsClick) { Text("设置") }
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
        "未安排"
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
        plan == null || planType == null -> "点此设置"
        !planType.rings -> "休息"
        !plan.enabled -> "已暂停"
        else -> planType.name
    }
    // 12 小时制时，把「上午/下午」挪到副标题前缀，避免大行被撑宽
    val amPm = if (plan != null && showTime) DisplayPrefs.amPmLabel(context, plan.hour) else null
    val subtitle = if (amPm != null) "$amPm · $baseSubtitle" else baseSubtitle
    // 开关语义 = 今天会不会响：休息/未安排置灰关闭；启用=开（可关掉暂停）；已暂停=关（可点开恢复）
    val switchOn = ringsToday && plan?.enabled == true
    val switchEnabled = ringsToday
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

    CycleCard(modifier = cardModifier, onClick = onOpenPlan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddAlarm) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加闹钟",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = switchOn,
                onCheckedChange = onTogglePlan,
                enabled = switchEnabled,
                colors = switchColors
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
        ) {
            Text(
                "${DisplayPrefs.formatTime(context, alarm.hour, alarm.minute)} · " +
                    alarm.label.ifBlank { "闹钟" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除这条闹钟",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle, colors = switchColors)
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
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var hour by remember(alarm.id) { mutableStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableStateOf(alarm.minute) }
    var enabled by remember(alarm.id) { mutableStateOf(alarm.enabled) }
    var muted by remember(alarm.id) { mutableStateOf(alarm.mutedDuringHoliday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dayLabel 的闹钟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
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
                    Text("时间：${DisplayPrefs.formatTime(context, hour, minute)}")
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
                                label.trim().ifBlank { "闹钟" } + "（试听）",
                                snoozed = false
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("试听闹钟（立即响铃）")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "这条闹钟是否生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("假期静音", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "假期模式开启时这条不响（取消勾选则假期照常响）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = muted, onCheckedChange = { muted = it })
                }
                if (alarm.planGenerated) {
                    Text(
                        "修改后不再随安排自动同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
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
            ) { Text("保存") }
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
                    ) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
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
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state, title = { Text(title) })
    }
}
