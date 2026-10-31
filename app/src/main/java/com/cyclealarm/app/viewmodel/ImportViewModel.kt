package com.cyclealarm.app.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyclealarm.app.core.ImportPrefs
import com.cyclealarm.app.data.AlarmImportWriter
import com.cyclealarm.app.data.ImportWeeklyPolicy
import com.cyclealarm.app.data.AppDatabase
import com.cyclealarm.app.importer.csv.CsvSheetReader
import com.cyclealarm.app.importer.DetectedLayout
import com.cyclealarm.app.importer.FileKind
import com.cyclealarm.app.importer.FileTypeSniffer
import com.cyclealarm.app.importer.ImportOptions
import com.cyclealarm.app.importer.ImportWarning
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.LayoutDetector
import com.cyclealarm.app.importer.ScheduleImporter
import com.cyclealarm.app.importer.Sheet
import com.cyclealarm.app.importer.xlsx.XlsxSheetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime

/** 导入流程的状态机。 */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState
    data class SheetPick(val sheetNames: List<String>) : ImportUiState
    data object Parsing : ImportUiState
    data class Preview(
        val sheetName: String,
        val entries: List<ImportedEntry>,
        val warnings: List<ImportWarning>,
        val selected: Set<Int>,
        val options: ImportOptions,
        val needsManualMapping: Boolean
    ) : ImportUiState

    data object Writing : ImportUiState
    data class Done(
        val cycleCount: Int,
        val dateCount: Int,
        val switchedToWeekly: Boolean
    ) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    private val writer = AlarmImportWriter(app)
    private var sheets: List<Sheet> = emptyList()
    private var selectedSheet: Sheet? = null
    private var options = ImportOptions(
        leadMinutes = ImportPrefs.leadMinutes(app),
        sectionTimes = ImportPrefs.sectionTimes(app)
    )
    private var lastPreview: ImportUiState.Preview? = null
    private var manualLayout: DetectedLayout? = null
    private val _alarmCount = MutableStateFlow(0)
    val alarmCount: StateFlow<Int> = _alarmCount
    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> = _undoAvailable

    init {
        refreshUndoAvailability()
    }

    fun refreshAlarmCount() {
        viewModelScope.launch {
            val db = AppDatabase.get(getApplication())
            _alarmCount.value = db.alarmDao().getAll().size + db.dateAlarmDao().getAll().size
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }

    fun onFileSelected(uri: Uri) {
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            try {
                val (bytes, name) = withContext(Dispatchers.IO) { readBytes(uri) }
                val kind = FileTypeSniffer.sniff(bytes.take(512).toByteArray())
                when (kind) {
                    FileKind.HTML_TABLE -> {
                        _state.value = ImportUiState.Error("该文件其实是网页表格，请用 Excel 打开后另存为 .xlsx")
                        return@launch
                    }
                    FileKind.XLSX -> {
                        val parsed = withContext(Dispatchers.IO) {
                            XlsxSheetReader().read(ByteArrayInputStream(bytes))
                        }
                        handleParsedSheets(parsed)
                    }
                    FileKind.UNSUPPORTED -> {
                        // 非 zip / 非 HTML：按 CSV 尝试（CSV 的文件头没有魔数）
                        val parsed = withContext(Dispatchers.IO) {
                            CsvSheetReader().read(ByteArrayInputStream(bytes), name)
                        }
                        handleParsedSheets(parsed)
                    }
                }
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: "读取文件失败，请确认文件没有损坏"
                )
            }
        }
    }

    private fun readBytes(uri: Uri): Pair<ByteArray, String> {
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }.orEmpty()
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开所选文件")
        val bytes = stream.use { input ->
            val limit = 10 * 1024 * 1024
            val buffer = ByteArray(8 * 1024)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > limit) throw IllegalStateException("文件过大（超过 10MB），请精简后再导入")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
        if (bytes.isEmpty()) throw IllegalStateException("文件是空的")
        return bytes to name
    }

    private fun handleParsedSheets(parsed: List<Sheet>) {
        if (parsed.isEmpty()) {
            _state.value = ImportUiState.Error("文件里没有可读取的工作表")
            return
        }
        sheets = parsed
        if (parsed.size == 1) {
            selectSheet(parsed.first().name)
        } else {
            _state.value = ImportUiState.SheetPick(parsed.map { it.name })
        }
    }

    fun selectSheet(name: String) {
        val sheet = sheets.firstOrNull { it.name == name } ?: return
        selectedSheet = sheet
        manualLayout = null
        _state.value = ImportUiState.Parsing
        viewModelScope.launch {
            runImport(sheet)
        }
    }

    private suspend fun runImport(sheet: Sheet) {
        try {
            val detected = manualLayout ?: LayoutDetector.detect(sheet)
            val result = withContext(Dispatchers.Default) {
                ScheduleImporter.import(sheet, options, if (detected is DetectedLayout.NeedsManual) null else detected)
            }
            val preview = ImportUiState.Preview(
                sheetName = sheet.name,
                entries = result.entries,
                warnings = result.warnings,
                selected = result.entries.indices.toSet(),
                options = options,
                needsManualMapping = detected is DetectedLayout.NeedsManual
            )
            lastPreview = preview
            _state.value = preview
        } catch (e: Exception) {
            _state.value = ImportUiState.Error(e.message ?: "解析失败")
        }
    }

    fun updateEntry(index: Int, entry: ImportedEntry) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        val entries = preview.entries.toMutableList()
        if (index !in entries.indices) return
        entries[index] = entry
        _state.value = preview.copy(entries = entries)
        lastPreview = _state.value as ImportUiState.Preview
    }

    fun deleteEntry(index: Int) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        if (index !in preview.entries.indices) return
        val entries = preview.entries.toMutableList().apply { removeAt(index) }
        val selected = entries.indices.toSet()
        _state.value = preview.copy(entries = entries, selected = selected)
        lastPreview = _state.value as ImportUiState.Preview
    }

    fun toggleSelected(index: Int) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        val selected = preview.selected.toMutableSet()
        if (!selected.add(index)) selected.remove(index)
        _state.value = preview.copy(selected = selected)
        lastPreview = _state.value as ImportUiState.Preview
    }

    fun setAllSelected(selected: Boolean) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        _state.value = preview.copy(
            selected = if (selected) preview.entries.indices.toSet() else emptySet()
        )
        lastPreview = _state.value as ImportUiState.Preview
    }

    fun updateOptions(
        leadMinutes: Int,
        fallbackTime: LocalTime,
        overwrite: Boolean,
        sectionTimes: Map<Int, LocalTime>
    ) {
        options = options.copy(
            leadMinutes = leadMinutes.coerceIn(0, 60),
            fallbackTime = fallbackTime,
            overwrite = overwrite,
            sectionTimes = sectionTimes
        )
        ImportPrefs.saveLeadMinutes(getApplication(), options.leadMinutes)
        ImportPrefs.saveSectionTimes(getApplication(), sectionTimes)
        val sheet = selectedSheet ?: return
        _state.value = ImportUiState.Parsing
        viewModelScope.launch { runImport(sheet) }
    }

    fun applyManualLayout(layout: DetectedLayout) {
        manualLayout = layout
        val sheet = selectedSheet ?: return
        _state.value = ImportUiState.Parsing
        viewModelScope.launch { runImport(sheet) }
    }

    /**
     * @param weeklyConfirmed 用户明确点了「仍然导入并切换每周」。
     *   未经确认时不会切换循环方式，第 8 天以后的条目会被跳过（门禁不再是装饰）。
     */
    fun write(weeklyConfirmed: Boolean = false) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        val entries = preview.entries.filterIndexed { index, _ -> index in preview.selected }
        if (entries.isEmpty()) {
            _state.value = preview
            return
        }
        _state.value = ImportUiState.Writing
        viewModelScope.launch {
            try {
                val confirmedWeeklySwitch =
                    weeklyConfirmed && ImportWeeklyPolicy.requiresWeeklySwitch(entries)
                val result = withContext(Dispatchers.IO) {
                    writer.write(
                        entries = entries,
                        overwrite = preview.options.overwrite,
                        weeklySwitchConfirmed = confirmedWeeklySwitch
                    )
                }
                _undoAvailable.value = true
                _state.value = ImportUiState.Done(
                    result.cycleCount,
                    result.dateCount,
                    result.switchedToWeekly
                )
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "写入闹钟失败")
            }
        }
    }

    /** 待确认的撤销（导入后新增条目会被一并回滚）。 */
    private val _undoConfirm = MutableStateFlow<AlarmImportWriter.UndoPreview?>(null)
    val undoConfirm: StateFlow<AlarmImportWriter.UndoPreview?> = _undoConfirm

    fun undo() {
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) { writer.previewUndo() }
                if (preview != null && preview.hasExtras) {
                    _undoConfirm.value = preview
                    return@launch
                }
                performUndo()
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "撤销失败")
            }
        }
    }

    fun confirmUndo() {
        _undoConfirm.value = null
        viewModelScope.launch {
            try {
                performUndo()
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "撤销失败")
            }
        }
    }

    fun cancelUndo() {
        _undoConfirm.value = null
    }

    private suspend fun performUndo() {

        val result = withContext(Dispatchers.IO) { writer.undo() }
        _undoAvailable.value = false
        _state.value = when (result) {
            AlarmImportWriter.UndoResult.Success -> lastPreview ?: ImportUiState.Idle
            AlarmImportWriter.UndoResult.Corrupted ->
                ImportUiState.Error("上次导入记录已损坏，已清除，无法撤销")
            AlarmImportWriter.UndoResult.NoSnapshot ->
                ImportUiState.Error("没有可撤销的导入")
        }
    }

    fun closeImport(onFinished: () -> Unit) {
        viewModelScope.launch {
            // 快照保留到 24 小时过期：离开导入页不再删除，重开 App 仍可撤销
            refreshUndoAvailability()
            onFinished()
        }
    }

    private fun refreshUndoAvailability() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { writer.hasUndoSnapshot() }
            }.onSuccess { _undoAvailable.value = it }
                .onFailure { Log.e(TAG, "check import snapshot failed", it) }
        }
    }

    private companion object {
        const val TAG = "ImportViewModel"
    }
}
