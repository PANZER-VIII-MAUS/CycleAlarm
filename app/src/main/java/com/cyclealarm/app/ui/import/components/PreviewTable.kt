package com.cyclealarm.app.ui.import.components

import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.ui.weekdayRes
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.importer.ImportedEntry
import com.cyclealarm.app.importer.ScheduleKind
import com.cyclealarm.app.ui.formatEpochDay
import com.cyclealarm.app.ui.theme.timePickerContext

/** 导入预览表：可勾选、可编辑单行、可删除。 */
@Composable
fun PreviewTable(
    entries: List<ImportedEntry>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onEdit: (Int, ImportedEntry) -> Unit,
    onDelete: (Int) -> Unit
) {
    val context = LocalContext.current
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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
                            .semantics { role = Role.Button }
                    ) {
                        Text(
                            "${entryLabel(entry)}  ${DisplayPrefs.formatTime(context, entry.hour, entry.minute)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            entry.title.ifBlank { stringResource(R.string.preview_untitled) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editingIndex = index }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_row))
                    }
                    IconButton(onClick = { onDelete(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_row))
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
    var title by rememberSaveable { mutableStateOf(entry.title) }
    var hour by rememberSaveable { mutableStateOf(entry.hour) }
    var minute by rememberSaveable { mutableStateOf(entry.minute) }
    var weekday by rememberSaveable { mutableStateOf(entry.weekday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.preview_title_label)) },
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
                        entry.epochDay?.let { formatEpochDay(context, it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(entry.copy(title = title.trim(), hour = hour, minute = minute, weekday = weekday))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun entryLabel(entry: ImportedEntry): String = entryLabel(LocalContext.current, entry)

@Composable
private fun entryLabel(context: android.content.Context, entry: ImportedEntry): String = when (entry.kind) {
    ScheduleKind.WEEKLY -> entry.weekday?.let { weekdayName(it) } ?: stringResource(R.string.cycle_weekly)
    ScheduleKind.DATED -> entry.epochDay?.let { formatEpochDay(context, it) } ?: stringResource(R.string.main_date_kind)
}

@Composable
private fun weekdayName(weekday: Int): String =
    stringResource(weekdayRes((weekday - 1).coerceIn(0, 6) + 1))

