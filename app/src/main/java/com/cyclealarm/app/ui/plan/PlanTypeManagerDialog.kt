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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.data.ActivityTypeEntity
import java.util.Locale

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
    var editing by remember { mutableStateOf<ActivityTypeEntity?>(null) }
    var pendingTimeChange by remember { mutableStateOf<ActivityTypeEntity?>(null) }
    var deleting by remember { mutableStateOf<ActivityTypeEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理安排类型") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                if (types.isEmpty()) {
                    Text(
                        "还没有安排类型",
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
                                        append(String.format(Locale.CHINA, "%02d:%02d 响铃", type.hour, type.minute))
                                    } else {
                                        append("不响铃")
                                    }
                                    append(" · 使用 ${usage[type.id] ?: 0} 天")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onMove(type.id, true) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            onClick = { onMove(type.id, false) },
                            enabled = index < types.lastIndex
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                        }
                        IconButton(onClick = { editing = type }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { deleting = type }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
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
                    Text("＋ 新增安排类型")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
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
            title = { Text("更新已安排的日子？") },
            text = {
                Text(
                    "该安排有 $days 天正在使用，改时间会一起更新，确定吗？\n\n" +
                        "确定：这 $days 天一起改成新时间。\n取消：只改类型默认值，已安排的日子保持原时间。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(updated, true)
                    pendingTimeChange = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdate(updated, false)
                    pendingTimeChange = null
                }) { Text("取消") }
            }
        )
    }

    deleting?.let { type ->
        val days = usage[type.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「${type.name}」") },
            text = {
                Text(
                    if (days > 0) {
                        "$days 天正在使用，删除后这些天变为未安排。"
                    } else {
                        "确定删除该安排类型？"
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
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
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
    var name by remember(type.id) { mutableStateOf(type.name) }
    var hour by remember(type.id) { mutableStateOf(type.hour) }
    var minute by remember(type.id) { mutableStateOf(type.minute) }
    var rings by remember(type.id) { mutableStateOf(type.rings) }
    val nameValid = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type.id == 0) "新增安排类型" else "编辑安排类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（如：上课 / 自习 / 早读）") },
                    singleLine = true,
                    isError = !nameValid,
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
                    enabled = rings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (rings) {
                            String.format(Locale.CHINA, "响铃时间：%02d:%02d", hour, minute)
                        } else {
                            "不响铃（无时间）"
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("响铃", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "关闭表示这类安排不产生闹钟（如休息）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = rings, onCheckedChange = { rings = it })
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
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
