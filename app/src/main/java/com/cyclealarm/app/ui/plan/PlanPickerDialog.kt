package com.cyclealarm.app.ui.plan

import androidx.compose.ui.res.stringResource
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
import com.cyclealarm.app.R
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
        title = { Text(stringResource(R.string.plan_day_title, dayText)) },
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
                                stringResource(R.string.plan_current, currentType?.name ?: stringResource(R.string.cycle_custom)) +
                                    DisplayPrefs.formatTime(
                                        context,
                                        currentPlan.hour,
                                        currentPlan.minute
                                    ),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.plan_tap_change),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.action_change_time)
                        )
                    }
                    HorizontalDivider()
                }
                if (types.isEmpty()) {
                    Text(
                        stringResource(R.string.plan_no_types_hint),
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
                                type.name + if (selected) stringResource(R.string.plan_current_marker) else "",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (type.rings) {
                                    stringResource(R.string.ptm_ring_at, DisplayPrefs.formatTime(context, type.hour, type.minute))
                                } else {
                                    stringResource(R.string.ptm_no_ring)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_current_plan))
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.plan_unset),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClear() }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
                TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.plan_manage))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
