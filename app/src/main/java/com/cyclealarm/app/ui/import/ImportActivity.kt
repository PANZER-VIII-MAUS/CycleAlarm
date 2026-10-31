package com.cyclealarm.app.ui.import

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclealarm.app.ui.theme.CycleAlarmTheme
import com.cyclealarm.app.ui.theme.ThemeManager
import com.cyclealarm.app.viewmodel.ImportViewModel

/** 课表/排班表导入页。 */
class ImportActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.cyclealarm.app.core.AppLanguage.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyStartupTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CycleAlarmTheme {
                ImportScreen(
                    viewModel = viewModel(),
                    onClose = { finish() }
                )
            }
        }
    }
}
