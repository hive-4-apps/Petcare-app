package com.hive4apps.petcare

import androidx.test.core.app.ApplicationProvider
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.data.Store
import com.hive4apps.petcare.ui.Draft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

/** A prescription entered after the course started (today at 20:00, or a day already past). */
@RunWith(RobolectricTestRunner::class)
class PastStartTest {
    private val now = LocalDateTime.parse("2026-11-04T22:30")
    private val today = now.toLocalDate()

    @Test
    fun todayKeepsTodayEvenWhenEveryTimeIsBehind() {
        val d = Draft(name = "Apoquel", times = listOf("08:00", "20:00")).startingOn(today, now)
        assertEquals(today, d.startDate)
        assertEquals("20:00", d.startTime)
        assertEquals(listOf("2026-11-04T20:00"), d.pastDoses(now).map { it.at.toString() })
    }

    @Test
    fun onceADayAt20hStartedToday() {
        val d = Draft(name = "Metacam", times = listOf("20:00")).startingOn(today, now)
        assertEquals(today, d.startDate)
        assertNull(d.startTime)
        assertEquals(1, d.pastDoses(now).size)
    }

    @Test
    fun todayWithATimeToComeStartsThere() {
        val d = Draft(name = "Apoquel", times = listOf("08:00", "20:00")).startingOn(today, LocalDateTime.parse("2026-11-04T10:00"))
        assertEquals("20:00", d.startTime)
        assertTrue(d.pastDoses(LocalDateTime.parse("2026-11-04T10:00")).isEmpty())
    }

    @Test
    fun aDayAlreadyPastKeepsItsDate() {
        val d = Draft(name = "Apoquel", times = listOf("08:00", "20:00")).startingOn(today.minusDays(2), now)
        assertEquals(LocalDate.parse("2026-11-02"), d.startDate)
        assertNull(d.startTime)
        assertEquals(6, d.pastDoses(now).size)
    }

    @Test
    fun pastDosesAreSavedAsGivenOnlyWhenAsked() {
        Store.resetForTests()
        val store = Store.get(ApplicationProvider.getApplicationContext())
        store.addPet(Pet("p", "Rex", Species.DOG))
        val draft = Draft(name = "Apoquel", times = listOf("20:00")).startingOn(today.minusDays(1), now)

        store.saveTreatments(listOf(draft.toTreatment("p", null)), null, draft.copy(markPastGiven = false).alreadyGiven("p", now))
        assertEquals(emptySet<String>(), store.data.value.done.keys)

        store.saveTreatments(listOf(draft.toTreatment("p", null)), null, draft.alreadyGiven("p", now))
        assertEquals(
            setOf("${draft.id}|2026-11-03|20:00", "${draft.id}|2026-11-04|20:00"),
            store.data.value.done.keys,
        )
    }
}
