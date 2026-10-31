package com.cyclealarm.app.ui.import

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.importer.DetectedLayout
import com.cyclealarm.app.importer.ImportOptions
import com.cyclealarm.app.importer.ImportWarning
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import com.cyclealarm.app.importer.SectionTimeTable
import com.cyclealarm.app.data.ImportWeeklyPolicy
import com.cyclealarm.app.ui.import.components.PreviewTable
import com.cyclealarm.app.ui.theme.timePickerContext
import com.cyclealarm.app.viewmodel.ImportUiState
import com.cyclealarm.app.viewmodel.ImportViewModel
import java.time.LocalTime

private val MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/vnd.ms-excel"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel = viewModel(),
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val alarmCount by viewModel.alarmCount.collectAsStateWithLifecycle()
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }
    BackHandler { viewModel.closeImport(onClose) }

    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showOverwrite by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state is ImportUiState.Preview) {
        if (state is ImportUiState.Preview) viewModel.refreshAlarmCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从课表导入") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeImport(onClose) }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ImportUiState.Idle -> IdleContent(
                    onPick = { launcher.launch(MIME_TYPES) },
                    undoAvailable = undoAvailable,
                    onUndo = viewModel::undo
                )

                ImportUiState.Reading -> LoadingContent("正在读取文件…")
                ImportUiState.Parsing -> LoadingContent("正在解析表格…")
                ImportUiState.Writing -> LoadingContent("正在写入闹钟…")

                is ImportUiState.SheetPick -> SheetPickContent(
                    names = current.sheetNames,
                    onPick = viewModel::selectSheet
                )

                is ImportUiState.Preview -> PreviewContent(
                    state = current,
                    onToggle = viewModel::toggleSelected,
                    onToggleAll = viewModel::setAllSelected,
                    onEdit = viewModel::updateEntry,
                    onDelete = viewModel::deleteEntry,
                    onOptions = { showOptions = true },
                    onManual = { showManual = true },
                    onConfirm = {
                        if (current.options.overwrite) showOverwrite = true else viewModel.write(weeklyConfirmed = true)
                    },
                    onCancelWeekly = viewModel::reset
                )

                is ImportUiState.Done -> DoneContent(
                    state = current,
                    undoAvailable = undoAvailable,
                    onUndo = viewModel::undo,
                    onClose = { viewModel.closeImport(onClose) }
                )

                is ImportUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = viewModel::reset,
                    onClose = { viewModel.closeImport(onClose) }
                )
            }
        }
    }

    if (showOptions) {
        (state as? ImportUiState.Preview)?.let { preview ->
            OptionsDialog(
                options = preview.options,
                onDismiss = { showOptions = false },
                onApply = { lead, fallback, overwrite, sections ->
                    viewModel.updateOptions(lead, fallback, overwrite, sections)
                    showOptions = false
                }
            )
        }
    }

    if (showManual) {
        ManualMappingDialog(
            onDismiss = { showManual = false },
            onApply = { layout ->
                viewModel.applyManualLayout(layout)
                showManual = false
            }
        )
    }

    if (showOverwrite) {
        AlertDialog(
            onDismissRequest = { showOverwrite = false },
            title = { Text("覆盖导入") },
            text = { Text("将删除现有 $alarmCount 条闹钟，确定覆盖导入？") },
            confirmButton = {
                TextButton(onClick = {
                    showOverwrite = false
                    viewModel.write()
                }) { Text("确定覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwrite = false }) { Text("取消") }
            }
        )
    }
}

// ---------------------------------------------------------------------
// 各状态内容
// ---------------------------------------------------------------------

@Composable
private fun IdleContent(
    onPick: () -> Unit,
    undoAvailable: Boolean,
    onUndo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("选择课表 / 排班表", style = MaterialTheme.typography.titleMedium)
        Text(
            "支持 Excel(.xlsx) 与 CSV。周课表会切换为「每周」循环；带具体日期的排班表会生成为一次性闹钟。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text("选择 Excel / CSV 文件")
        }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("图片识别暂不支持")
        }
        if (undoAvailable) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text("撤销上次导入（24 小时内）")
            }
        }
        Text(
            "先用 Excel / CSV 导入，或用底部「添加闹钟」手动加",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SheetPickContent(names: List<String>, onPick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("请选择要导入的工作表", style = MaterialTheme.typography.titleMedium) }
        items(names) { name ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(name) }
                    .semantics { role = Role.Button }
            ) {
                Text(name, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun PreviewContent(
    state: ImportUiState.Preview,
    onToggle: (Int) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onEdit: (Int, ImportedEntry) -> Unit,
    onDelete: (Int) -> Unit,
    onOptions: () -> Unit,
    onManual: () -> Unit,
    onConfirm: () -> Unit,
    onCancelWeekly: () -> Unit
) {
    val needsConfirm = state.warnings.size
    val selectedEntries = state.entries.filterIndexed { index, _ -> index in state.selected }
    val requiresWeeklySwitch = ImportWeeklyPolicy.requiresWeeklySwitch(selectedEntries)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "识别到 ${state.entries.size} 条，${state.selected.size} 条已选中" +
                if (needsConfirm > 0) "，${needsConfirm} 条需确认" else "",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "工作表：${state.sheetName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (requiresWeeklySwitch) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "该文件按周组织，导入将把循环方式改为每周（周一至周日）；第 8 天以后的安排会保留但不生效",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancelWeekly) { Text("取消") }
                        TextButton(onClick = onConfirm) { Text("仍然导入") }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { onToggleAll(true) }) { Text("全选") }
            TextButton(onClick = { onToggleAll(false) }) { Text("全不选") }
            if (state.needsManualMapping) {
                TextButton(onClick = onManual) { Text("手动指定结构") }
            }
        }

        if (state.entries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("没有识别到可导入的闹钟", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "可以手动指定表头行、星期列与时间列后重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                        Text("手动指定表格结构")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                PreviewTable(
                    entries = state.entries,
                    selected = state.selected,
                    onToggle = onToggle,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }

        WarningsCard(state.warnings)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onOptions, modifier = Modifier.weight(1f)) {
                Text("选项")
            }
            Button(
                onClick = onConfirm,
                enabled = state.selected.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (requiresWeeklySwitch) {
                        "仍然导入并切换每周（${state.selected.size}）"
                    } else {
                        "确认导入（${state.selected.size}）"
                    }
                )
            }
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<ImportWarning>) {
    if (warnings.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("需确认 ${warnings.size} 条", style = MaterialTheme.typography.titleSmall)
            warnings.take(6).forEach { warning ->
                Text(
                    "· ${warning.message}" + if (warning.sourceRef.isNotBlank()) "（${warning.sourceRef}）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (warnings.size > 6) {
                Text(
                    "…… 还有 ${warnings.size - 6} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DoneContent(
    state: ImportUiState.Done,
    undoAvailable: Boolean,
    onUndo: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("已导入 ${state.cycleCount + state.dateCount} 条闹钟", style = MaterialTheme.typography.titleMedium)
        Text(
            "周期闹钟 ${state.cycleCount} 条，按日期闹钟 ${state.dateCount} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.switchedToWeekly) {
            Text(
                "循环方式已切换为每周（周一至周日）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (undoAvailable) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text("撤销本次导入（24 小时内）")
            }
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("导入失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重新选择文件") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("返回主界面") }
    }
}

// ---------------------------------------------------------------------
// 选项对话框
// ---------------------------------------------------------------------

@Composable
private fun OptionsDialog(
    options: ImportOptions,
    onDismiss: () -> Unit,
    onApply: (lead: Int, fallback: LocalTime, overwrite: Boolean, sections: Map<Int, LocalTime>) -> Unit
) {
    val context = LocalContext.current
    var lead by rememberSaveable { mutableStateOf(options.leadMinutes) }
    var fallbackHour by rememberSaveable { mutableStateOf(options.fallbackTime.hour) }
    var fallbackMinute by rememberSaveable { mutableStateOf(options.fallbackTime.minute) }
    val fallback = LocalTime.of(fallbackHour, fallbackMinute)
    var overwrite by rememberSaveable { mutableStateOf(options.overwrite) }
    var sectionsEncoded by rememberSaveable {
        mutableStateOf(SectionTimeTable.encode(options.sectionTimes))
    }
    val sections = remember(sectionsEncoded) { SectionTimeTable.decode(sectionsEncoded) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入选项") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("提前量：$lead 分钟", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { lead = (lead - 5).coerceAtLeast(0) }) { Text("-5") }
                    OutlinedButton(onClick = { lead = (lead + 5).coerceAtMost(60) }) { Text("+5") }
                }
                Text(
                    "表格里没有时间时使用的默认上课时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            timePickerContext(context),
                            { _, h, m ->
                                fallbackHour = h
                                fallbackMinute = m
                            },
                            fallback.hour,
                            fallback.minute,
                            DisplayPrefs.is24Hour(context)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "默认时间：${DisplayPrefs.formatTime(context, fallback.hour, fallback.minute)}"
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "覆盖现有闹钟",
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                stateDescription = if (overwrite) "已开启" else "已关闭"
                            }
                    )
                    CycleSwitch(
                        checked = overwrite,
                        onCheckedChange = { overwrite = it },
                        modifier = Modifier.semantics {
                            stateDescription = if (overwrite) "已开启" else "已关闭"
                        }
                    )
                }
                HorizontalDivider()
                Text("节次时间表", style = MaterialTheme.typography.titleSmall)
                (1..8).forEach { section ->
                    val time = sections[section] ?: SectionTimeTable.default()[section] ?: LocalTime.of(8, 0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 $section 节", modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    timePickerContext(context),
                                    { _, h, m ->
                                        sectionsEncoded = SectionTimeTable.encode(
                                            sections + (section to LocalTime.of(h, m))
                                        )
                                    },
                                    time.hour,
                                    time.minute,
                                    DisplayPrefs.is24Hour(context)
                                ).show()
                            }
                        ) {
                            Text(DisplayPrefs.formatTime(context, time.hour, time.minute))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(lead, fallback, overwrite, sections) }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------------
// 手动指定结构
// ---------------------------------------------------------------------

@Composable
private fun ManualMappingDialog(
    onDismiss: () -> Unit,
    onApply: (DetectedLayout) -> Unit
) {
    var dated by rememberSaveable { mutableStateOf(false) }
    var headerRow by rememberSaveable { mutableStateOf(0) }
    var dateColumn by rememberSaveable { mutableStateOf(1) }
    var timeColumn by rememberSaveable { mutableStateOf<Int?>(2) }
    var weekColumnsEncoded by rememberSaveable {
        mutableStateOf(encodeWeekColumns(DEFAULT_WEEK_COLUMNS))
    }
    val weekColumns = remember(weekColumnsEncoded) { decodeWeekColumns(weekColumnsEncoded) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动指定表格结构") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dated = false }) { Text("按星期") }
                    OutlinedButton(onClick = { dated = true }) { Text("按日期") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("表头行：第 ${headerRow + 1} 行", modifier = Modifier.weight(1f))
                    TextButton(onClick = { headerRow = (headerRow - 1).coerceAtLeast(0) }) { Text("-") }
                    TextButton(onClick = { headerRow += 1 }) { Text("+") }
                }
                if (dated) {
                    ColumnPicker(
                        label = "日期列",
                        selected = dateColumn,
                        onSelect = { dateColumn = it }
                    )
                    ColumnPicker(
                        label = "时间列（可无）",
                        selected = timeColumn,
                        allowNone = true,
                        onSelect = { timeColumn = it.takeIf { index -> index >= 0 } }
                    )
                } else {
                    (1..7).forEach { weekday ->
                        ColumnPicker(
                            label = weekdayName(weekday),
                            selected = weekColumns[weekday] ?: (weekday - 1),
                            onSelect = {
                                weekColumnsEncoded = encodeWeekColumns(weekColumns + (weekday to it))
                            }
                        )
                    }
                    ColumnPicker(
                        label = "时间/节次列（可无）",
                        selected = timeColumn,
                        allowNone = true,
                        onSelect = { timeColumn = it.takeIf { index -> index >= 0 } }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dataStart = headerRow + 1
                val layout = if (dated) {
                    DetectedLayout.Dated(
                        headerRow = headerRow,
                        dateColumn = dateColumn,
                        weekdayColumn = null,
                        timeColumn = timeColumn,
                        titleColumns = (0..20).filter { it != dateColumn && it != timeColumn },
                        dataStartRow = dataStart
                    )
                } else {
                    DetectedLayout.WeeklyGrid(
                        headerRow = headerRow,
                        dayColumns = weekColumns,
                        timeColumn = timeColumn,
                        titleColumns = (0..20).filter { it !in weekColumns.values && it != timeColumn },
                        dataStartRow = dataStart
                    )
                }
                onApply(layout)
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ColumnPicker(
    label: String,
    selected: Int?,
    allowNone: Boolean = false,
    onSelect: (Int) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.let { columnName(it) } ?: "无")
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("选择 $label") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    if (allowNone) {
                        Text(
                            "无",
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelect(-1)
                                expanded = false
                            }.semantics { role = Role.Button }.padding(12.dp)
                        )
                    }
                    (0..20).forEach { index ->
                        Text(
                            columnName(index),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelect(index)
                                expanded = false
                            }.semantics { role = Role.Button }.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("关闭") }
            }
        )
    }
}

private fun columnName(index: Int): String = ('A' + index.coerceIn(0, 25)).toString()

private fun weekdayName(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[weekday - 1]

private val DEFAULT_WEEK_COLUMNS = mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6, 7 to 7)

private fun encodeWeekColumns(columns: Map<Int, Int>): String =
    columns.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }

private fun decodeWeekColumns(raw: String): Map<Int, Int> =
    raw.split(';').mapNotNull { part ->
        val weekday = part.substringBefore('=', "").toIntOrNull()
        val column = part.substringAfter('=', "").toIntOrNull()
        if (weekday != null && column != null) weekday to column else null
    }.toMap().ifEmpty { DEFAULT_WEEK_COLUMNS }
