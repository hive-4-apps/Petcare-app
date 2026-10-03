package com.hive4apps.petcare

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hive4apps.petcare.core.HH_MM
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.core.Treatment
import com.hive4apps.petcare.data.Store
import com.hive4apps.petcare.reminders.ReminderReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class RemindersTest {
    @Test
    fun ringNotifyAndValidate() {
        Store.resetForTests()
        val app = ApplicationProvider.getApplicationContext<PetCareApp>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val store = Store.get(app)
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        // A dose two minutes ago and one in an hour.
        val times = listOf(now.minusMinutes(2), now.plusHours(1)).map { it.toLocalTime().format(HH_MM) }.sorted()
        store.addPet(Pet("p", "Thor", Species.DOG))
        store.saveTreatments(
            listOf(Treatment("t", "p", "Apoquel", dose = "1/2 comprimido", times = times, startDate = LocalDate.now().minusDays(1).toString())),
            null,
        )

        // The next alarm is set.
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertNotNull(alarms.nextScheduledAlarm)

        // It rings: the dose of two minutes ago is notified, in Portuguese.
        ReminderReceiver().onReceive(app, Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_RING))
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val due = notifications.single()
        val title = due.extras.getCharSequence("android.title").toString()
        assertTrue(title, title.contains("Thor: dar Apoquel"))
        assertEquals("✓ Feito", due.actions[0].title.toString())

        // "Done" from the notification.
        due.actions[0].actionIntent.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, store.data.value.done.size)
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
}
