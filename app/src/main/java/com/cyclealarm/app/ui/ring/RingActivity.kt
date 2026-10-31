package com.cyclealarm.app.ui.ring

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.RingLaunchValidator
import com.cyclealarm.app.core.RingPrefs
import com.cyclealarm.app.core.RingPreviewPolicy
import com.cyclealarm.app.service.RingService
import com.cyclealarm.app.ui.theme.CycleAlarmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全屏响铃界面：锁屏唤醒显示，支持停止与稍后提醒。
 *
 * 状态约束（0.2.2）：
 * - 返回键等同「停止」，不允许退出后铃声继续响；
 * - 屏幕常亮，避免用户看不清按钮；
 * - 支持 onNewIntent：连续两个闹钟时刷新为当前这条，而不是沿用第一条的数据；
 * - 服务停止（超时 / 通知停止 / 试听结束）时自动退出；
 * - 试听（preview）模式不显示「稍后」、不写任何数据库。
 */
class RingActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cyclealarm.app.core.AppLanguage.wrap(newBase))
    }


    private data class RingUiState(
        val alarmId: Int,
        val kind: String,
        val dayIndex: Int,
        val hour: Int,
        val minute: Int,
        val label: String,
        val originalHour: Int?,
        val originalMinute: Int?,
        val preview: Boolean
    )

    private var ringState by mutableStateOf<RingUiState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 返回键 = 停止（含二次确认的替代：直接停止并关闭服务，符合闹钟的使用直觉）
        onBackPressedDispatcher.addCallback(this) { finishRing() }

        followServiceState()
        applyIntent(intent)
        if (ringState == null) return

        // 竞态兜底：服务可能在 Activity 订阅前就已停止（例如响铃被通知栏迅速停止）。
        // 给服务 1.2 秒把 isRinging 置 true 的机会，否则直接退出，避免卡在响铃页。
        lifecycleScope.launch {
            delay(1_200)
            if (!RingService.isRinging.value) finish()
        }

        setContent {
            CycleAlarmTheme {
                RingContent()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    /** 服务停止后界面跟随退出（同进程 StateFlow，无跨进程开销）。 */
    private fun followServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var seenRinging = RingService.isRinging.value
                RingService.isRinging.collect { ringing ->
                    if (ringing) {
                        seenRinging = true
                    } else if (seenRinging) {
                        finish()
                    }
                }
            }
        }
    }

    /** 解析并校验 Intent；参数不完整时直接结束，绝不回落到写死的 08:00 假页面。 */
    private fun applyIntent(rawIntent: Intent?) {
        // 拿不到 Intent（或被外部拉起）时直接结束，绝不回落到写死的默认时间
        val source = rawIntent ?: run {
            finish()
            return
        }
        val alarmId = source.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        val kind = if (source?.hasExtra(AlarmScheduler.EXTRA_ALARM_KIND) == true) {
            source.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
        } else {
            null
        }
        val dayIndex = if (source?.hasExtra(AlarmScheduler.EXTRA_DAY_INDEX) == true) {
            source.getIntExtra(AlarmScheduler.EXTRA_DAY_INDEX, 0)
        } else {
            null
        }
        val hour = if (source?.hasExtra(AlarmScheduler.EXTRA_HOUR) == true) {
            source.getIntExtra(AlarmScheduler.EXTRA_HOUR, 0)
        } else {
            null
        }
        val minute = if (source?.hasExtra(AlarmScheduler.EXTRA_MINUTE) == true) {
            source.getIntExtra(AlarmScheduler.EXTRA_MINUTE, 0)
        } else {
            null
        }
        if (!RingLaunchValidator.isValid(alarmId, kind, dayIndex, hour, minute)) {
            finish()
            return
        }

        val checkedKind = requireNotNull(kind)
        val checkedDayIndex = requireNotNull(dayIndex)
        val checkedHour = requireNotNull(hour)
        val checkedMinute = requireNotNull(minute)
        val label = source.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
        val preview = source.getBooleanExtra(AlarmScheduler.EXTRA_PREVIEW, false)
        val originalHour = if (source.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR)) {
            source.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_HOUR, checkedHour)
        } else {
            null
        }
        val originalMinute = if (source.hasExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE)) {
            source.getIntExtra(AlarmScheduler.EXTRA_ORIGINAL_MINUTE, checkedMinute)
        } else {
            null
        }

        ringState = RingUiState(
            alarmId = alarmId,
            kind = checkedKind,
            dayIndex = checkedDayIndex,
            hour = checkedHour,
            minute = checkedMinute,
            label = label,
            originalHour = originalHour,
            originalMinute = originalMinute,
            preview = preview
        )
    }

    @Composable
    private fun RingContent() {
        val state = ringState ?: return
        val snoozeMinutes = RingPrefs.snoozeMinutes(this)
        RingScreen(
            kind = state.kind,
            dayIndex = state.dayIndex,
            hour = state.hour,
            minute = state.minute,
            label = state.label,
            originalHour = state.originalHour,
            originalMinute = state.originalMinute,
            snoozeMinutes = snoozeMinutes,
            showSnooze = RingPreviewPolicy.allowSnooze(state.preview),
            onStop = {
                if (RingPreviewPolicy.allowDatabaseWrites(state.preview)) {
                    AlarmScheduler.clearSnoozeForAlarm(this, state.kind, state.alarmId)
                }
                finishRing()
            },
            onSnooze = {
                if (RingPreviewPolicy.allowSnooze(state.preview)) {
                    lifecycleScope.launch {
                        val scheduled = AlarmScheduler.snoozeAt(
                            context = this@RingActivity,
                            kind = state.kind,
                            alarmId = state.alarmId,
                            dayIndex = state.dayIndex,
                            hour = state.hour,
                            minute = state.minute,
                            label = state.label,
                            minutes = snoozeMinutes,
                            originalHour = state.originalHour ?: state.hour,
                            originalMinute = state.originalMinute ?: state.minute
                        )
                        if (scheduled) finishRing()
                    }
                }
            }
        )
    }

    private fun finishRing() {
        stopService(Intent(this, RingService::class.java))
        finish()
    }
}

@Composable
private fun RingScreen(
    kind: String,
    dayIndex: Int,
    hour: Int,
    minute: Int,
    label: String,
    originalHour: Int?,
    originalMinute: Int?,
    snoozeMinutes: Int,
    showSnooze: Boolean,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    val context = LocalContext.current
    val detail = if (originalHour != null && originalMinute != null) {
        val source = if (kind == AlarmScheduler.KIND_DATE) {
            label.ifBlank { "按日期闹钟" }
        } else {
            "第 ${dayIndex + 1} 天"
        }
        "原定 ${DisplayPrefs.formatTime(context, originalHour, originalMinute)} · $source"
    } else {
        label.ifBlank { "第 ${dayIndex + 1} 天" }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("闹钟", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                DisplayPrefs.formatTime(context, hour, minute),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                detail,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (showSnooze) {
                    Button(
                        onClick = onSnooze,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A2F36),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text("稍后 $snoozeMinutes 分钟")
                    }
                }
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC84239),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text("停止")
                }
            }
        }
    }
}
