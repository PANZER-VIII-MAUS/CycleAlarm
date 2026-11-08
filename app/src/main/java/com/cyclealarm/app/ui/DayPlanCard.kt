package com.cyclealarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.ActivityTypeEntity
import com.cyclealarm.app.data.DayPlanEntity
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.components.CycleSwitch
import com.cyclealarm.app.ui.theme.LocalExtraColors
import com.cyclealarm.app.ui.theme.DarkBackground

@Composable
internal fun DayPlanCard(
    dayText: String,
    isToday: Boolean,
    plan: DayPlanEntity?,
    planType: ActivityTypeEntity?,
    manualAlarms: List<AlarmEntity>,
    onOpenPlan: () -> Unit,
    onTogglePlan: (Boolean) -> Unit,
    onAddAlarm: () -> Unit,
    onEditManual: (AlarmEntity) -> Unit,
    onToggleManual: (AlarmEntity, Boolean) -> Unit,
    onDeleteManual: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    // 时间以安排自身时间为准（同步逻辑保证与闹钟一致）；非响铃状态显示「未安排」占位
    val ringsToday = plan != null && planType != null && planType.rings
    val showTime = plan != null && ringsToday
    val timeText = if (plan != null && showTime) {
        DisplayPrefs.formatClock(context, plan.hour, plan.minute)
    } else {
        stringResource(R.string.plan_unset)
    }
    val fontScale = LocalDensity.current.fontScale
    val largeFont = fontScale > 1.25f
    val timeStyle = if (showTime) {
        // 大字号时不能把主时间调小（五轮复审 §4.7）：compact 布局已经给时间整行宽度，
        // 六轮复审 §3.1：headlineSmall 只有 20sp（比 headlineMedium 30sp 更小），
        // 改用 displaySmall（36sp）才真的「字号变大 → 时间变大」
        if (largeFont) {
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.headlineMedium
        }
    } else {
        // 占位文案：字号缩到 20sp / Medium，但保留 headlineMedium 的 lineHeight(36sp)，卡片高度不变
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
    val timeColor = if (showTime) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val baseSubtitle = when {
        plan == null || planType == null -> stringResource(R.string.plan_tap_to_set)
        !planType.rings -> stringResource(R.string.plan_rest)
        !plan.enabled -> stringResource(R.string.plan_paused)
        else -> planType.name
    }
    // 12 小时制时，把「上午/下午」挪到副标题前缀，避免大行被撑宽
    val amPm = if (plan != null && showTime) DisplayPrefs.amPmLabel(context, plan.hour) else null
    val subtitle = if (amPm != null) "$amPm · $baseSubtitle" else baseSubtitle
    // 开关语义 = 今天会不会响：休息/未安排置灰关闭；启用=开（可关掉暂停）；已暂停=关（可点开恢复）
    val switchOn = ringsToday && plan?.enabled == true
    val switchEnabled = ringsToday
    val planStateDescription = when {
        !ringsToday -> stringResource(R.string.day_not_ringing)
        switchOn -> stringResource(R.string.state_on)
        else -> stringResource(R.string.state_off)
    }
    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = LocalExtraColors.current.switchOffTrack,
        checkedThumbColor = Color.White,
        uncheckedThumbColor = Color.White
    )
    val highlightColor = if (MaterialTheme.colorScheme.background == DarkBackground) {
        LocalExtraColors.current.primaryDark
    } else {
        MaterialTheme.colorScheme.primary
    }
    val cardModifier = if (isToday) {
        Modifier.border(1.5.dp, highlightColor, RoundedCornerShape(24.dp))
    } else {
        Modifier
    }

    CycleCard(
        modifier = cardModifier,
        onClick = onOpenPlan,
        onClickLabel = stringResource(R.string.cd_open_plan)
    ) {
        run {
            // 七轮复审 §2.3：去掉 maxWidth 兜底——360dp 手机卡内只有 280dp，
            // 用卡内宽判定会让普通手机永远走紧凑布局。只按屏宽 + 字号判定。
            val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val compact = largeFont || screenWidthDp < 340
            Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        // 用 subtitle（12 小时制时带「上午/下午」）而不是 baseSubtitle，读屏不再丢上下午
                        contentDescription = "$dayText $subtitle $timeText"
                        stateDescription = planStateDescription
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (compact) {
                    // 大字号：时间独占第二行，避免「07:30」被挤压截断
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dayText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isToday) {
                                Box(
                                    Modifier
                                        .padding(start = 6.dp)
                                        .size(6.dp)
                                        .background(highlightColor, CircleShape)
                                )
                            }
                        }
                        Text(
                            timeText,
                            style = timeStyle,
                            color = timeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isToday) {
                            Box(
                                Modifier
                                    .padding(start = 6.dp)
                                    .size(6.dp)
                                    .background(highlightColor, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    // 占据剩余宽度：字号/显示大小放大时也不会把「07:30」裁掉
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            timeText,
                            style = timeStyle,
                            color = timeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            IconButton(onClick = onAddAlarm) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_alarm),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CycleSwitch(
                checked = switchOn,
                onCheckedChange = onTogglePlan,
                enabled = switchEnabled,
                colors = switchColors,
                modifier = Modifier.semantics {
                    stateDescription = planStateDescription
                }
            )
            }
        }
        manualAlarms.forEach { alarm ->
            ManualAlarmRow(
                alarm = alarm,
                switchColors = switchColors,
                onEdit = { onEditManual(alarm) },
                onToggle = { onToggleManual(alarm, it) },
                onDelete = { onDeleteManual(alarm) }
            )
        }
    }
}


@Composable
internal fun ManualAlarmRow(
    alarm: AlarmEntity,
    switchColors: SwitchColors,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .semantics {
                    role = Role.Button
                    stateDescription = if (alarm.enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
                }
        ) {
            Text(
                "${DisplayPrefs.formatTime(context, alarm.hour, alarm.minute)} · " +
                    alarm.label.ifBlank { stringResource(R.string.alarm_fallback) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_delete_alarm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CycleSwitch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = switchColors,
            modifier = Modifier.semantics {
                stateDescription = if (alarm.enabled) context.getString(R.string.state_on) else context.getString(R.string.state_off)
            }
        )
    }
}

