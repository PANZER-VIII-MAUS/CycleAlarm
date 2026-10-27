package com.cyclealarm.app

import android.app.Application
import com.cyclealarm.app.core.NotificationChannels

class CycleAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
