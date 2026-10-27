package com.cyclealarm.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyclealarm.app.ui.theme.CycleAlarmTheme

/** 设置页（独立页面）。 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CycleAlarmTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
