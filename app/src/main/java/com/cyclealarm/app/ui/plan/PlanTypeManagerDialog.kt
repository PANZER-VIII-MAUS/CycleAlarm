package com.cyclealarm.app.ui.plan

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.ui.theme.timePickerContext
import com.cyclealarm.app.ui.ActivityTypeEntitySaver

/**
 * 安排类型管理：增删改、上下移、显示使用天数。
 * 修改时间且该类型正在使用时，弹窗询问是否把这 N 天一起改。
 */
@Composable
fun PlanTypeManagerDialog(
    types: List<ActivityTypeEntity>,
    usage: Map<Int, Int>,
    onAdd: (name: String, hour: Int, minute: Int, rings: Boolean) -> Unit,
    onUpdate: (type: ActivityTypeEntity, applyToExisting: Boolean) -> Unit,
    onDelete: (id: Int) -> Unit,
    onMove: (id: Int, up: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editing by rememberSaveable(stateSaver = ActivityTypeEntitySaver) {
        mutableStateOf<ActivityTypeEntity?>(null)
    }
    var pendingTimeChange by rememberSaveable(stateSaver = ActivityTypeEntitySaver) {
        mutableStateOf<ActivityTypeEntity?>(null)
    }
    var deleting by rememberSaveable(stateSaver = ActivityTypeEntitySaver) {
        mutableStateOf<ActivityTypeEntity?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ptm_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                if (types.isEmpty()) {
                    Text(
                        stringResource(R.string.ptm_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                types.forEachIndexed { index, type ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(type.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                buildString {
                                    if (type.rings) {
                                        append(stringResource(R.string.ptm_ring_at, DisplayPrefs.formatTime(context, type.hour, type.minute)))
                                    } else {
                                        append(stringResource(R.string.ptm_no_ring))
                                    }
                                    append(stringResource(R.string.ptm_used_days, usage[type.id] ?: 0))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onMove(type.id, true) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                        }
                        IconButton(
                            onClick = { onMove(type.id, false) },
                            enabled = index < types.lastIndex
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                        }
                        IconButton(onClick = { editing = type }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { deleting = type }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedButton(
                    onClick = {
                        editing = ActivityTypeEntity(name = "", hour = 7, minute = 0, rings = true)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.ptm_add))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
    )

    editing?.let { type ->
        PlanTypeEditDialog(
            type = type,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                val inUse = (usage[updated.id] ?: 0) > 0
                val timeChanged = updated.hour != type.hour || updated.minute != type.minute
                when {
                    type.id == 0 -> onAdd(updated.name, updated.hour, updated.minute, updated.rings)
                    inUse && timeChanged -> pendingTimeChange = updated
                    else -> onUpdate(updated, false)
                }
            }
        )
    }

    pendingTimeChange?.let { updated ->
        val days = usage[updated.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingTimeChange = null },
            title = { Text(stringResource(R.string.ptm_update_title)) },
            text = {
                Text(
                    stringResource(R.string.ptm_update_text, days) + "\n\n" +
                        stringResource(R.string.ptm_update_detail, days)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(updated, true)
                    pendingTimeChange = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdate(updated, false)
                    pendingTimeChange = null
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    deleting?.let { type ->
        val days = usage[type.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.ptm_delete_title, type.name)) },
            text = {
                Text(
                    if (days > 0) {
                        stringResource(R.string.ptm_delete_in_use, days)
                    } else {
                        stringResource(R.string.ptm_delete_confirm)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(type.id)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun PlanTypeEditDialog(
    type: ActivityTypeEntity,
    onDismiss: () -> Unit,
    onSave: (ActivityTypeEntity) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable(type.id) { mutableStateOf(type.name) }
    var hour by rememberSaveable(type.id) { mutableStateOf(type.hour) }
    var minute by rememberSaveable(type.id) { mutableStateOf(type.minute) }
    var rings by rememberSaveable(type.id) { mutableStateOf(type.rings) }
    val nameValid = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
                    Text(if (type.id == 0) stringResource(R.string.ptm_add_title) else stringResource(R.string.ptm_edit_title))
                },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ptm_name_label)) },
                    singleLine = true,
                    isError = !nameValid,
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
                    enabled = rings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (rings) {
                            stringResource(R.string.ptm_ring_time, DisplayPrefs.formatTime(context, hour, minute))
                        } else {
                            stringResource(R.string.ptm_no_ring_time)
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ptm_ring_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.ptm_ring_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val ringStateText = if (rings) {
                        stringResource(R.string.ptm_ring_state_on)
                    } else {
                        stringResource(R.string.ptm_no_ring)
                    }
                    CycleSwitch(
                        checked = rings,
                        onCheckedChange = { rings = it },
                        modifier = Modifier.semantics {
                            stateDescription = ringStateText
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValid,
                onClick = {
                    onSave(
                        type.copy(
                            name = name.trim(),
                            hour = hour,
                            minute = minute,
                            rings = rings
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
