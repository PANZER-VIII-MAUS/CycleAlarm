package com.cyclealarm.app.core

import androidx.core.net.toUri
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** 循环播放系统闹钟铃声（单例，避免多个 worker 同时发声）。 */
object RingtonePlayer {
    private var player: MediaPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var rampTask: Runnable? = null

    /**
     * @param customUri 每闹钟自定义铃声；null/空 = 用系统默认闹钟铃声
     * @param ramp 是否音量渐强（0.3 起每 3 秒 +0.1，直到满音量）
     */
    fun play(context: Context, customUri: String? = null, ramp: Boolean = false) {
        stop()
        val fallback: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // 候选：自定义铃声 → 系统闹钟铃声 → 提示音。
        // 自定义 URI 失效（文件被删、权限被回收）时必须回退，否则就是「静音响铃」（四轮复审 N5）
        val candidates = buildList {
            RingOptionPolicy.resolveUri(customUri)
                ?.let { runCatching { it.toUri() }.getOrNull() }
                ?.let { add(it) }
            fallback?.let { if (it !in this) add(it) }
        }
        if (candidates.isEmpty()) error("No default ringtone available")
        var lastError: Exception? = null
        for (uri in candidates) {
            val started = runCatching { startWith(context, uri, ramp) }
            if (started.isSuccess) return
            lastError = started.exceptionOrNull() as? Exception
            player = null
        }
        throw lastError ?: IllegalStateException("ringtone unavailable")
    }

    private fun startWith(context: Context, uri: Uri, ramp: Boolean) {
        val p = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            runCatching { setDataSource(context, uri) }
            isLooping = true
            prepare()
            if (ramp) {
                setVolume(RingOptionPolicy.RAMP_START, RingOptionPolicy.RAMP_START)
            }
            start()
        }
        player = p
        if (ramp) {
            scheduleRamp()
        }
    }

    private fun scheduleRamp() {
        rampTask?.let { handler.removeCallbacks(it) }
        var step = 0
        val task = object : Runnable {
            override fun run() {
                val active = player ?: return
                step++
                val volume = RingOptionPolicy.rampVolume(step)
                runCatching { active.setVolume(volume, volume) }
                if (volume < 1f) {
                    handler.postDelayed(this, RingOptionPolicy.RAMP_INTERVAL_MS)
                }
            }
        }
        rampTask = task
        handler.postDelayed(task, RingOptionPolicy.RAMP_INTERVAL_MS)
    }

    fun stop() {
        rampTask?.let { handler.removeCallbacks(it) }
        rampTask = null
        val p = player
        player = null
        if (p != null) {
            try {
                p.stop()
            } catch (_: Exception) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
    }
}
