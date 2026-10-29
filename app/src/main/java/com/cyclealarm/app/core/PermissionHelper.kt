package com.cyclealarm.app.core

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 与 ColorOS / Android 权限体系相关的辅助方法。 */
object PermissionHelper {

    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 是否已加入电池优化白名单；无法检测时返回 null（UI 显示「无法检测」而不是假的「未授权」）。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean? = runCatching {
        val powerManager = context.getSystemService(android.os.PowerManager::class.java)
            ?: return null
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull()

    /** Android 14+ 全屏提醒是否可用；低版本无需申请，返回 null。 */
    fun canUseFullScreenIntent(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        }.getOrNull()
    }

    /** 响铃通知渠道是否开启（渠道不存在视为开启）；无法判断返回 null。 */
    fun ringChannelEnabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(NotificationChannels.RING) ?: return true
            channel.importance != NotificationManager.IMPORTANCE_NONE
        }.getOrNull()
    }

    /** Android 12+：请求 SCHEDULE_EXACT_ALARM。 */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    /** 请求加入电池优化白名单（部分 ROM 会直接跳转列表页）。 */
    fun openBatteryOptimizationSettings(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (!runCatching { context.startActivity(direct) }.isSuccess) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    fun openOverlaySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    /** 应用通知设置页（Android 14 的「全屏提醒」也在这里开启）。 */
    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 跳转各厂商的「自启动管理」页面，全部失败时退回应用详情页。
     *
     * 不再用 `resolveActivity` 预检：Android 11+ 包可见性过滤会让它对第三方厂商包直接
     * 返回 null，导致「预检全部失败 → 静默落到应用详情页」，用户以为跳转成功了。
     *
     * @return true = 打开了厂商自启动页；false = 退回了应用详情页（UI 据此提示文字路径）
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val targets = listOf(
            // ColorOS 12+ / ColorOS 11 / 旧 OPPO
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter.permission", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // vivo / 小米 / 华为 / 荣耀
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        )
        for (component in targets) {
            val opened = runCatching {
                context.startActivity(Intent().setComponent(component))
            }.isSuccess
            if (opened) return true
        }
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        return false
    }
}
