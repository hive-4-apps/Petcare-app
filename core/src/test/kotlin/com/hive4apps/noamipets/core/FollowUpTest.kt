package com.hive4apps.noamipets.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowUpTest {
    private val rex = Pet("rex", "Rex", Species.DOG)
    private val minou = Pet("minou", "Minou", Species.CAT, closedOn = "2026-10-05")
    private val data = AppData(
        pets = listOf(rex, minou),
        treatments = listOf(
            Treatment("a", "rex", "Apoquel", startDate = "2026-10-01"),
            Treatment("b", "minou", "Metacam", startDate = "2026-10-01"),
        ),
    )

    @Test
    fun closedFollowUpHasNoCareFromItsClosingDay() {
        assertEquals(listOf("a", "b"), data.treatmentsFollowedOn(LocalDate.parse("2026-10-04")).map { it.id })
        assertEquals(listOf("a"), data.treatmentsFollowedOn(LocalDate.parse("2026-10-05")).map { it.id })
        assertEquals(listOf("a"), data.treatmentsFollowedOn(LocalDate.parse("2026-11-01")).map { it.id })
    }

    @Test
    fun withoutClosedFollowUpEverythingIsFollowed() {
        val open = data.copy(pets = listOf(rex, minou.copy(closedOn = null)))
        assertEquals(2, open.treatmentsFollowedOn(LocalDate.parse("2026-11-01")).size)
    }
}
