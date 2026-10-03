package com.hive4apps.petcare

import android.app.Application
import com.hive4apps.petcare.data.CrashLog
import com.hive4apps.petcare.reminders.Reminders

class PetCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Reminders.createChannel(this)
        Reminders.reschedule(this)
    }
}
