package com.hive4apps.noamipets.core

import kotlinx.serialization.Serializable

@Serializable
enum class Species { DOG, CAT }

/** What is given: decides the icon and the words of the reminder ("give", "apply", "bath"...). */
@Serializable
enum class CareKind { MEDICATION, SHAMPOO, EYE_DROPS, EAR_DROPS, TOPICAL, INJECTION, ANTIPARASITIC, OTHER }

@Serializable
data class Pet(
    val id: String,
    val name: String,
    val species: Species,
    val photoPath: String? = null,
    val createdAt: Long = 0,
    /** Follow-up closed by the user (ISO date): no more reminders, shown apart; null while followed. */
    val closedOn: String? = null,
)

/**
 * One line of the vet's prescription, turned into a schedule.
 *
 * Doses fall on [times] every [everyDays] days from [startDate], starting at [startTime] on the first day.
 * With a [durationDays], the treatment gives as many doses as the full days would (a course started in the
 * evening ends in the morning after the last day); without, it goes on until stopped.
 */
@Serializable
data class Treatment(
    val id: String,
    val petId: String,
    val name: String,
    val kind: CareKind = CareKind.MEDICATION,
    /** "1/2 comprimé", "3 gouttes", "5 ml"... as written by the vet. */
    val dose: String = "",
    /** Times of day, "HH:mm", sorted. */
    val times: List<String> = listOf("08:00"),
    val everyDays: Int = 1,
    /** null = continuous, until stopped. */
    val durationDays: Int? = 7,
    /** ISO date of the first day. */
    val startDate: String,
    /** First dose on the first day ("HH:mm"); earlier times that day are skipped. */
    val startTime: String? = null,
    val notes: String = "",
    val documentId: String? = null,
    /** Stopped by the user before the end (ISO date, no dose from that day on). */
    val stoppedOn: String? = null,
)

/** Photos of a vet document and the text read from them. */
@Serializable
data class VetDocument(
    val id: String,
    val petId: String,
    val createdAt: Long,
    val imagePaths: List<String>,
    val text: String = "",
)

@Serializable
data class AppData(
    val pets: List<Pet> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val documents: List<VetDocument> = emptyList(),
    /** Dose key -> when it was validated (epoch millis). */
    val done: Map<String, Long> = emptyMap(),
    /** Minutes before a dose when the reminder rings (0 = on time). */
    val reminderAdvanceMinutes: Int = 0,
    val version: Int = 1,
) {
    /** Treatments still followed on [day]: those of pets whose follow-up was not closed on or before it. */
    fun treatmentsFollowedOn(day: java.time.LocalDate): List<Treatment> {
        val closed = pets.mapNotNull { p -> p.closedOn?.let { p.id to it } }.toMap()
        if (closed.isEmpty()) return treatments
        return treatments.filter { t ->
            val on = closed[t.petId] ?: return@filter true
            val closedDay = runCatching { java.time.LocalDate.parse(on) }.getOrNull() ?: return@filter false
            day < closedDay
        }
    }
}
