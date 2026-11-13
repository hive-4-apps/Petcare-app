package com.hive4apps.noamipets.ui

import com.hive4apps.noamipets.core.CareKind
import com.hive4apps.noamipets.core.Dose
import com.hive4apps.noamipets.core.HH_MM
import com.hive4apps.noamipets.core.ParsedCare
import com.hive4apps.noamipets.core.Schedule
import com.hive4apps.noamipets.core.Treatment
import com.hive4apps.noamipets.core.defaultTimes
import com.hive4apps.noamipets.core.parseTime
import com.hive4apps.noamipets.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A treatment being checked or edited. [guessed] lists what was not found in the document (to check). */
data class Draft(
    val id: String = Store.newId(),
    val name: String = "",
    val kind: CareKind = CareKind.MEDICATION,
    val dose: String = "",
    val times: List<String> = listOf("08:00"),
    val everyDays: Int = 1,
    val durationDays: Int = 7,
    val continuous: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val startTime: String? = null,
    val notes: String = "",
    val include: Boolean = true,
    val guessed: Set<Field> = emptySet(),
    val source: String = "",
    val documentId: String? = null,
    /** Read from handwriting by the AI. */
    val handwritten: Boolean = false,
    /** Keeps going this treatment already followed ("Manter..."): it is stopped where this one starts. */
    val continuesId: String? = null,
    val continuesName: String? = null,
    /** Started before it was entered: the doses already behind are marked as given when saved. */
    val markPastGiven: Boolean = true,
) {
    enum class Field { FREQUENCY, DURATION, DOSE }

    fun toTreatment(petId: String, documentId: String?): Treatment = Treatment(
        id = id,
        petId = petId,
        name = name.trim(),
        kind = kind,
        dose = dose.trim(),
        times = times.mapNotNull(::parseTime).distinct().sorted().map { it.format(HH_MM) },
        everyDays = everyDays.coerceAtLeast(1),
        durationDays = if (continuous) null else durationDays.coerceAtLeast(1),
        startDate = startDate.toString(),
        startTime = startTime?.takeIf { it in times && it != times.minOrNull() },
        notes = notes.trim(),
        documentId = documentId ?: this.documentId,
    )

    /** Doses of this plan already behind [now] (a course started before it was entered in the app). */
    fun pastDoses(now: LocalDateTime): List<Dose> {
        if (name.isBlank() || startDate > now.toLocalDate()) return emptyList()
        return Schedule.doses(toTreatment("", null), startDate, now.toLocalDate()).filter { it.at <= now.minusMinutes(GRACE_MINUTES) }
    }

    /** The doses to record as given when saving (dose key -> time of the dose), if the user says so. */
    fun alreadyGiven(petId: String, now: LocalDateTime): Map<String, Long> {
        if (!markPastGiven) return emptyMap()
        return Schedule.doses(toTreatment(petId, null), startDate, now.toLocalDate())
            .filter { it.at <= now.minusMinutes(GRACE_MINUTES) }
            .associate { it.key to it.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    }

    /**
     * A start date chosen by the user, kept as is even when it is today or already past (a course started before
     * it was entered). Today, the first dose is the next time to come or, when every time is behind, the last one.
     */
    fun startingOn(day: LocalDate, now: LocalDateTime): Draft {
        if (day != now.toLocalDate()) return copy(startDate = day, startTime = null)
        val sorted = times.sorted()
        val limit = now.toLocalTime().minusMinutes(GRACE_MINUTES)
        val first = sorted.firstOrNull { (parseTime(it) ?: return@firstOrNull false) >= limit } ?: sorted.lastOrNull()
        return copy(startDate = day, startTime = first?.takeIf { it != sorted.first() })
    }

    /** Starting today: the first dose is the next time to come, not one already past. */
    fun startingFrom(now: LocalDateTime): Draft {
        if (startDate != now.toLocalDate()) return copy(startTime = null)
        val sorted = times.sorted()
        // 30 minutes of grace: a dose of 08:00 read at 08:20 is still for now.
        val next = sorted.firstOrNull { (parseTime(it) ?: return@firstOrNull false) >= now.toLocalTime().minusMinutes(GRACE_MINUTES) }
        return when {
            next == null -> copy(startDate = startDate.plusDays(1), startTime = null)
            next == sorted.first() -> copy(startTime = null)
            else -> copy(startTime = next)
        }
    }

    companion object {
        /** A dose up to 30 minutes late is still "to give now", not one already behind. */
        const val GRACE_MINUTES = 30L

        fun fromParsed(p: ParsedCare, now: LocalDateTime): Draft {
            val guessed = buildSet {
                if (p.perDay == null && p.everyDays == null) add(Field.FREQUENCY)
                if (p.durationDays == null && !p.continuous) add(Field.DURATION)
                if (p.dose == null) add(Field.DOSE)
            }
            val defaultDuration = if (p.kind == CareKind.SHAMPOO) 28 else 7
            return Draft(
                name = p.name,
                kind = p.kind,
                dose = p.dose.orEmpty(),
                times = defaultTimes(p.perDay ?: 1, p.timeHint).let {
                    // A bath is given in the day, not at 08:00 sharp.
                    if (p.kind == CareKind.SHAMPOO && p.perDay == null) listOf("10:00") else it
                },
                everyDays = p.everyDays ?: 1,
                durationDays = p.durationDays ?: defaultDuration,
                continuous = p.continuous && p.durationDays == null,
                guessed = guessed,
                source = p.source,
                handwritten = p.handwritten,
            ).startingFrom(now)
        }

        /**
         * A care the prescription says to keep ("Manter banhos 15/15 dias") for a treatment already followed: same
         * name and kind, the frequency and dose of the prescription (or the old ones when it gives none), and the
         * next dose falls when the old treatment would have given it (no extra bath today if the last was 5 days ago).
         */
        fun continuing(p: ParsedCare, old: Treatment, now: LocalDateTime): Draft {
            val read = fromParsed(p, now)
            val sameFrequency = p.perDay == null && p.everyDays == null
            val draft = read.copy(
                name = old.name,
                kind = old.kind,
                dose = p.dose ?: old.dose,
                // Same number of doses a day: keep the hours the user already chose.
                times = if (sameFrequency || (p.perDay ?: 1) == old.times.size) old.times else read.times,
                everyDays = if (sameFrequency) old.everyDays else read.everyDays,
                notes = old.notes,
                guessed = read.guessed.filterNot { it == Field.FREQUENCY || it == Field.DOSE && old.dose.isNotBlank() }.toSet(),
                continuesId = old.id,
                continuesName = old.name,
            )
            val next = if (Schedule.isFinished(old, now.toLocalDate())) {
                null
            } else {
                Schedule.doses(old, now.toLocalDate(), now.toLocalDate().plusDays(old.everyDays.toLong() + 1))
                    .firstOrNull { it.at >= now.minusMinutes(30) }
            }
            return draft.copy(startDate = next?.at?.toLocalDate() ?: now.toLocalDate(), startTime = null).startingFrom(now)
        }

        fun fromTreatment(t: Treatment): Draft = Draft(
            id = t.id,
            name = t.name,
            kind = t.kind,
            dose = t.dose,
            times = t.times,
            everyDays = t.everyDays,
            durationDays = t.durationDays ?: 7,
            continuous = t.durationDays == null,
            startDate = LocalDate.parse(t.startDate),
            startTime = t.startTime,
            notes = t.notes,
            documentId = t.documentId,
            // Editing a care: doses left unchecked stay as they were unless the user asks.
            markPastGiven = false,
        )
    }
}
