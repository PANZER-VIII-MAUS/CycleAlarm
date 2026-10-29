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
import androidx.compose.ui.unit.dp
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
        title = { Text("关键权限设置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuideRow(
                    title = "通知权限",
                    desc = "Android 13+ 必须手动授权，否则闹钟不响",
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
                    title = "精确闹钟权限",
                    desc = "Android 12+ 系统限制，授予后闹钟可准时触发",
                    granted = PermissionHelper.canScheduleExactAlarms(context),
                    onClick = { PermissionHelper.openExactAlarmSettings(context) }
                )
                GuideRow(
                    title = "电池优化白名单",
                    desc = "选择“不优化”，防止闹钟被杀",
                    granted = states.ignoringBatteryOptimizations,
                    onClick = { PermissionHelper.openBatteryOptimizationSettings(context) }
                )
                GuideRow(
                    title = "自启动",
                    desc = if (autoStartFallback) {
                        "未找到厂商设置页，已打开应用详情；请手动允许自启动"
                    } else {
                        "ColorOS 等国产 ROM 必设，保证开机后闹钟重建"
                    },
                    granted = null,
                    onClick = { autoStartFallback = !PermissionHelper.openAutoStartSettings(context) }
                )
                GuideRow(
                    title = "悬浮窗",
                    desc = "部分 ROM 用于锁屏/全屏场景提醒",
                    granted = PermissionHelper.canDrawOverlays(context),
                    onClick = { PermissionHelper.openOverlaySettings(context) }
                )
                GuideRow(
                    title = "响铃通知渠道",
                    desc = "关掉「闹钟响铃」渠道会导致响铃无界面",
                    granted = states.ringChannelEnabled,
                    onClick = { PermissionHelper.openAppNotificationSettings(context) }
                )
                GuideRow(
                    title = "全屏提醒",
                    desc = "Android 14+ 需在通知设置中单独开启",
                    granted = states.fullScreenIntent,
                    onClick = { PermissionHelper.openAppNotificationSettings(context) }
                )
                Text(
                    text = "当前设备：${RomHelper.vendor.displayName}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = RomHelper.extraTips().joinToString("\n") { "· $it" } +
                        "\n· 多任务界面下拉本应用卡片，点锁锁定后台",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
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
                contentDescription = "已授权",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            TextButton(onClick = onClick) { Text("去设置") }
        }
    }
}

private data class GuideStates(
    val notifications: Boolean,
    val ignoringBatteryOptimizations: Boolean?,
    val ringChannelEnabled: Boolean?,
    val fullScreenIntent: Boolean?
)
