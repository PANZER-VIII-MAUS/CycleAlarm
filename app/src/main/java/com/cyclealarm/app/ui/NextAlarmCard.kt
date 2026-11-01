package com.cyclealarm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.NextAlarm
import com.cyclealarm.app.core.NextAlarmDisplay
import com.cyclealarm.app.core.NextAlarmKind
import com.cyclealarm.app.core.cycleDayOf
import com.cyclealarm.app.data.AppSettingsEntity
import com.cyclealarm.app.data.CycleTypes
import com.cyclealarm.app.ui.components.CycleCard
import com.cyclealarm.app.ui.theme.LocalExtraColors
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun NextAlarmCard(
    nextAlarm: NextAlarm?,
    now: Long,
    settings: AppSettingsEntity?,
    modifier: Modifier = Modifier,
    onSkipNext: () -> Unit = {}
) {
    val context = LocalContext.current
    CycleCard(modifier = modifier) {
        if (nextAlarm == null || settings == null) {
            Text(
                text = stringResource(R.string.main_no_alarm),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.main_no_alarm_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = DisplayPrefs.formatClock(
                                context, nextAlarm.hour, nextAlarm.minute
                            ),
                            style = if (LocalDensity.current.fontScale > 1.25f) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineMedium
                            },
                            maxLines = 1,
                            softWrap = false,
                            color = LocalExtraColors.current.navy
                        )
                        if (!DisplayPrefs.is24Hour(context)) {
                            Text(
                                text = DisplayPrefs.amPmLabel(context, nextAlarm.hour).orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = NextAlarmDisplay.countdownText(context, nextAlarm.triggerAt, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 倒计时变化时让 TalkBack 播报，但不要打断用户当前的朗读
                        // 这里原来挂了 liveRegion：倒计时每分钟都变，TalkBack 会每小时播报 60 次
                        modifier = Modifier
                    )
                }
                Text(
                    text = nextAlarmSourceLabel(nextAlarm, settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            if (!nextAlarm.isSnooze) {
                TextButton(
                    onClick = onSkipNext,
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text(stringResource(R.string.main_skip_next)) }
            }
        }
    }
}


@Composable
internal fun nextAlarmSourceLabel(
    alarm: NextAlarm,
    settings: AppSettingsEntity
): String = when (alarm.kind) {
    NextAlarmKind.CYCLE -> {
        val triggerDate = Instant.ofEpochMilli(alarm.triggerAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        when (settings.cycleType) {
            CycleTypes.WEEKLY -> stringResource(weekdayRes(triggerDate.dayOfWeek.value))
            CycleTypes.DAILY -> stringResource(R.string.cycle_daily)
            else -> stringResource(
                R.string.ring_day_format,
                cycleDayOf(settings, triggerDate.toEpochDay())
            )
        }
    }

    NextAlarmKind.DATE -> stringResource(R.string.main_date_kind)
    NextAlarmKind.SNOOZE -> stringResource(R.string.main_snooze_kind)
}

/** 周一..周日（ISO dayOfWeek：1=周一）。 */

@Composable
internal fun weekdayShort(dayOfWeek: Int): String = stringResource(weekdayRes(dayOfWeek))

