package com.cyclealarm.app.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/** 循环播放系统闹钟铃声（单例，避免多个 worker 同时发声）。 */
object RingtonePlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context) {
        stop()
        val uri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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
                start()
            }
            pendingPlayer = p
            player = p
        } catch (throwable: Exception) {
            player = null
            runCatching { pendingPlayer?.release() }
            throw throwable
        }
    }

    fun stop() {
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
