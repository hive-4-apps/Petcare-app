package com.hive4apps.noamipets

import android.app.Application
import com.hive4apps.noamipets.data.CrashLog
import com.hive4apps.noamipets.reminders.Reminders

class NoamiPetsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Reminders.createChannel(this)
        Reminders.reschedule(this)
    }
}
