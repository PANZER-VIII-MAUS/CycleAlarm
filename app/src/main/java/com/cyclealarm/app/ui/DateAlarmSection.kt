package com.cyclealarm.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.theme.timePickerContext
import com.cyclealarm.app.ui.DateAlarmEntitySaver
import java.util.Locale

/**
 * 按日期闹钟列表（课表/排班表按日期导入的一次性闹钟）。
 * 空列表时整块不显示，避免占用主界面空间。
 */
@Composable
fun DateAlarmSection(
    alarms: List<DateAlarmEntity>,
    settings: AppSettingsEntity?,
    holidays: List<HolidayEntity>,
    onToggle: (DateAlarmEntity, Boolean) -> Unit,
    onSave: (DateAlarmEntity) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (alarms.isEmpty()) return
    val context = LocalContext.current
    var editing by rememberSaveable(stateSaver = DateAlarmEntitySaver) {
        mutableStateOf<DateAlarmEntity?>(null)
    }

    CycleCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("按日期闹钟", style = MaterialTheme.typography.titleMedium)
            Text(
                "按日期导入的一次性闹钟；响过或日期已过就不再响",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            alarms.forEach { alarm ->
                val next = AlarmScheduler.nextTriggerForDateAlarm(alarm, settings, holidays)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editing = alarm }
                    ) {
                        Text(
                            formatEpochDay(alarm.epochDay),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${DisplayPrefs.formatTime(context, alarm.hour, alarm.minute)} · " +
                                alarm.label.ifBlank { "按日期闹钟" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            when {
                                !alarm.enabled -> "已停用"
                                next == null -> "已过期"
                                else -> "待响铃"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!alarm.enabled || next == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    IconButton(onClick = { onDelete(alarm.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除这条按日期闹钟")
                    }
                    Switch(
                        checked = alarm.enabled,
                        onCheckedChange = { onToggle(alarm, it) }
                    )
                }
            }
        }
    }

    editing?.let { alarm ->
        DateAlarmEditDialog(
            alarm = alarm,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            }
        )
    }
}

@Composable
private fun DateAlarmEditDialog(
    alarm: DateAlarmEntity,
    onDismiss: () -> Unit,
    onSave: (DateAlarmEntity) -> Unit
) {
    val context = LocalContext.current
    var label by rememberSaveable(alarm.id) { mutableStateOf(alarm.label) }
    var hour by rememberSaveable(alarm.id) { mutableStateOf(alarm.hour) }
    var minute by rememberSaveable(alarm.id) { mutableStateOf(alarm.minute) }
    var enabled by rememberSaveable(alarm.id) { mutableStateOf(alarm.enabled) }
    var muted by rememberSaveable(alarm.id) { mutableStateOf(alarm.mutedDuringHoliday) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatEpochDay(alarm.epochDay)} 的闹钟") },
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
                    Text("时间：${DisplayPrefs.formatTime(context, hour, minute)}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "这条按日期闹钟是否生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("假期静音", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "开启后落在假期里的这一天不响",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = muted, onCheckedChange = { muted = it })
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
