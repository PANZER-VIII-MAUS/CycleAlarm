package com.cyclealarm.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.PlanPickerAction
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.DayPlanEntity

/**
 * 选择某一天的安排：当前安排（可进改时间）+ 类型列表 + 未安排 + 管理类型。
 *
 * 0.2.2 起卡片点击总是先打开本选择器：换类型不必再「先删除变未安排、再点一次」。
 */
@Composable
fun PlanPickerDialog(
    dayText: String,
    types: List<ActivityTypeEntity>,
    currentPlan: DayPlanEntity?,
    onSelect: (ActivityTypeEntity) -> Unit,
    onClear: () -> Unit,
    onEditCurrentTime: (() -> Unit)? = null,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentType = currentPlan?.let { plan -> types.firstOrNull { it.id == plan.typeId } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dayText 的安排") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                if (currentPlan != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onEditCurrentTime != null) {
                                onEditCurrentTime?.invoke()
                            }
                            .semantics { role = Role.Button }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "当前：${currentType?.name ?: "自定义"} " +
                                    DisplayPrefs.formatTime(
                                        context,
                                        currentPlan.hour,
                                        currentPlan.minute
                                    ),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "点击改这一天的安排时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "改时间"
                        )
                    }
                    HorizontalDivider()
                }
                if (types.isEmpty()) {
                    Text(
                        "还没有安排类型，请先到「管理安排类型」里新增。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                types.forEach { type ->
                    val selected = currentPlan?.typeId == type.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val action = PlanPickerAction.forTypeClick(currentPlan, type.id)
                                when {
                                    action == PlanPickerAction.EditCurrentTime &&
                                        onEditCurrentTime != null -> onEditCurrentTime.invoke()
                                    else -> onSelect(type)
                                }
                            }
                            .semantics { role = Role.Button }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                type.name + if (selected) "（当前）" else "",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (type.rings) {
                                    "${DisplayPrefs.formatTime(context, type.hour, type.minute)} 响铃"
                                } else {
                                    "不响铃"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = "当前安排")
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "未安排",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClear() }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
                TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text("管理安排类型…")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
