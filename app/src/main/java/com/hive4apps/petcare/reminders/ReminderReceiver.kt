package com.hive4apps.petcare.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hive4apps.petcare.data.CrashLog
import com.hive4apps.petcare.data.Store
import java.time.LocalDateTime

/** The alarm of the next dose, and the buttons of the reminders. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val doseKey = intent.getStringExtra(EXTRA_DOSE)
            when (intent.action) {
                ACTION_RING -> {
                    Reminders.dueDoses(context, LocalDateTime.now()).forEach { Reminders.notifyDose(context, it) }
                    Reminders.reschedule(context)
                }
                ACTION_DONE -> if (doseKey != null) {
                    Store.get(context).setDone(doseKey, true)
                    Reminders.cancel(context, doseKey)
                }
                ACTION_SNOOZE -> if (doseKey != null) {
                    Reminders.cancel(context, doseKey)
                    Reminders.snooze(context, doseKey)
                }
                ACTION_SNOOZED -> if (doseKey != null && !Store.get(context).isDone(doseKey)) {
                    Reminders.findDose(context, doseKey)?.let { Reminders.notifyDose(context, it) }
                }
            }
        }.onFailure { CrashLog.record(context, "ReminderReceiver ${intent.action}", it) }
    }

    companion object {
        const val ACTION_RING = "com.hive4apps.petcare.RING"
        const val ACTION_DONE = "com.hive4apps.petcare.DONE"
        const val ACTION_SNOOZE = "com.hive4apps.petcare.SNOOZE"
        const val ACTION_SNOOZED = "com.hive4apps.petcare.SNOOZED"
        const val EXTRA_DOSE = "dose"
    }
}

/** Boot, app update, clock or time zone change: the alarm has to be set again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.reschedule(context)
    }
}
