package com.cyclealarm.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.ui.components.PillButton
import com.cyclealarm.app.ui.components.ScrimDialog

/**
 * 单日调整安排时间（只改这一天，其他同类型日不变）。
 * 视觉按设计稿：28dp 圆角对话框、54sp Navy 大时间、Material3 TimePicker 表盘、
 * 全宽胶囊「保存」、红色文字「删除」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTimeDialog(
    title: String,
    hour: Int,
    minute: Int,
    onSave: (hour: Int, minute: Int) -> Unit,
    onDeletePlan: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DisplayPrefs.is24Hour(context)
    )

    ScrimDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "只修改这一天的安排时间，其他日期不受影响",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors()
            )
        }
        Spacer(Modifier.height(16.dp))
        PillButton(
            text = "保存",
            onClick = { onSave(state.hour, state.minute) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(24.dp))
            TextButton(onClick = onDeletePlan) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
