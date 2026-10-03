package com.hive4apps.petcare.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTest {
    private fun t(
        times: List<String> = listOf("08:00", "20:00"),
        everyDays: Int = 1,
        duration: Int? = 7,
        start: String = "2026-10-01",
        startTime: String? = null,
    ) = Treatment("t", "p", "Apoquel", times = times, everyDays = everyDays, durationDays = duration, startDate = start, startTime = startTime)

    @Test
    fun twiceADayForAWeek() {
        val tr = t()
        assertEquals(14, Schedule.totalDoses(tr))
        val all = Schedule.doses(tr, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-01"))
        assertEquals(14, all.size)
        assertEquals(LocalDateTime.parse("2026-10-07T20:00"), all.last().at)
        assertEquals(LocalDate.parse("2026-10-07"), Schedule.lastDay(tr))
        assertEquals(14, all.last().number)
    }

    @Test
    fun startedInTheEveningEndsInTheMorningAfter() {
        val tr = t(startTime = "20:00")
        val all = Schedule.doses(tr, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-30"))
        assertEquals(14, all.size)
        assertEquals(LocalDateTime.parse("2026-10-01T20:00"), all.first().at)
        assertEquals(LocalDateTime.parse("2026-10-08T08:00"), all.last().at)
    }

    @Test
    fun shampooEveryThreeDaysForFourWeeks() {
        val tr = t(times = listOf("10:00"), everyDays = 3, duration = 28)
        assertEquals(10, Schedule.totalDoses(tr))
        val all = Schedule.doses(tr, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-30"))
        assertEquals(10, all.size)
        assertEquals(LocalDate.parse("2026-10-28"), all.last().at.toLocalDate())
        assertEquals(1, Schedule.dosesOn(tr, LocalDate.parse("2026-10-04")).size)
        assertEquals(0, Schedule.dosesOn(tr, LocalDate.parse("2026-10-05")).size)
    }

    @Test
    fun continuousTreatmentNumbersDosesFromTheStart() {
        val tr = t(times = listOf("08:00"), duration = null, startTime = "09:00", everyDays = 2)
        assertNull(Schedule.totalDoses(tr))
        // Start day skipped (09:00 > 08:00); doses on 10-03, 10-05, ...
        val doses = Schedule.dosesOn(tr, LocalDate.parse("2026-10-07"))
        assertEquals(1, doses.size)
        assertEquals(3, doses.first().number)
        val viaLoop = Schedule.doses(tr, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")).last()
        assertEquals(viaLoop.number, doses.first().number)
    }

    @Test
    fun stoppedTreatment() {
        val tr = t().copy(stoppedOn = "2026-10-03")
        assertEquals(4, Schedule.doses(tr, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-30")).size)
    }

    @Test
    fun nextDose() {
        val tr = t()
        val now = LocalDateTime.parse("2026-10-02T09:00")
        assertEquals(LocalDateTime.parse("2026-10-02T20:00"), Schedule.nextDoseAfter(listOf(tr), now, emptySet())?.at)
        val done = setOf(doseKey("t", LocalDateTime.parse("2026-10-02T20:00")))
        assertEquals(LocalDateTime.parse("2026-10-03T08:00"), Schedule.nextDoseAfter(listOf(tr), now, done)?.at)
        assertNull(Schedule.nextDoseAfter(listOf(tr), LocalDateTime.parse("2026-10-08T00:00"), emptySet()))
    }
}
