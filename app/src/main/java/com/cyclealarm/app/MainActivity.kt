package com.cyclealarm.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cyclealarm.app.core.AlarmResyncHelper
import com.cyclealarm.app.core.NotificationChannels
import com.cyclealarm.app.service.AlarmGuardService
import com.cyclealarm.app.ui.MainScreen
import com.cyclealarm.app.ui.theme.ThemeManager
import com.cyclealarm.app.ui.theme.CycleAlarmTheme
import com.cyclealarm.app.worker.DailyResyncWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** 在 super.onCreate 之前设置窗口背景，消除「系统浅色 + App 强制深色」的白闪。 */
    private fun applyStoredTheme() {
        val mode = ThemeManager.readStoredMode(this)
        val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (ThemeManager.resolveDark(mode, systemDark)) {
            setTheme(R.style.Theme_CycleAlarm_OpaqueDark)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动背景按 App 主题选择（而不是只跟系统 night），避免强制深色时闪白
        applyStoredTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ColorOS 等 ROM 可能拦截前台服务或后台任务，启动阶段任何异常都不能让应用闪退
        try {
            NotificationChannels.createAll(this)
            ContextCompat.startForegroundService(this, Intent(this, AlarmGuardService::class.java))
            DailyResyncWorker.enqueue(this)
        } catch (e: Exception) {
            Log.w("CycleAlarm", "startup service failed, continuing without it", e)
        }

        // 每次回到前台都重建排期，兜底假期/跨天等状态变化
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    AlarmResyncHelper.resyncAll(applicationContext)
                } catch (e: Exception) {
                    Log.w("CycleAlarm", "resync failed, continuing", e)
                }
            }
        }

        setContent {
            CycleAlarmTheme {
                MainScreen()
            }
        }
    }
}
