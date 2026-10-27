package com.cyclealarm.app.ui.import.components

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import com.cyclealarm.app.ui.formatEpochDay
import java.util.Locale

/** 导入预览表：可勾选、可编辑单行、可删除。 */
@Composable
fun PreviewTable(
    entries: List<ImportedEntry>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onEdit: (Int, ImportedEntry) -> Unit,
    onDelete: (Int) -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(entries) { index, entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = index in selected,
                        onCheckedChange = { onToggle(index) }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editingIndex = index }
                    ) {
                        Text(
                            "${entryLabel(entry)}  ${String.format(Locale.CHINA, "%02d:%02d", entry.hour, entry.minute)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            entry.title.ifBlank { "未命名" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editingIndex = index }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑这一行")
                    }
                    IconButton(onClick = { onDelete(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除这一行")
                    }
                }
            }
        }
    }

    editingIndex?.let { index ->
        val entry = entries.getOrNull(index) ?: return@let
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingIndex = null },
            onSave = { updated ->
                onEdit(index, updated)
                editingIndex = null
            }
        )
    }
}

@Composable
private fun EditEntryDialog(
    entry: ImportedEntry,
    onDismiss: () -> Unit,
    onSave: (ImportedEntry) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(entry.title) }
    var hour by remember { mutableStateOf(entry.hour) }
    var minute by remember { mutableStateOf(entry.minute) }
    var weekday by remember { mutableStateOf(entry.weekday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑这一条") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（课程 / 班次）") },
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
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("时间：${String.format(Locale.CHINA, "%02d:%02d", hour, minute)}")
                }
                if (entry.kind == ScheduleKind.WEEKLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { day ->
                            TextButton(onClick = { weekday = day }) {
                                Text(
                                    weekdayName(day),
                                    color = if (weekday == day) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        entry.epochDay?.let { formatEpochDay(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(entry.copy(title = title.trim(), hour = hour, minute = minute, weekday = weekday))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun entryLabel(entry: ImportedEntry): String = when (entry.kind) {
    ScheduleKind.WEEKLY -> entry.weekday?.let { weekdayName(it) } ?: "每周"
    ScheduleKind.DATED -> entry.epochDay?.let { formatEpochDay(it) } ?: "按日期"
}

private fun weekdayName(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[(weekday - 1).coerceIn(0, 6)]
