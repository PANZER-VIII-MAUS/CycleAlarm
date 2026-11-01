package com.cyclealarm.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器冒烟：主界面能起来、能进设置页。
 *
 * 这是「instrumentation 0 覆盖」的补课（四轮复审 §5.3）：Compose 层与 Activity 启动
 * 之前完全靠手测，R8 混淆后的运行时行为也没有端到端验证。
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun appString(id: Int): String =
        composeRule.activity.getString(id)

    @Test
    fun main_screen_shows_title_and_add_button() {
        composeRule.onNodeWithText(appString(R.string.main_title)).assertExists()
        composeRule.onNodeWithText(appString(R.string.main_add_alarm)).assertExists()
    }

    @Test
    fun settings_opens_from_top_bar() {
        composeRule.onNodeWithContentDescription(appString(R.string.cd_cycle_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(appString(R.string.settings_title)).assertExists()
    }

    @Test
    fun alarm_types_dialog_opens() {
        composeRule.onNodeWithContentDescription(appString(R.string.cd_alarm_types)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(appString(R.string.ptm_title)).assertExists()
    }
}
