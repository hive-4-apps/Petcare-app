package com.hive4apps.noamipets.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One dose that was due: given (with when it was validated) or not. [number] is null when no longer in the plan. */
data class HistoryEntry(
    val treatment: Treatment,
    val at: LocalDateTime,
    val number: Int?,
    val total: Int?,
    /** When it was validated (epoch millis); null if never checked. */
    val givenAt: Long?,
) {
    val given: Boolean get() = givenAt != null
}

/** One care as it went: period, doses given out of those due, and whether it is still followed. */
data class CareRecord(
    val treatment: Treatment,
    val from: LocalDate?,
    /** Last day of doses (null: continuous and still going). */
    val to: LocalDate?,
    val given: Int,
    val due: Int,
    val ongoing: Boolean,
    /** Removed from the follow-up: only in the history. */
    val archived: Boolean,
)

/** The care history of a pet: what was given, when, and what was missed, current and removed cares alike. */
object CareHistory {
    /**
     * Every dose due up to [now] for these cares, newest first. A dose validated but no longer in the plan
     * (times changed afterwards) is kept too: what was given is never lost.
     */
    fun entries(treatments: List<Treatment>, done: Map<String, Long>, now: LocalDateTime): List<HistoryEntry> {
        val today = now.toLocalDate()
        val result = mutableListOf<HistoryEntry>()
        for (t in treatments) {
            val start = runCatching { LocalDate.parse(t.startDate) }.getOrNull()
            val planned = if (start == null || start > today) {
                emptyList()
            } else {
                Schedule.doses(t, start, today).filter { it.at <= now }
            }
            // Removed from the follow-up: the doses not given from that day on were no longer expected.
            val archivedOn = t.archivedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val keys = HashSet<String>()
            planned.forEach { dose ->
                keys += dose.key
                val givenAt = done[dose.key]
                if (givenAt != null || archivedOn == null || dose.at.toLocalDate() < archivedOn) {
                    result += HistoryEntry(t, dose.at, dose.number, dose.total, givenAt)
                }
            }
            val prefix = t.id + "|"
            done.forEach { (key, givenAt) ->
                if (key.startsWith(prefix) && key !in keys) {
                    parseDoseKey(key)?.let { at -> result += HistoryEntry(t, at, null, null, givenAt) }
                }
            }
        }
        return result.sortedWith(compareByDescending<HistoryEntry> { it.at }.thenBy { it.treatment.name })
    }

    /** Each care, the ongoing ones first, then the past ones from the most recently ended. */
    fun records(
        current: List<Treatment>,
        archived: List<Treatment>,
        done: Map<String, Long>,
        now: LocalDateTime,
        followUpClosed: Boolean = false,
    ): List<CareRecord> {
        val today = now.toLocalDate()
        fun record(t: Treatment, isArchived: Boolean): CareRecord {
            val past = entries(listOf(t), done, now)
            val stop = t.stoppedOn?.let { runCatching { LocalDate.parse(it).minusDays(1) }.getOrNull() }
            val last = Schedule.lastDay(t)
            val to = listOfNotNull(stop, last).minOrNull()
            return CareRecord(
                treatment = t,
                from = runCatching { LocalDate.parse(t.startDate) }.getOrNull(),
                to = to,
                given = past.count { it.given },
                due = past.size,
                ongoing = !isArchived && !followUpClosed && !Schedule.isFinished(t, today),
                archived = isArchived,
            )
        }
        val all = current.map { record(it, false) } + archived.map { record(it, true) }
        val (ongoing, past) = all.partition { it.ongoing }
        return ongoing.sortedBy { it.from } +
            past.sortedWith(compareByDescending<CareRecord> { it.to ?: it.from }.thenByDescending { it.from })
    }

    /** "id|2026-10-09|08:00" -> the time of the dose. */
    fun parseDoseKey(key: String): LocalDateTime? {
        val parts = key.split('|')
        if (parts.size != 3) return null
        val day = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(parts[2], HH_MM) }.getOrNull() ?: return null
        return day.atTime(time)
    }
}
