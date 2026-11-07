package com.cyclealarm.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 把「每日自动备份」额外写到系统「下载」目录，换机 / 卸载后用户仍能拿回文件。
 *
 * - Android 10+ 走 MediaStore（**不需要存储权限**）；
 * - Android 8/9（本应用 minSdk 26）没有免权限的公开目录写入，跳过（私有目录备份仍在）。
 *
 * 写入是「先查同名再插入」：每次启动不会产生重复文件。
 */
object DownloadBackupWriter {

    private const val TAG = "DownloadBackupWriter"
    private const val SUB_DIR = "CycleAlarm"
    private const val KEEP = 7

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 已存在同名文件则跳过；返回是否新写入。 */
    fun writeIfAbsent(context: Context, displayName: String, payload: String): Boolean {
        if (!isSupported()) return false
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR
        val exists = runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} = ? AND " +
                    "${MediaStore.Downloads.IS_PENDING} = 0",
                arrayOf(displayName, relativePath),
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
        if (exists) return false

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                ?: error("openOutputStream returned null")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            pruneOldCopies(context, collection, relativePath)
            true
        }.onFailure {
            Log.e(TAG, "write download backup failed", it)
            runCatching { resolver.delete(uri, null, null) }
        }.getOrDefault(false)
    }

    /** 公共目录只保留最近 [KEEP] 份，避免每天一份无限累积。 */
    private fun pruneOldCopies(
        context: Context,
        collection: android.net.Uri,
        relativePath: String
    ) {
        runCatching {
            val resolver = context.contentResolver
            val ids = mutableListOf<Long>()
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) ids += cursor.getLong(0)
            }
            ids.drop(KEEP).forEach { id ->
                runCatching {
                    resolver.delete(
                        android.content.ContentUris.withAppendedId(collection, id),
                        null,
                        null
                    )
                }
            }
        }
    }

    /** 供测试/文档用的路径提示。 */
    fun publicPathHint(context: Context): String =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUB_DIR).absolutePath
}
