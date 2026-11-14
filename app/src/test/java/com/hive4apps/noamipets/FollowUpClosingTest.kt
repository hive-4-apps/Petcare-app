package com.hive4apps.noamipets

import androidx.test.core.app.ApplicationProvider
import com.hive4apps.noamipets.core.Pet
import com.hive4apps.noamipets.core.Species
import com.hive4apps.noamipets.core.Treatment
import com.hive4apps.noamipets.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Closing a pet's follow-up stops its cares; reopening it (or adding a care) brings it back. */
@RunWith(RobolectricTestRunner::class)
class FollowUpClosingTest {
    private val today = LocalDate.now()
    private val store = run {
        Store.resetForTests()
        Store.get(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun closeThenReopen() {
        store.addPet(Pet("p", "Rex", Species.DOG))
        val ongoing = Treatment("a", "p", "Apoquel", durationDays = null, startDate = today.minusDays(3).toString())
        val stoppedBefore = Treatment("b", "p", "Collyre", durationDays = null, startDate = today.minusDays(10).toString(), stoppedOn = today.minusDays(2).toString())
        store.saveTreatments(listOf(ongoing, stoppedBefore), null)

        store.closeFollowUp("p", today)
        var data = store.data.value
        assertEquals(today.toString(), data.pets.single().closedOn)
        assertEquals(today.plusDays(1).toString(), data.treatments.first { it.id == "a" }.stoppedOn)
        assertEquals(today.minusDays(2).toString(), data.treatments.first { it.id == "b" }.stoppedOn)
        assertEquals(emptyList<Treatment>(), data.treatmentsFollowedOn(today))

        store.reopenFollowUp("p")
        data = store.data.value
        assertNull(data.pets.single().closedOn)
        assertNull(data.treatments.first { it.id == "a" }.stoppedOn)
        // Stopped by hand before the closing: stays stopped.
        assertEquals(today.minusDays(2).toString(), data.treatments.first { it.id == "b" }.stoppedOn)
    }

    @Test
    fun aNewCareReopensTheFollowUp() {
        store.addPet(Pet("p", "Rex", Species.DOG))
        store.saveTreatments(listOf(Treatment("a", "p", "Apoquel", startDate = today.toString())), null)
        store.closeFollowUp("p", today)

        // Editing an old care keeps the follow-up closed...
        store.saveTreatments(listOf(store.data.value.treatments.single().copy(notes = "avec la nourriture")), null)
        assertEquals(today.toString(), store.data.value.pets.single().closedOn)

        // ...a new one reopens it.
        store.saveTreatments(listOf(Treatment("c", "p", "Metacam", startDate = today.toString())), null)
        assertNull(store.data.value.pets.single().closedOn)
    }

    @Test
    fun deleteTheFollowUpKeepsThePetAndItsHistory() {
        store.addPet(Pet("p", "Rex", Species.DOG))
        store.addPet(Pet("q", "Minou", Species.CAT))
        store.saveTreatments(
            listOf(
                Treatment("a", "p", "Apoquel", durationDays = null, startDate = today.minusDays(2).toString()),
                Treatment("m", "q", "Metacam", startDate = today.toString()),
            ),
            null,
        )
        store.setDone("a|${today.minusDays(1)}|08:00", true)
        store.closeFollowUp("p", today)

        store.deleteFollowUp("p", today)
        var data = store.data.value
        assertEquals(listOf("p", "q"), data.pets.map { it.id })
        assertNull(data.pets.first { it.id == "p" }.closedOn)
        // Out of the follow-up (no plan, no reminder)...
        assertEquals(listOf("m"), data.treatments.map { it.id })
        // ...but in the history, with the dose given.
        val archived = data.history.single()
        assertEquals("a", archived.id)
        assertEquals(today.toString(), archived.archivedOn)
        assertEquals(setOf("a|${today.minusDays(1)}|08:00"), data.done.keys)

        store.clearHistory("p")
        data = store.data.value
        assertEquals(emptyList<Treatment>(), data.history)
        assertEquals(emptySet<String>(), data.done.keys)
    }

    @Test
    fun deletingACareKeepsItsGivenDosesInTheHistory() {
        store.addPet(Pet("p", "Rex", Species.DOG))
        store.saveTreatments(
            listOf(
                Treatment("a", "p", "Apoquel", startDate = today.minusDays(1).toString()),
                Treatment("typo", "p", "Apocuel", startDate = today.toString()),
            ),
            null,
        )
        store.setDone("a|${today.minusDays(1)}|08:00", true)

        store.deleteTreatment("a", today)
        store.deleteTreatment("typo", today)
        val data = store.data.value
        assertEquals(emptyList<Treatment>(), data.treatments)
        // Given at least once: kept in the history. Never given (entered by mistake): erased.
        assertEquals(listOf("a"), data.history.map { it.id })
        assertEquals(setOf("a|${today.minusDays(1)}|08:00"), data.done.keys)

        store.deleteFromHistory("a")
        assertEquals(emptyList<Treatment>(), store.data.value.history)
        assertEquals(emptySet<String>(), store.data.value.done.keys)
    }
}
