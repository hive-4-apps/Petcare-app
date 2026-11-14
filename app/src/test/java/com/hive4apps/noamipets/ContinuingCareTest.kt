package com.hive4apps.noamipets

import com.hive4apps.noamipets.core.CareKind
import com.hive4apps.noamipets.core.ParsedCare
import com.hive4apps.noamipets.core.Treatment
import com.hive4apps.noamipets.ui.Draft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

/** "Manter banhos 15/15 dias" written by hand on a new prescription, for baths already followed in the app. */
@RunWith(RobolectricTestRunner::class)
class ContinuingCareTest {
    private val now = LocalDateTime.of(2026, 11, 4, 9, 0)
    private val baths = Treatment(
        id = "bath", petId = "suzi", name = "Shampoo Cloresten", kind = CareKind.SHAMPOO, times = listOf("18:00"),
        everyDays = 7, durationDays = 28, startDate = LocalDate.of(2026, 10, 30).toString(),
    )
    private val manter = ParsedCare(
        name = "Banhos", kind = CareKind.SHAMPOO, dose = null, perDay = 1, everyDays = 15, durationDays = null,
        continuous = true, timeHint = null, source = "*Manter banhos 15/15 dias", handwritten = true, continuesId = "bath",
    )

    @Test
    fun keepsTheTreatmentGoingFromItsNextDose() {
        val draft = Draft.continuing(manter, baths, now)
        assertEquals("Shampoo Cloresten", draft.name)
        assertEquals("bath", draft.continuesId)
        assertEquals(15, draft.everyDays)
        assertTrue(draft.continuous)
        // Same number of baths that day: the hour chosen before is kept.
        assertEquals(listOf("18:00"), draft.times)
        // Last bath on 30/10, next one was due on 06/11: no bath today.
        assertEquals(LocalDate.of(2026, 11, 6), draft.startDate)
        assertTrue(draft.handwritten)
    }

    @Test
    fun finishedTreatmentStartsAgainNow() {
        val old = baths.copy(startDate = LocalDate.of(2026, 8, 1).toString())
        val draft = Draft.continuing(manter, old, now)
        assertEquals(LocalDate.of(2026, 11, 4), draft.startDate)
    }
}
