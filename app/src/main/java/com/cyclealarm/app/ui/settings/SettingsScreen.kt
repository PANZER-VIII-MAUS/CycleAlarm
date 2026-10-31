package com.cyclealarm.app.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.cyclealarm.app.data.BackupManager
import com.cyclealarm.app.core.ShiftTemplate
import com.cyclealarm.app.core.ShiftTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyclealarm.app.R
import com.cyclealarm.app.core.AppLanguage
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.ResyncCheckpoint
import com.cyclealarm.app.core.TemporaryMuteStore
import com.cyclealarm.app.core.RingPrefs
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.history.RingHistoryActivity
import com.cyclealarm.app.ui.formatEpochDay
import com.cyclealarm.app.ui.theme.ThemeManager
import com.cyclealarm.app.viewmodel.AlarmViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

private enum class SettingsDialog {
    CYCLE_MODE, CYCLE_LENGTH, ALIGN_DAY, THEME, TIME_FORMAT, DURATION, SNOOZE
}

/**
 * 设置页：循环 / 显示 / 响铃 / 系统时间 / 关于。
 * 除「周期天数」「今天对齐」需要点确定外，其余选择即时生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current
    val backupScope = rememberCoroutineScope()
    var showShiftTemplates by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    listOf(
                        AppLanguage.SYSTEM to R.string.settings_language_system,
                        AppLanguage.ZH to R.string.settings_language_zh,
                        AppLanguage.EN to R.string.settings_language_en
                    ).forEach { (code, label) ->
                        TextButton(
                            onClick = {
                                AppLanguage.set(context, code)
                                showLanguageDialog = false
                                (context as? android.app.Activity)?.recreate()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(label)) }
                    }
                    Text(
                        stringResource(R.string.settings_language_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showShiftTemplates) {
        ShiftTemplateDialog(
            onPick = { template ->
                viewModel.applyShiftTemplate(template)
                showShiftTemplates = false
                Toast.makeText(context, "已应用「${template.name}」", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showShiftTemplates = false }
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                runCatching {
                    val payload = BackupManager.export(context)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }.onSuccess {
                    Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                    BackupManager.import(context, text)
                }.onSuccess {
                    Toast.makeText(context, "已从备份恢复", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cycleLength by viewModel.cycleLength.collectAsStateWithLifecycle()
    val themeMode by ThemeManager.mode.collectAsStateWithLifecycle()

    var is24h by rememberSaveable { mutableStateOf(DisplayPrefs.is24Hour(context)) }
    var ringDuration by rememberSaveable { mutableStateOf(RingPrefs.durationSeconds(context)) }
    var snoozeMinutes by rememberSaveable { mutableStateOf(RingPrefs.snoozeMinutes(context)) }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLength by rememberSaveable { mutableStateOf<Int?>(null) }

    val cycleType = settings?.cycleType ?: CycleTypes.CUSTOM
    val isCustom = cycleType == CycleTypes.CUSTOM
    val todayIndex = remember(settings) {
        val s = settings ?: return@remember 1
        val len = s.cycleLength.coerceAtLeast(1)
        Math.floorMod(LocalDate.now().toEpochDay() - s.cycleStartEpochDay, len.toLong()).toInt() + 1
    }

    // 系统时间与版本：打开页面读一次
    val now = remember { LocalDateTime.now() }
    val tzName = remember { TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT) }
    val zoneId = remember { ZoneId.systemDefault().id }
    val autoTime = remember {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) == 1
    }
    val autoTimeZone = remember {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    fun requestMode(mode: String) {
        val target = when (mode) {
            CycleTypes.DAILY -> 1
            CycleTypes.WEEKLY -> 7
            else -> cycleLength
        }
        if (target < cycleLength) {
            pendingMode = mode
        } else {
            viewModel.applyCycleSettings(mode, cycleLength, todayIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "cycle") {
                CycleCard {
                    Text("循环", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingRow(
                        title = "循环方式",
                        value = cycleTypeLabel(cycleType),
                        onClick = { dialog = SettingsDialog.CYCLE_MODE }
                    )
                    if (isCustom) {
                        SettingRow(
                            title = "周期天数",
                            value = "$cycleLength 天",
                            onClick = { dialog = SettingsDialog.CYCLE_LENGTH }
                        )
                        SettingRow(
                            title = "今天对齐",
                            value = "今天 = 第 $todayIndex 天",
                            onClick = { dialog = SettingsDialog.ALIGN_DAY }
                        )
                    }
                }
            }

            item(key = "display") {
                CycleCard {
                    Text("显示", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingRow(
                        title = stringResource(R.string.settings_language),
                        value = when (AppLanguage.get(context)) {
                            AppLanguage.ZH -> stringResource(R.string.settings_language_zh)
                            AppLanguage.EN -> stringResource(R.string.settings_language_en)
                            else -> stringResource(R.string.settings_language_system)
                        },
                        onClick = { showLanguageDialog = true }
                    )
                    SettingRow(
                        title = "主题",
                        value = themeLabel(themeMode),
                        onClick = { dialog = SettingsDialog.THEME }
                    )
                    SettingRow(
                        title = "时间格式",
                        value = if (is24h) "24 小时制" else "12 小时制",
                        onClick = { dialog = SettingsDialog.TIME_FORMAT }
                    )
                }
            }

            item(key = "ring") {
                CycleCard {
                    Text("响铃", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingRow(
                        title = "响铃时长",
                        value = RingPrefs.durationLabel(ringDuration),
                        onClick = { dialog = SettingsDialog.DURATION }
                    )
                    SettingRow(
                        title = "稍后提醒间隔",
                        value = "$snoozeMinutes 分钟",
                        onClick = { dialog = SettingsDialog.SNOOZE }
                    )
                    SettingRow(
                        title = "轮班模板",
                        value = "白夜休休 / 做二休二 / 四班三运转",
                        onClick = { showShiftTemplates = true }
                    )
                    SettingRow(
                        title = "分享排班",
                        value = "把当前排班发出去",
                        onClick = {
                            backupScope.launch {
                                runCatching {
                                    val text = viewModel.buildShareText()
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(share, "分享排班")
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context, "生成分享文本失败", Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                    SettingRow(
                        title = "响铃历史",
                        value = "最近 7 天的响铃记录",
                        onClick = {
                            context.startActivity(
                                Intent(context, RingHistoryActivity::class.java)
                            )
                        }
                    )
                    SettingRow(
                        title = "今天临时静音",
                        value = if (
                            TemporaryMuteStore.mutedDays(context)
                                .contains(LocalDate.now().toEpochDay())
                        ) {
                            "已开启（明天恢复）"
                        } else {
                            "未开启"
                        },
                        onClick = {
                            val today = LocalDate.now().toEpochDay()
                            if (TemporaryMuteStore.mutedDays(context).contains(today)) {
                                viewModel.unmuteToday()
                                Toast.makeText(context, "已取消今天静音", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.muteToday()
                                Toast.makeText(context, "今天不再响铃", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SettingRow(
                        title = "导出备份",
                        value = "生成单文件备份",
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                                .format(Date())
                            exportLauncher.launch("CycleAlarm-备份-$stamp.txt")
                        }
                    )
                    SettingRow(
                        title = "导入备份",
                        value = "从备份文件恢复",
                        onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }
                    )
                    SettingRow(
                        title = "排期诊断",
                        value = ResyncCheckpoint.lastDiagnosticLabel(context),
                        onClick = {
                            Toast.makeText(
                                context,
                                "对账结果会记录在这里，正常时显示「正常」",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            item(key = "systemTime") {
                CycleCard {
                    Text("系统时间", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingStaticRow(
                        title = "当前时间",
                        value = "${formatEpochDay(now.toLocalDate().toEpochDay())} " +
                            DisplayPrefs.formatTime(context, now.hour, now.minute)
                    )
                    SettingStaticRow(title = "时区", value = "$tzName · $zoneId")
                    SettingStaticRow(
                        title = "自动设置时间",
                        value = if (autoTime) "开" else "关"
                    )
                    SettingStaticRow(
                        title = "自动设置时区",
                        value = if (autoTimeZone) "开" else "关"
                    )
                    Text(
                        "App 无法修改系统时间或时区。修改系统时区后，闹钟排期会自动重建，无需手动重排。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "请在系统设置里找到日期和时间",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) { Text("打开系统日期和时间设置") }
                }
            }

            item(key = "about") {
                CycleCard {
                    Text("关于", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingStaticRow(title = "版本", value = versionName)
                    Text(
                        "循环闹钟 · 个人自用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.CYCLE_MODE -> OptionDialog(
            title = "循环方式",
            currentLabel = cycleTypeLabel(cycleType),
            options = listOf(
                "每天" to { requestMode(CycleTypes.DAILY) },
                "每周" to { requestMode(CycleTypes.WEEKLY) },
                "自定义" to { requestMode(CycleTypes.CUSTOM) }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.CYCLE_LENGTH -> StepperDialog(
            title = "周期天数",
            initial = cycleLength,
            range = 1..30,
            format = { "$it 天" },
            onConfirm = { newLength ->
                if (newLength < cycleLength) {
                    pendingLength = newLength
                } else {
                    viewModel.applyCycleSettings(CycleTypes.CUSTOM, newLength, todayIndex)
                }
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.ALIGN_DAY -> StepperDialog(
            title = "今天对齐",
            initial = todayIndex,
            range = 1..cycleLength.coerceAtLeast(1),
            format = { "今天 = 第 $it 天" },
            onConfirm = { day ->
                viewModel.applyCycleSettings(CycleTypes.CUSTOM, cycleLength, day)
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.THEME -> OptionDialog(
            title = "主题",
            currentLabel = themeLabel(themeMode),
            options = listOf(
                "跟随系统" to { ThemeManager.set(context, ThemeManager.MODE_SYSTEM) },
                "浅色" to { ThemeManager.set(context, ThemeManager.MODE_LIGHT) },
                "深色" to { ThemeManager.set(context, ThemeManager.MODE_DARK) }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.TIME_FORMAT -> OptionDialog(
            title = "时间格式",
            currentLabel = if (is24h) "24 小时制" else "12 小时制",
            options = listOf(
                "24 小时制" to {
                    is24h = true
                    DisplayPrefs.set24Hour(context, true)
                },
                "12 小时制" to {
                    is24h = false
                    DisplayPrefs.set24Hour(context, false)
                }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.DURATION -> OptionDialog(
            title = "响铃时长",
            currentLabel = RingPrefs.durationLabel(ringDuration),
            options = RingPrefs.DURATION_OPTIONS.map { option ->
                RingPrefs.durationLabel(option) to {
                    ringDuration = option
                    RingPrefs.setDurationSeconds(context, option)
                }
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.SNOOZE -> OptionDialog(
            title = "稍后提醒间隔",
            currentLabel = "$snoozeMinutes 分钟",
            options = RingPrefs.SNOOZE_OPTIONS.map { option ->
                "$option 分钟" to {
                    snoozeMinutes = option
                    RingPrefs.setSnoozeMinutes(context, option)
                }
            },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }

    pendingMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text("确认") },
            text = { Text("切换循环方式会缩短周期天数，末尾天的闹钟会被删除，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyCycleSettings(mode, cycleLength, todayIndex)
                    pendingMode = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMode = null }) { Text("取消") }
            }
        )
    }

    pendingLength?.let { length ->
        AlertDialog(
            onDismissRequest = { pendingLength = null },
            title = { Text("确认") },
            text = { Text("缩短天数会删除第 $length 天之后的所有闹钟，确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyCycleSettings(CycleTypes.CUSTOM, length, todayIndex)
                    pendingLength = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLength = null }) { Text("取消") }
            }
        )
    }
}

// ---------------------------------------------------------------------
// 行组件
// ---------------------------------------------------------------------

@Composable
private fun ShiftTemplateDialog(
    onPick: (ShiftTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("轮班模板") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ShiftTemplates.ALL.forEach { template ->
                    TextButton(
                        onClick = { onPick(template) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(template.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    "应用模板会把循环方式设为自定义、并写入一个周期的闹钟；已有闹钟不会被删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingStaticRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------
// 选择 / 步进对话框
// ---------------------------------------------------------------------

@Composable
private fun OptionDialog(
    title: String,
    currentLabel: String?,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (label, action) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (label == currentLabel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                action()
                                onDismiss()
                            }
                            .semantics { role = Role.Button }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StepperDialog(
    title: String,
    initial: Int,
    range: IntRange,
    format: (Int) -> String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable {
        mutableStateOf(initial.coerceIn(range.first, range.last))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { value = (value - 1).coerceAtLeast(range.first) }
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "减少")
                }
                Text(
                    format(value),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = { value = (value + 1).coerceAtMost(range.last) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "增加")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(value)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------------
// 文案
// ---------------------------------------------------------------------

private fun cycleTypeLabel(type: String): String = when (type) {
    CycleTypes.DAILY -> "每天"
    CycleTypes.WEEKLY -> "每周"
    else -> "自定义"
}

private fun themeLabel(mode: String): String = when (mode) {
    ThemeManager.MODE_LIGHT -> "浅色"
    ThemeManager.MODE_DARK -> "深色"
    else -> "跟随系统"
}
