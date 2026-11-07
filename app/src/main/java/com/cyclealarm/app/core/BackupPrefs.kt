package com.cyclealarm.app.core

import androidx.core.content.edit
import android.content.Context

/**
 * 备份相关用户偏好。
 *
 * 「写到系统下载目录」是**可选**功能（默认关）：它会把明文备份放进公共目录，
 * 属于隐私敏感行为，必须由用户显式打开（六轮复审 §4.2）。
 */
object BackupPrefs {

    private const val PREFS = "app_prefs"
    private const val KEY_DOWNLOAD_COPY = "backup_download_copy_enabled"

    fun downloadCopyEnabled(context: Context): Boolean = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOWNLOAD_COPY, false)
    }.getOrDefault(false)

    fun setDownloadCopyEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putBoolean(KEY_DOWNLOAD_COPY, enabled) }
        }
    }
}
