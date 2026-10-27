package com.cyclealarm.app.core

import android.content.Intent

/** Pure decision for system events that require an alarm reschedule. */
object BootResyncPolicy {
    fun shouldResync(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
}
