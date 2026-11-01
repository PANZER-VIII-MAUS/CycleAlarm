package com.cyclealarm.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclealarm.app.R
import com.cyclealarm.app.core.PermissionHelper
import com.cyclealarm.app.core.RomHelper

/** 首次启动与右上角“i”按钮打开的权限引导。 */
@Composable
fun PermissionGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 从系统设置返回后刷新真实状态（每次 ON_RESUME 重新读取）
    var refreshTick by remember { mutableStateOf(0) }
    var autoStartFallback by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val states = remember(refreshTick) {
        GuideStates(
            notifications = PermissionHelper.notificationsGranted(context),
            ignoringBatteryOptimizations = PermissionHelper.isIgnoringBatteryOptimizations(context),
            ringChannelEnabled = PermissionHelper.ringChannelEnabled(context),
            fullScreenIntent = PermissionHelper.canUseFullScreenIntent(context)
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuideRow(
                    title = stringResource(R.string.perm_notifications),
                    desc = stringResource(R.string.perm_notifications_desc),
                    granted = PermissionHelper.notificationsGranted(context),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionHelper.openAppNotificationSettings(context)
                        }
                    }
                )
                GuideRow(
                    title = stringResource(R.string.perm_exact_alarm),
                    desc = stringResource(R.string.perm_exact_alarm_desc),
                    granted = PermissionHelper.canScheduleExactAlarms(context),
                    onClick = { PermissionHelper.openExactAlarmSettings(context) }
                )
                GuideRow(
                    title = stringResource(R.string.perm_battery),
                    desc = stringResource(R.string.perm_battery_desc),
                    granted = states.ignoringBatteryOptimizations,
                    onClick = { PermissionHelper.openBatteryOptimizationSettings(context) }
                )
                GuideRow(
                    title = stringResource(R.string.perm_autostart),
                    desc = if (autoStartFallback) {
                        stringResource(R.string.perm_autostart_fallback)
                    } else {
                        stringResource(R.string.perm_autostart_desc)
                    },
                    granted = null,
                    onClick = { autoStartFallback = !PermissionHelper.openAutoStartSettings(context) }
                )
                GuideRow(
                    title = stringResource(R.string.perm_overlay),
                    desc = stringResource(R.string.perm_overlay_desc),
                    granted = PermissionHelper.canDrawOverlays(context),
                    onClick = { PermissionHelper.openOverlaySettings(context) }
                )
                GuideRow(
                    title = stringResource(R.string.perm_ring_channel),
                    desc = stringResource(R.string.perm_ring_channel_desc),
                    granted = states.ringChannelEnabled,
                    onClick = { PermissionHelper.openAppNotificationSettings(context) }
                )
                GuideRow(
                    title = stringResource(R.string.perm_fullscreen),
                    desc = stringResource(R.string.perm_fullscreen_desc),
                    granted = states.fullScreenIntent,
                    onClick = { PermissionHelper.openAppNotificationSettings(context) }
                )
                Text(
                    text = stringResource(R.string.perm_device, RomHelper.displayName(context)),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = RomHelper.extraTips(context).joinToString("\n") { "· $it" } +
                        "\n" + stringResource(R.string.perm_multitask_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.db_error_ok)) }
        }
    )
}

@Composable
private fun GuideRow(
    title: String,
    desc: String,
    granted: Boolean?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted == true) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.perm_granted),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onClick) { Text(stringResource(R.string.perm_go_settings)) }
        }
    }
}

private data class GuideStates(
    val notifications: Boolean,
    val ignoringBatteryOptimizations: Boolean?,
    val ringChannelEnabled: Boolean?,
    val fullScreenIntent: Boolean?
)
