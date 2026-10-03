package com.hive4apps.petcare.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One dose to give, at a given time. [number] counts from 1; [total] is null for a continuous treatment. */
data class Dose(
    val treatment: Treatment,
    val at: LocalDateTime,
    val number: Int,
    val total: Int?,
) {
    val key: String get() = doseKey(treatment.id, at)
}

fun doseKey(treatmentId: String, at: LocalDateTime): String =
    "$treatmentId|${at.toLocalDate()}|${at.toLocalTime().format(HH_MM)}"

val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text.trim(), HH_MM) }.getOrNull()

object Schedule {
    /** Doses of the whole course (null for a continuous treatment). */
    fun totalDoses(t: Treatment): Int? {
        val days = t.durationDays ?: return null
        val every = t.everyDays.coerceAtLeast(1)
        return ((days + every - 1) / every) * t.sortedTimes().size
    }

    /** Doses falling between [from] and [to] (both included). */
    fun doses(t: Treatment, from: LocalDate, to: LocalDate): List<Dose> {
        val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: return emptyList()
        val times = t.sortedTimes()
        if (times.isEmpty() || to < start) return emptyList()
        val every = t.everyDays.coerceAtLeast(1)
        val total = totalDoses(t)
        val stop = t.stoppedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val firstTime = t.startTime?.let(::parseTime)
        val result = mutableListOf<Dose>()

        // Continuous treatments: jump straight to the first day needed instead of counting from the start.
        var day = start
        var count = 0
        if (total == null && from > start) {
            val cycles = ChronoUnit.DAYS.between(start, from) / every
            day = start.plusDays(cycles * every)
            val firstDayDoses = times.count { firstTime == null || it >= firstTime }
            count = if (cycles == 0L) 0 else firstDayDoses + ((cycles - 1) * times.size).toInt()
        }
        while (day <= to) {
            if (stop != null && day >= stop) break
            for (time in times) {
                if (day == start && firstTime != null && time < firstTime) continue
                if (total != null && count >= total) return result
                count++
                if (day >= from) result += Dose(t, day.atTime(time), count, total)
            }
            day = day.plusDays(every.toLong())
        }
        return result
    }

    fun dosesOn(t: Treatment, day: LocalDate): List<Dose> = doses(t, day, day)

    /** Day of the last dose (null for a continuous treatment). */
    fun lastDay(t: Treatment): LocalDate? {
        val total = totalDoses(t) ?: return null
        val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: return null
        val span = (t.durationDays ?: 0).toLong() + t.everyDays + 1
        val all = doses(t, start, start.plusDays(span))
        return all.getOrNull(total - 1)?.at?.toLocalDate() ?: all.lastOrNull()?.at?.toLocalDate()
    }

    fun isActiveOn(t: Treatment, day: LocalDate): Boolean {
        val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: return false
        if (day < start) return false
        if (t.stoppedOn != null && day >= LocalDate.parse(t.stoppedOn)) return false
        val last = lastDay(t) ?: return true
        return day <= last
    }

    /** True when every dose is behind (course finished or stopped). */
    fun isFinished(t: Treatment, today: LocalDate): Boolean {
        if (t.stoppedOn != null && LocalDate.parse(t.stoppedOn) <= today) return true
        val last = lastDay(t) ?: return false
        return last < today
    }

    /** All doses of the day for these treatments, by time. */
    fun dayPlan(treatments: List<Treatment>, day: LocalDate): List<Dose> =
        treatments.flatMap { dosesOn(it, day) }.sortedWith(compareBy({ it.at }, { it.treatment.name }))

    /** Next dose after [now] (strictly), among all treatments: when to ring next. */
    fun nextDoseAfter(treatments: List<Treatment>, now: LocalDateTime, done: Set<String>): Dose? {
        val maxGap = (treatments.maxOfOrNull { it.everyDays } ?: 1).coerceAtLeast(1)
        val from = now.toLocalDate()
        return treatments
            .flatMap { doses(it, from, from.plusDays(maxGap.toLong() + 1)) }
            .filter { it.at > now && it.key !in done }
            .minByOrNull { it.at }
    }

    /** Progress of the course so far. */
    fun progress(t: Treatment, done: Set<String>, today: LocalDate): Progress {
        val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull() ?: return Progress(0, 0, null)
        val until = lastDay(t) ?: today
        val doses = doses(t, start, if (until > today) until else today)
        val given = doses.count { it.key in done }
        return Progress(given, doses.count { it.at.toLocalDate() <= today }, totalDoses(t), doses.size)
    }
}

data class Progress(val given: Int, val dueSoFar: Int, val total: Int?, val planned: Int = total ?: 0)

fun Treatment.sortedTimes(): List<LocalTime> = times.mapNotNull(::parseTime).distinct().sorted()

/** Times of day for n doses a day, spaced evenly; a sensible default the user can change. */
fun defaultTimes(perDay: Int, hint: TimeHint? = null): List<String> = when (perDay.coerceIn(1, 6)) {
    1 -> listOf(
        when (hint) {
            TimeHint.EVENING -> "20:00"
            TimeHint.NOON -> "12:00"
            else -> "08:00"
        },
    )
    2 -> listOf("08:00", "20:00")
    3 -> listOf("07:00", "15:00", "23:00")
    4 -> listOf("06:00", "12:00", "18:00", "00:00")
    5 -> listOf("07:00", "11:00", "15:00", "19:00", "23:00")
    else -> listOf("06:00", "10:00", "14:00", "18:00", "22:00", "02:00")
}.sorted()

enum class TimeHint { MORNING, NOON, EVENING }
