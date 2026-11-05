package com.cyclealarm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.cyclealarm.app.R
import com.cyclealarm.app.core.PermissionHealth
import com.cyclealarm.app.core.PermissionHealthPolicy
import com.cyclealarm.app.core.PermissionHelper

/**
 * 锁屏响铃健康横幅。
 *
 * 真机教训：ColorOS 上「通知被关 / 没有悬浮窗权限 / 不在电池白名单」都会导致
 * 锁屏时只有铃声、没有「停止/稍后」界面，而应用不给任何提示。
 * 这里在主页常驻一条提示，点「去设置」直接跳到对应系统页面。
 */
@Composable
fun RingHealthBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val health = remember(refreshTick) {
        PermissionHealth(
            notifications = PermissionHelper.notificationsGranted(context),
            ringChannel = PermissionHelper.ringChannelEnabled(context) ?: true,
            fullScreenIntent = PermissionHelper.canUseFullScreenIntent(context),
            overlay = PermissionHelper.canDrawOverlays(context),
            batteryExempt = PermissionHelper.isIgnoringBatteryOptimizations(context)
        )
    }
    val issues = PermissionHealthPolicy.issues(health)
    if (issues.isEmpty()) return

    // 注意：joinToString 的 lambda 不是 @Composable，必须用 context.getString 取文案
    val issueNames = issues.map { issue -> context.getString(issueNameRes(issue)) }
    val names = issueNames.joinToString("、")
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.perm_health_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.perm_health_missing, issues.size, names),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { openSettingsFor(context, issues.first()) }
                ) {
                    Text(stringResource(R.string.perm_health_action))
                }
            }
        }
    }
}

private fun issueNameRes(issue: PermissionHealth.Issue): Int = when (issue) {
    PermissionHealth.Issue.NOTIFICATIONS -> R.string.perm_issue_notifications
    PermissionHealth.Issue.RING_CHANNEL -> R.string.perm_issue_ring_channel
    PermissionHealth.Issue.FULL_SCREEN_INTENT -> R.string.perm_issue_fsi
    PermissionHealth.Issue.OVERLAY -> R.string.perm_issue_overlay
    PermissionHealth.Issue.BATTERY -> R.string.perm_issue_battery
}

private fun openSettingsFor(
    context: android.content.Context,
    issue: PermissionHealth.Issue
) {
    when (issue) {
        PermissionHealth.Issue.NOTIFICATIONS,
        PermissionHealth.Issue.RING_CHANNEL -> PermissionHelper.openAppNotificationSettings(context)
        PermissionHealth.Issue.FULL_SCREEN_INTENT -> PermissionHelper.openFullScreenIntentSettings(context)
        PermissionHealth.Issue.OVERLAY -> PermissionHelper.openOverlaySettings(context)
        PermissionHealth.Issue.BATTERY -> PermissionHelper.openBatteryOptimizationSettings(context)
    }
}
