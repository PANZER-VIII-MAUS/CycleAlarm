package com.cyclealarm.app.core

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
        val resolved = RingOptionPolicy.resolveUri(customUri)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val uri: Uri? = resolved ?: fallback
        if (uri == null) error("No default ringtone available")
        var pendingPlayer: MediaPlayer? = null
        try {
            val p = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                if (ramp) {
                    setVolume(RingOptionPolicy.RAMP_START, RingOptionPolicy.RAMP_START)
                }
                start()
            }
            pendingPlayer = p
            player = p
            if (ramp) {
                scheduleRamp()
            }
        } catch (throwable: Exception) {
            player = null
            runCatching { pendingPlayer?.release() }
            throw throwable
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
