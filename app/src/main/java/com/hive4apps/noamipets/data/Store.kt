package com.hive4apps.noamipets.data

import android.content.Context
import com.hive4apps.noamipets.core.AppData
import com.hive4apps.noamipets.core.Pet
import com.hive4apps.noamipets.core.Schedule
import com.hive4apps.noamipets.core.Treatment
import com.hive4apps.noamipets.core.VetDocument
import com.hive4apps.noamipets.reminders.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Everything the user typed, in one JSON file of the app's private storage (kept by Android when the app is
 * updated over itself, and by the Google backup). Photos of the documents sit next to it in docs/.
 */
class Store private constructor(private val context: Context) {
    private val file = File(context.filesDir, FILE)
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    val docsDir: File get() = File(context.filesDir, "docs").apply { mkdirs() }

    /** Photos of the pets (each one replaced, never kept twice). */
    val petPhotosDir: File get() = File(context.filesDir, "pets").apply { mkdirs() }

    fun newPetPhotoFile(petId: String): File = File(petPhotosDir, "${petId}_${System.currentTimeMillis()}.jpg")

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        if (next == _data.value) return
        save(next)
        _data.value = next
        Reminders.reschedule(context)
    }

    // ---- Pets ----

    fun addPet(pet: Pet) = update { it.copy(pets = it.pets + pet) }

    /** A photo replaced or removed: the old file goes. */
    fun updatePet(pet: Pet) = update { d ->
        d.pets.find { it.id == pet.id }?.photoPath?.takeIf { it != pet.photoPath }?.let { File(it).delete() }
        d.copy(pets = d.pets.map { if (it.id == pet.id) pet else it })
    }

    fun deletePet(petId: String) = update { d ->
        val treatmentIds = (d.treatments + d.history).filter { it.petId == petId }.map { it.id }.toSet()
        d.documents.filter { it.petId == petId }.forEach { doc -> doc.imagePaths.forEach { File(it).delete() } }
        d.pets.find { it.id == petId }?.photoPath?.let { File(it).delete() }
        d.copy(
            pets = d.pets.filterNot { it.id == petId },
            treatments = d.treatments.filterNot { it.petId == petId },
            history = d.history.filterNot { it.petId == petId },
            documents = d.documents.filterNot { it.petId == petId },
            done = d.done.filterKeys { key -> key.substringBefore('|') !in treatmentIds },
        )
    }

    /**
     * Closes the pet's follow-up on [today]: no more reminders from now on, every ongoing care stops from
     * tomorrow (doses already given stay in the history) and the pet is shown with the closed follow-ups.
     */
    fun closeFollowUp(petId: String, today: LocalDate) = update { d ->
        val stop = today.plusDays(1).toString()
        d.copy(
            pets = d.pets.map { if (it.id == petId) it.copy(closedOn = today.toString()) else it },
            treatments = d.treatments.map { t ->
                if (t.petId == petId && !Schedule.isFinished(t, today) && (t.stoppedOn?.let { it > stop } ?: true)) {
                    t.copy(stoppedOn = stop)
                } else {
                    t
                }
            },
        )
    }

    /** Reopens a closed follow-up: the cares stopped by the closing go on again. */
    fun reopenFollowUp(petId: String) = update { d ->
        val pet = d.pets.find { it.id == petId } ?: return@update d
        val closedOn = pet.closedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@update d
        val stoppedByClosing = closedOn.plusDays(1).toString()
        d.copy(
            pets = d.pets.map { if (it.id == petId) it.copy(closedOn = null) else it },
            treatments = d.treatments.map { t ->
                if (t.petId == petId && t.stoppedOn == stoppedByClosing) t.copy(stoppedOn = null) else t
            },
        )
    }

    /**
     * Starts the pet's follow-up again from scratch but keeps its history: every care leaves the follow-up
     * (ongoing ones stop from tomorrow) and moves to the history with the doses given. The prescriptions stay
     * in the pet's documents.
     */
    fun deleteFollowUp(petId: String, today: LocalDate) = update { d ->
        val (removed, kept) = d.treatments.partition { it.petId == petId }
        d.copy(
            pets = d.pets.map { if (it.id == petId) it.copy(closedOn = null) else it },
            treatments = kept,
            history = d.history + removed.map { it.archived(today) },
        )
    }

    /** Erases the pet's history for good: the cares removed from its follow-up and their doses. */
    fun clearHistory(petId: String) = update { d ->
        val ids = d.history.filter { it.petId == petId }.map { it.id }.toSet()
        d.copy(
            history = d.history.filterNot { it.id in ids },
            done = d.done.filterKeys { it.substringBefore('|') !in ids },
        )
    }

    /** Erases one care of the history for good, with its doses. */
    fun deleteFromHistory(id: String) = update { d ->
        d.copy(
            history = d.history.filterNot { it.id == id },
            done = d.done.filterKeys { it.substringBefore('|') != id },
        )
    }

    private fun Treatment.archived(today: LocalDate): Treatment {
        val stop = today.plusDays(1).toString()
        val stopped = if (!Schedule.isFinished(this, today) && (stoppedOn?.let { it > stop } ?: true)) copy(stoppedOn = stop) else this
        return stopped.copy(archivedOn = today.toString())
    }

    // ---- Treatments ----

    /** A new care for a pet whose follow-up was closed reopens it (the old cares stay stopped). */
    fun saveTreatments(
        treatments: List<Treatment>,
        document: VetDocument?,
        /** Doses already given before the care was entered (dose key -> when given). */
        alreadyGiven: Map<String, Long> = emptyMap(),
    ) = update { d ->
        val ids = treatments.map { it.id }.toSet()
        val existing = d.treatments.map { it.id }.toSet()
        val reopened = treatments.filter { it.id !in existing }.map { it.petId }.toSet()
        d.copy(
            done = d.done + alreadyGiven.filterKeys { it !in d.done },
            pets = if (reopened.isEmpty()) d.pets else d.pets.map { if (it.id in reopened) it.copy(closedOn = null) else it },
            treatments = d.treatments.filterNot { it.id in ids } + treatments,
            documents = if (document != null) d.documents.filterNot { it.id == document.id } + document else d.documents,
        )
    }

    /**
     * Deletes a care. Once a dose of it was given, it goes to the pet's history (with those doses) instead of
     * vanishing; a care never given (entered by mistake) is erased.
     */
    fun deleteTreatment(id: String, today: LocalDate) = update { d ->
        val t = d.treatments.find { it.id == id } ?: return@update d
        val given = d.done.keys.any { it.substringBefore('|') == id }
        if (given) {
            d.copy(treatments = d.treatments.filterNot { it.id == id }, history = d.history + t.archived(today))
        } else {
            d.copy(
                treatments = d.treatments.filterNot { it.id == id },
                done = d.done.filterKeys { it.substringBefore('|') != id },
            )
        }
    }

    fun deleteDocument(id: String) = update { d ->
        d.documents.find { it.id == id }?.imagePaths?.forEach { File(it).delete() }
        d.copy(
            documents = d.documents.filterNot { it.id == id },
            treatments = d.treatments.map { if (it.documentId == id) it.copy(documentId = null) else it },
        )
    }

    // ---- Doses ----

    fun setDone(doseKey: String, done: Boolean) = update { d ->
        d.copy(done = if (done) d.done + (doseKey to System.currentTimeMillis()) else d.done - doseKey)
    }

    fun isDone(doseKey: String): Boolean = doseKey in _data.value.done

    fun setReminderAdvance(minutes: Int) = update { it.copy(reminderAdvanceMinutes = minutes) }

    // ---- Backup ----

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _data.value)

    /** Replaces everything with a backup (photos of documents are not in it: their paths are dropped). */
    fun importJson(text: String) {
        val imported = json.decodeFromString(AppData.serializer(), text)
        update {
            imported.copy(
                pets = imported.pets.map { p -> if (p.photoPath?.let { !File(it).exists() } == true) p.copy(photoPath = null) else p },
                documents = imported.documents.map { doc -> doc.copy(imagePaths = doc.imagePaths.filter { File(it).exists() }) },
            )
        }
    }

    private fun load(): AppData {
        for (candidate in listOf(file, File(context.filesDir, "$FILE.bak"))) {
            if (!candidate.exists()) continue
            runCatching { return json.decodeFromString(AppData.serializer(), candidate.readText()) }
                .onFailure { CrashLog.record(context, "Store.load ${candidate.name}", it) }
        }
        return AppData()
    }

    /** Written to a temporary file then renamed: a crash in the middle never leaves a half-written file. */
    private fun save(data: AppData) {
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        if (file.exists()) file.copyTo(File(context.filesDir, "$FILE.bak"), overwrite = true)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        // Name kept from the PetCare era: the data already on the phone is in this file.
        private const val FILE = "petcare.json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

        @Volatile private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }

        fun newId(): String = UUID.randomUUID().toString()

        /** Tests: each one starts with a new app and empty storage. */
        @androidx.annotation.VisibleForTesting
        fun resetForTests() {
            instance = null
        }
    }
}
