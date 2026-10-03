package com.hive4apps.petcare.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hive4apps.petcare.MainActivity
import com.hive4apps.petcare.R
import com.hive4apps.petcare.core.CareKind
import com.hive4apps.petcare.core.Dose
import com.hive4apps.petcare.core.HH_MM
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.data.CrashLog
import com.hive4apps.petcare.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm at a time: the next dose not yet validated. When it rings, the doses of that moment are notified
 * and the following alarm is set. Each notification has "Done" and "In 15 min" buttons.
 */
object Reminders {
    private const val CHANNEL_ID = "care_reminders"
    const val SNOOZE_MINUTES = 15L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun reschedule(context: Context) {
        runCatching {
            val data = Store.get(context).data.value
            val advance = data.reminderAdvanceMinutes.toLong()
            val now = LocalDateTime.now()
            val next = Schedule.nextDoseAfter(data.treatments, now.plusMinutes(advance), data.done.keys)
            val alarms = context.getSystemService(AlarmManager::class.java)
            val pending = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RING),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (next == null) {
                alarms.cancel(pending)
                return
            }
            val ringAt = next.at.minusMinutes(advance).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setAlarm(context, alarms, ringAt, pending)
        }.onFailure { CrashLog.record(context, "Reminders.reschedule", it) }
    }

    /** "In 15 min": rings again for this dose only. */
    fun snooze(context: Context, doseKey: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, doseKey.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZED)
                .putExtra(ReminderReceiver.EXTRA_DOSE, doseKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(context, alarms, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000, pending)
    }

    private fun setAlarm(context: Context, alarms: AlarmManager, at: Long, pending: PendingIntent) {
        if (canRingOnTime(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            // Without the exact alarm permission Android may ring a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun canRingOnTime(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Doses due around [moment] (and missed ones of the last hour) that are not validated yet. */
    fun dueDoses(context: Context, moment: LocalDateTime): List<Dose> {
        val data = Store.get(context).data.value
        val advance = data.reminderAdvanceMinutes.toLong()
        val from = moment.minusHours(1)
        val until = moment.plusMinutes(advance + 1)
        return listOf(from.toLocalDate(), until.toLocalDate()).distinct()
            .flatMap { day -> Schedule.dayPlan(data.treatments, day) }
            .filter { it.at > from && it.at <= until && it.key !in data.done }
            .distinctBy { it.key }
    }

    fun findDose(context: Context, doseKey: String): Dose? {
        val parts = doseKey.split('|')
        if (parts.size != 3) return null
        val treatment = Store.get(context).data.value.treatments.find { it.id == parts[0] } ?: return null
        val day = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        return Schedule.dosesOn(treatment, day).find { it.key == doseKey }
    }

    fun notifyDose(context: Context, dose: Dose) {
        if (!canNotify(context)) return
        val data = Store.get(context).data.value
        val pet = data.pets.find { it.id == dose.treatment.petId }
        val emoji = if (pet?.species == Species.CAT) "🐱" else "🐶"
        val title = context.getString(kindAction(dose.treatment.kind), pet?.name.orEmpty(), dose.treatment.name)
        val parts = listOfNotNull(
            dose.treatment.dose.takeIf { it.isNotBlank() },
            dose.at.toLocalTime().format(HH_MM),
            dose.total?.let { context.getString(R.string.dose_number_of, dose.number, it) },
        )
        val body = (parts + listOfNotNull(dose.treatment.notes.takeIf { it.isNotBlank() })).joinToString(" · ")
        val id = dose.key.hashCode()

        fun action(action: String) = PendingIntent.getBroadcast(
            context, id xor action.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_DOSE, dose.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFF08A4B.toInt())
            .setContentTitle("$emoji $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_done), action(ReminderReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.notif_snooze, SNOOZE_MINUTES), action(ReminderReceiver.ACTION_SNOOZE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, doseKey: String) {
        NotificationManagerCompat.from(context).cancel(doseKey.hashCode())
    }

    fun kindAction(kind: CareKind): Int = when (kind) {
        CareKind.SHAMPOO -> R.string.notif_title_shampoo
        CareKind.EYE_DROPS, CareKind.EAR_DROPS -> R.string.notif_title_drops
        CareKind.TOPICAL, CareKind.ANTIPARASITIC -> R.string.notif_title_apply
        CareKind.INJECTION -> R.string.notif_title_injection
        else -> R.string.notif_title_give
    }
}
