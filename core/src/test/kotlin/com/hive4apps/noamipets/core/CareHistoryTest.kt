package com.hive4apps.noamipets.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareHistoryTest {
    private val apoquel = Treatment(
        "a", "rex", "Apoquel", times = listOf("08:00", "20:00"), durationDays = 3, startDate = "2026-10-01",
    )
    private val now = LocalDateTime.parse("2026-10-02T12:00")

    @Test
    fun everyDoseDueSoFarNewestFirstGivenOrNot() {
        val done = mapOf("a|2026-10-01|08:00" to 1L, "a|2026-10-02|08:00" to 2L)
        val entries = CareHistory.entries(listOf(apoquel), done, now)
        assertEquals(
            listOf("2026-10-02T08:00", "2026-10-01T20:00", "2026-10-01T08:00"),
            entries.map { it.at.toString() },
        )
        assertEquals(listOf(true, false, true), entries.map { it.given })
        assertEquals(listOf(3, 2, 1), entries.map { it.number })
        assertEquals(6, entries.first().total)
    }

    @Test
    fun aGivenDoseNoLongerInThePlanStays() {
        // Validated at 07:00, then the time of the care was changed to 08:00.
        val done = mapOf("a|2026-10-01|07:00" to 5L)
        val entries = CareHistory.entries(listOf(apoquel), done, now)
        val moved = entries.single { it.at == LocalDateTime.parse("2026-10-01T07:00") }
        assertTrue(moved.given)
        assertNull(moved.number)
    }

    @Test
    fun removedCareOnlyKeepsWhatWasDueBeforeItsRemoval() {
        val removed = apoquel.copy(durationDays = null, stoppedOn = "2026-10-03", archivedOn = "2026-10-02")
        val done = mapOf("a|2026-10-01|08:00" to 1L)
        val entries = CareHistory.entries(listOf(removed), done, LocalDateTime.parse("2026-10-05T12:00"))
        assertEquals(listOf("2026-10-01T20:00", "2026-10-01T08:00"), entries.map { it.at.toString() })
    }

    @Test
    fun recordsOngoingFirstThenPastByEndDate() {
        val ongoing = Treatment("o", "rex", "Collyre", durationDays = null, startDate = "2026-09-01")
        val old = Treatment("old", "rex", "Shampooing", durationDays = 2, startDate = "2026-08-01")
        val removed = Treatment("r", "rex", "Metacam", durationDays = null, startDate = "2026-09-20", stoppedOn = "2026-09-25", archivedOn = "2026-09-24")
        val done = mapOf("old|2026-08-01|08:00" to 1L, "r|2026-09-21|08:00" to 1L)
        val records = CareHistory.records(listOf(ongoing, old, apoquel), listOf(removed), done, LocalDateTime.parse("2026-10-10T09:00"))
        assertEquals(listOf("o", "a", "r", "old"), records.map { it.treatment.id })
        assertEquals(listOf(true, false, false, false), records.map { it.ongoing })
        val r = records.first { it.treatment.id == "r" }
        assertTrue(r.archived)
        assertEquals(LocalDate.parse("2026-09-24"), r.to)
        assertEquals(1, r.given)
        assertEquals(4, r.due) // 20, 21, 22, 23 september
        val o = records.first { it.treatment.id == "old" }
        assertEquals(1 to 2, o.given to o.due)
    }
}
