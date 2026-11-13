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
    fun deleteTheFollowUpKeepsThePet() {
        store.addPet(Pet("p", "Rex", Species.DOG))
        store.addPet(Pet("q", "Minou", Species.CAT))
        store.saveTreatments(
            listOf(
                Treatment("a", "p", "Apoquel", startDate = today.toString()),
                Treatment("m", "q", "Metacam", startDate = today.toString()),
            ),
            null,
        )
        store.setDone("a|$today|08:00", true)
        store.closeFollowUp("p", today)

        store.deleteFollowUp("p")
        val data = store.data.value
        assertEquals(listOf("p", "q"), data.pets.map { it.id })
        assertNull(data.pets.first { it.id == "p" }.closedOn)
        assertEquals(listOf("m"), data.treatments.map { it.id })
        assertEquals(emptySet<String>(), data.done.keys)
    }
}
