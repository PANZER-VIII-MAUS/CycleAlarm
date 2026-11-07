package com.cyclealarm.app.ui.settings

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.res.pluralStringResource
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
import com.cyclealarm.app.core.uiMessage
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
import com.cyclealarm.app.core.GuardServicePolicy
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
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showShiftTemplates) {
        ShiftTemplateDialog(
            onPick = { template ->
                viewModel.applyShiftTemplate(template)
                showShiftTemplates = false
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_template_applied, context.getString(template.nameRes)),
                    Toast.LENGTH_SHORT
                ).show()
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
                    // 导出要读整库并编码，不能放在主线程
                    val payload = withContext(Dispatchers.IO) { BackupManager.export(context) }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error(context.getString(R.string.error_write_file))
                }.onSuccess {
                    Toast.makeText(context, context.getString(R.string.toast_backup_exported), Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_export_failed, it.uiMessage(context)),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    // 选完文件先不导入：读出内容后弹二次确认，确认后才整库替换
    var pendingImportPayload by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                } ?: error(context.getString(R.string.error_read_file))
                if (bytes.size > MAX_BACKUP_BYTES) {
                    throw com.cyclealarm.app.core.LocalizedFailure(R.string.err_backup_too_large)
                }
                bytes.toString(Charsets.UTF_8)
            }.onSuccess { text ->
                pendingImportPayload = text
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_import_failed, it.uiMessage(context)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 自动备份恢复入口：数据只在私有目录，卸载/清数据后靠这里救回来
    var autoBackups by remember { mutableStateOf<List<com.cyclealarm.app.data.BackupManager.AutoBackupInfo>>(emptyList()) }
    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var pendingAutoRestore by remember { mutableStateOf<com.cyclealarm.app.data.BackupManager.AutoBackupInfo?>(null) }

    pendingAutoRestore?.let { info ->
        AlertDialog(
            onDismissRequest = { pendingAutoRestore = null },
            title = { Text(stringResource(R.string.restore_auto_confirm_title)) },
            text = { Text(stringResource(R.string.restore_auto_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAutoRestore = null
                        showAutoBackupDialog = false
                        backupScope.launch {
                            runCatching {
                                val payload = withContext(Dispatchers.IO) {
                                    com.cyclealarm.app.data.BackupManager.readAutoBackup(info)
                                }
                                withContext(Dispatchers.IO) { BackupManager.import(context, payload) }
                            }.onSuccess {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_restore_auto_ok),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_import_failed, it.uiMessage(context)),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text(stringResource(R.string.backup_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAutoRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showAutoBackupDialog) {
        AlertDialog(
            onDismissRequest = { showAutoBackupDialog = false },
            title = { Text(stringResource(R.string.row_restore_auto_backup)) },
            text = {
                if (autoBackups.isEmpty()) {
                    Text(stringResource(R.string.restore_auto_empty))
                } else {
                    androidx.compose.foundation.layout.Column {
                        autoBackups.forEach { info ->
                            val stamp = java.text.SimpleDateFormat(
                                "MM-dd HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(info.savedAt))
                            TextButton(
                                onClick = { pendingAutoRestore = info }
                            ) {
                                Text(
                                    context.getString(
                                        R.string.restore_auto_item,
                                        stamp,
                                        com.cyclealarm.app.data.AutoBackupPolicy.formatSize(info.sizeBytes)
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoBackupDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    pendingImportPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImportPayload = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                if (com.cyclealarm.app.data.BackupCodec.isLegacyUnverified(payload)) {
                    Text(stringResource(R.string.backup_confirm_text_legacy))
                } else {
                    Text(stringResource(R.string.backup_confirm_text))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportPayload = null
                        backupScope.launch {
                            runCatching { withContext(Dispatchers.IO) { BackupManager.import(context, payload) } }
                                .onSuccess { summary ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_backup_restored),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    summary.autoBackupPath?.let { path ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_backup_auto_saved, path),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_import_failed, it.uiMessage(context)),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                ) { Text(stringResource(R.string.backup_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportPayload = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            // 宽屏（平板/横屏）限制内容宽度并居中：六轮复审 §5
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .widthIn(max = 640.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "cycle") {
                CycleCard {
                    Text(stringResource(R.string.section_cycle), style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingRow(
                        title = stringResource(R.string.row_cycle_mode),
                        value = cycleTypeLabel(cycleType),
                        onClick = { dialog = SettingsDialog.CYCLE_MODE }
                    )
                    if (isCustom) {
                        SettingRow(
                            title = stringResource(R.string.row_cycle_length),
                            value = pluralStringResource(R.plurals.format_days, cycleLength, cycleLength),
                            onClick = { dialog = SettingsDialog.CYCLE_LENGTH }
                        )
                        SettingRow(
                            title = stringResource(R.string.row_align_today),
                            value = stringResource(R.string.format_today_is_day, todayIndex),
                            onClick = { dialog = SettingsDialog.ALIGN_DAY }
                        )
                    }
                }
            }

            item(key = "display") {
                CycleCard {
                    Text(stringResource(R.string.section_display), style = MaterialTheme.typography.titleSmall)
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
                        title = stringResource(R.string.row_theme),
                        value = themeLabel(themeMode),
                        onClick = { dialog = SettingsDialog.THEME }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_time_format),
                        value = if (is24h) stringResource(R.string.opt_24h) else stringResource(R.string.opt_12h),
                        onClick = { dialog = SettingsDialog.TIME_FORMAT }
                    )
                }
            }

            item(key = "ring") {
                CycleCard {
                    Text(stringResource(R.string.section_ring), style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingRow(
                        title = stringResource(R.string.row_ring_duration),
                        value = RingPrefs.durationLabel(context, ringDuration),
                        onClick = { dialog = SettingsDialog.DURATION }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_snooze_interval),
                        value = stringResource(R.string.format_minutes, snoozeMinutes),
                        onClick = { dialog = SettingsDialog.SNOOZE }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_shift_templates),
                        value = stringResource(R.string.shift_templates_summary),
                        onClick = { showShiftTemplates = true }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_share_schedule),
                        value = stringResource(R.string.share_schedule_value),
                        onClick = {
                            backupScope.launch {
                                runCatching {
                                    val text = viewModel.buildShareText()
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(share, context.getString(R.string.share_schedule_title))
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context, context.getString(R.string.toast_share_failed), Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_guard_service),
                        value = if (GuardServicePolicy.isEnabled(context)) {
                            stringResource(R.string.guard_on)
                        } else {
                            stringResource(R.string.guard_off)
                        },
                        onClick = {
                            val enabled = !GuardServicePolicy.isEnabled(context)
                            GuardServicePolicy.setEnabled(context, enabled)
                            val intent = Intent(context, com.cyclealarm.app.service.AlarmGuardService::class.java)
                            if (enabled) {
                                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                            } else {
                                context.stopService(intent)
                            }
                            Toast.makeText(
                                context,
                                if (enabled) {
                                    context.getString(R.string.toast_guard_on)
                                } else {
                                    context.getString(R.string.toast_guard_off)
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                            (context as? android.app.Activity)?.recreate()
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_ring_history),
                        value = stringResource(R.string.ring_history_value),
                        onClick = {
                            context.startActivity(
                                Intent(context, RingHistoryActivity::class.java)
                            )
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_mute_today),
                        value = if (
                            TemporaryMuteStore.mutedDays(context)
                                .contains(LocalDate.now().toEpochDay())
                        ) {
                            stringResource(R.string.mute_on)
                        } else {
                            stringResource(R.string.mute_off)
                        },
                        onClick = {
                            val today = LocalDate.now().toEpochDay()
                            if (TemporaryMuteStore.mutedDays(context).contains(today)) {
                                viewModel.unmuteToday()
                                Toast.makeText(context, context.getString(R.string.toast_mute_off), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.muteToday()
                                Toast.makeText(context, context.getString(R.string.toast_mute_on), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    var downloadCopyOn by remember {
                        mutableStateOf(com.cyclealarm.app.core.BackupPrefs.downloadCopyEnabled(context))
                    }
                    SettingRow(
                        title = stringResource(R.string.row_backup_to_downloads),
                        value = stringResource(
                            if (downloadCopyOn) R.string.backup_to_downloads_on
                            else R.string.backup_to_downloads_off
                        ),
                        onClick = {
                            val next = !downloadCopyOn
                            com.cyclealarm.app.core.BackupPrefs.setDownloadCopyEnabled(context, next)
                            downloadCopyOn = next
                            if (next) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.backup_to_downloads_toast),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_export_backup),
                        value = stringResource(R.string.export_backup_value),
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                                .format(Date())
                            exportLauncher.launch(
                                context.getString(R.string.backup_file_name, stamp)
                            )
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_import_backup),
                        value = stringResource(R.string.import_backup_value),
                        onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_restore_auto_backup),
                        value = stringResource(R.string.restore_auto_row_value),
                        onClick = {
                            autoBackups = runCatching {
                                com.cyclealarm.app.data.BackupManager.listAutoBackups(context)
                            }.getOrDefault(emptyList())
                            showAutoBackupDialog = true
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_ring_diagnosis),
                        value = ringDiagnosisLabel(context),
                        onClick = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.ring_diag_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    SettingRow(
                        title = stringResource(R.string.row_schedule_diagnosis),
                        value = ResyncCheckpoint.lastDiagnosticLabel(context),
                        onClick = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.diagnosis_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            item(key = "systemTime") {
                CycleCard {
                    Text(stringResource(R.string.section_system_time), style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingStaticRow(
                        title = stringResource(R.string.row_current_time),
                        value = "${formatEpochDay(context, now.toLocalDate().toEpochDay())} " +
                            DisplayPrefs.formatTime(context, now.hour, now.minute)
                    )
                    SettingStaticRow(title = stringResource(R.string.row_timezone), value = "$tzName · $zoneId")
                    SettingStaticRow(
                        title = stringResource(R.string.row_auto_time),
                        value = if (autoTime) stringResource(R.string.state_on) else stringResource(R.string.state_off)
                    )
                    SettingStaticRow(
                        title = stringResource(R.string.row_auto_timezone),
                        value = if (autoTimeZone) stringResource(R.string.state_on) else stringResource(R.string.state_off)
                    )
                    Text(
                        stringResource(R.string.system_time_hint),
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
                                    context.getString(R.string.toast_find_date_settings),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) { Text(stringResource(R.string.action_open_date_settings)) }
                }
            }

            item(key = "about") {
                CycleCard {
                    Text(stringResource(R.string.section_about), style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    SettingStaticRow(title = stringResource(R.string.row_version), value = versionName)
                    Text(
                        stringResource(R.string.about_text),
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
            title = stringResource(R.string.row_cycle_mode),
            currentLabel = cycleTypeLabel(cycleType),
            options = listOf(
                stringResource(R.string.cycle_daily) to { requestMode(CycleTypes.DAILY) },
                stringResource(R.string.cycle_weekly) to { requestMode(CycleTypes.WEEKLY) },
                stringResource(R.string.cycle_custom) to { requestMode(CycleTypes.CUSTOM) }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.CYCLE_LENGTH -> StepperDialog(
            title = stringResource(R.string.row_cycle_length),
            initial = cycleLength,
            range = 1..30,
            format = { context.resources.getQuantityString(R.plurals.format_days, it, it) },
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
            title = stringResource(R.string.row_align_today),
            initial = todayIndex,
            range = 1..cycleLength.coerceAtLeast(1),
            format = { context.getString(R.string.format_today_is_day, it) },
            onConfirm = { day ->
                viewModel.applyCycleSettings(CycleTypes.CUSTOM, cycleLength, day)
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.THEME -> OptionDialog(
            title = stringResource(R.string.row_theme),
            currentLabel = themeLabel(themeMode),
            options = listOf(
                stringResource(R.string.theme_system) to { ThemeManager.set(context, ThemeManager.MODE_SYSTEM) },
                stringResource(R.string.theme_light) to { ThemeManager.set(context, ThemeManager.MODE_LIGHT) },
                stringResource(R.string.theme_dark) to { ThemeManager.set(context, ThemeManager.MODE_DARK) }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.TIME_FORMAT -> OptionDialog(
            title = stringResource(R.string.row_time_format),
            currentLabel = if (is24h) stringResource(R.string.opt_24h) else stringResource(R.string.opt_12h),
            options = listOf(
                stringResource(R.string.opt_24h) to {
                    is24h = true
                    DisplayPrefs.set24Hour(context, true)
                },
                stringResource(R.string.opt_12h) to {
                    is24h = false
                    DisplayPrefs.set24Hour(context, false)
                }
            ),
            onDismiss = { dialog = null }
        )

        SettingsDialog.DURATION -> OptionDialog(
            title = stringResource(R.string.row_ring_duration),
            currentLabel = RingPrefs.durationLabel(context, ringDuration),
            options = RingPrefs.DURATION_OPTIONS.map { option ->
                RingPrefs.durationLabel(context, option) to {
                    ringDuration = option
                    RingPrefs.setDurationSeconds(context, option)
                }
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.SNOOZE -> OptionDialog(
            title = stringResource(R.string.row_snooze_interval),
            currentLabel = stringResource(R.string.format_minutes, snoozeMinutes),
            options = RingPrefs.SNOOZE_OPTIONS.map { option ->
                context.getString(R.string.format_minutes, option) to {
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
            title = { Text(stringResource(R.string.dialog_confirm_title)) },
            text = { Text(stringResource(R.string.confirm_cycle_mode)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyCycleSettings(mode, cycleLength, todayIndex)
                    pendingMode = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMode = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingLength?.let { length ->
        AlertDialog(
            onDismissRequest = { pendingLength = null },
            title = { Text(stringResource(R.string.dialog_confirm_title)) },
            text = { Text(context.getString(R.string.confirm_shrink_length, length)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyCycleSettings(CycleTypes.CUSTOM, length, todayIndex)
                    pendingLength = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLength = null }) { Text(stringResource(R.string.action_cancel)) }
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
        title = { Text(stringResource(R.string.row_shift_templates)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ShiftTemplates.ALL.forEach { template ->
                    TextButton(
                        onClick = { onPick(template) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(template.nameRes), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(template.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.shift_templates_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.cd_decrease))
                }
                Text(
                    format(value),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick = { value = (value + 1).coerceAtMost(range.last) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(value)
                onDismiss()
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// ---------------------------------------------------------------------
// 文案
// ---------------------------------------------------------------------

@Composable
private fun cycleTypeLabel(type: String): String = when (type) {
    CycleTypes.DAILY -> stringResource(R.string.cycle_daily)
    CycleTypes.WEEKLY -> stringResource(R.string.cycle_weekly)
    else -> stringResource(R.string.cycle_custom)
}

@Composable
private fun themeLabel(mode: String): String = when (mode) {
    ThemeManager.MODE_LIGHT -> stringResource(R.string.theme_light)
    ThemeManager.MODE_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

/** 备份文件大小上限（超过视为损坏）。 */
private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024

/**
 * 「上次响铃」诊断文案：记录的是响铃界面实际是怎么显示出来的
 * （标准全屏 / 悬浮窗兜底 / 只有通知）——ColorOS 屏蔽应用日志，只能靠它排障。
 */
private fun ringDiagnosisLabel(context: android.content.Context): String {
    val outcome = com.cyclealarm.app.core.RingDiagnostics.lastOutcome(context)
        ?: return context.getString(com.cyclealarm.app.R.string.ring_diag_unknown)
    val text = when (outcome) {
        com.cyclealarm.app.core.RingDiagnostics.Outcome.ACTIVITY_SHOWN ->
            context.getString(com.cyclealarm.app.R.string.ring_diag_activity)
        com.cyclealarm.app.core.RingDiagnostics.Outcome.OVERLAY_FALLBACK ->
            context.getString(com.cyclealarm.app.R.string.ring_diag_overlay)
        com.cyclealarm.app.core.RingDiagnostics.Outcome.NOTIFICATION_ONLY ->
            context.getString(com.cyclealarm.app.R.string.ring_diag_notification)
    }
    val at = com.cyclealarm.app.core.RingDiagnostics.lastAt(context)
    if (at <= 0L) return text
    val stamp = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(at))
    return "$text · $stamp"
}

