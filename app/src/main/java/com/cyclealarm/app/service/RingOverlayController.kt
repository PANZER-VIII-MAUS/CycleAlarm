package com.cyclealarm.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.cyclealarm.app.core.DisplayPrefs
import com.cyclealarm.app.core.RingPrefs
import com.cyclealarm.app.core.RingUiFallbackPolicy

/**
 * 悬浮窗兜底响铃界面。
 *
 * 用途：后台启动限制（BAL）或 ROM 定制策略把 [com.cyclealarm.app.ui.ring.RingActivity]
 * 拦掉时，用已授予的「悬浮窗/后台弹出界面」权限直接画一层全屏窗口，保证用户有
 * 「停止 / 稍后」可点——否则响铃期间用户只能强杀进程。
 *
 * 仅在 [RingUiFallbackPolicy.shouldShowOverlay] 为真时由 [RingService] 调用。
 */
class RingOverlayController(private val context: Context) {

    private var view: View? = null
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    fun isShowing(): Boolean = view != null

    fun show(
        timeText: String,
        detail: String,
        showSnooze: Boolean,
        snoozeMinutes: Int,
        onStop: () -> Unit,
        onSnooze: () -> Unit
    ) {
        if (view != null) return
        val wm = windowManager ?: return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(242, 8, 10, 12))
            setPadding(dp(32), dp(64), dp(32), dp(48))
        }
        root.addView(TextView(context).apply {
            text = timeText
            setTextColor(Color.WHITE)
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(context).apply {
            text = detail
            setTextColor(Color.parseColor("#B9C2CC"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(48))
        })
        root.addView(TextView(context).apply {
            text = "闹钟正在响铃"
            setTextColor(Color.parseColor("#B9C2CC"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        if (showSnooze) {
            root.addView(solidButton("稍后 $snoozeMinutes 分钟", "#2A2F36", density) { onSnooze() })
        }
        root.addView(solidButton("停止", "#C84239", density) { onStop() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val added = runCatching { wm.addView(root, params) }.isSuccess
        if (added) {
            view = root
        }
    }

    private fun solidButton(
        text: String,
        color: String,
        density: Float,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        fun dp(value: Int): Int = (value * density).toInt()
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 18f
        isAllCaps = false
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(28).toFloat()
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply { topMargin = dp(12) }
        layoutParams = params
        setOnClickListener { onClick() }
    }

    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager?.removeView(current) }
    }

    /** 供策略查询的静态判断：是否具备悬浮窗权限。 */
    companion object {
        fun canDrawOverlays(context: Context): Boolean =
            runCatching { android.provider.Settings.canDrawOverlays(context) }.getOrDefault(false)

        fun ringPrefsSnoozeMinutes(context: Context): Int = RingPrefs.snoozeMinutes(context)

        fun formatTime(context: Context, hour: Int, minute: Int): String =
            DisplayPrefs.formatTime(context, hour, minute)

        const val DELAY_MS = RingUiFallbackPolicy.OVERLAY_DELAY_MS
    }
}
