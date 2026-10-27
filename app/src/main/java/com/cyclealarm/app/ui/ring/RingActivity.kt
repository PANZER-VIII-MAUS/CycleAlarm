package com.cyclealarm.app.ui.ring

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.core.AlarmScheduler
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.RingPrefs
import com.cyclealarm.app.service.RingService
import com.cyclealarm.app.ui.theme.CycleAlarmTheme

/** 全屏响铃界面：锁屏唤醒显示，支持停止与稍后提醒。 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0) ?: 0
        val kind = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
            ?: AlarmScheduler.KIND_CYCLE
        val dayIndex = intent?.getIntExtra(AlarmScheduler.EXTRA_DAY_INDEX, 0) ?: 0
        val hour = intent?.getIntExtra(AlarmScheduler.EXTRA_HOUR, 8) ?: 8
        val minute = intent?.getIntExtra(AlarmScheduler.EXTRA_MINUTE, 0) ?: 0
        val label = intent?.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: ""
        val snoozeMinutes = RingPrefs.snoozeMinutes(this)

        setContent {
            CycleAlarmTheme {
                RingScreen(
                    dayIndex = dayIndex,
                    hour = hour,
                    minute = minute,
                    label = label,
                    snoozeMinutes = snoozeMinutes,
                    onStop = { stopRing() },
                    onSnooze = {
                        AlarmScheduler.snoozeAt(
                            context = this,
                            kind = kind,
                            alarmId = alarmId,
                            dayIndex = dayIndex,
                            hour = hour,
                            minute = minute,
                            label = label,
                            minutes = snoozeMinutes
                        )
                        stopRing()
                    }
                )
            }
        }
    }

    private fun stopRing() {
        stopService(Intent(this, RingService::class.java))
        finish()
    }
}

@Composable
private fun RingScreen(
    dayIndex: Int,
    hour: Int,
    minute: Int,
    label: String,
    snoozeMinutes: Int,
    onStop: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("闹钟", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                DisplayPrefs.formatTime(LocalContext.current, hour, minute),
                color = Color.White,
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                label.ifBlank { "第 ${dayIndex + 1} 天" },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
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
