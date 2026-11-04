package com.hive4apps.petcare.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hive4apps.petcare.core.ExistingCare
import com.hive4apps.petcare.core.ParsedCare
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.PrescriptionParser
import com.hive4apps.petcare.core.Schedule
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.core.Treatment
import com.hive4apps.petcare.core.VetDocument
import com.hive4apps.petcare.data.AiReader
import com.hive4apps.petcare.data.CrashLog
import com.hive4apps.petcare.data.Store
import com.hive4apps.petcare.data.TextReader
import com.hive4apps.petcare.reminders.Reminders
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Screen {
    data object Home : Screen
    data class PetDetail(val petId: String) : Screen
    data class EditTreatment(val treatmentId: String?, val petId: String) : Screen
    data class Document(val documentId: String) : Screen
    data object NewCare : Screen
}

enum class Tab { TODAY, PETS, SETTINGS }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store.get(app)
    val data = store.data

    var today by mutableStateOf(LocalDate.now())
        private set
    var now by mutableStateOf(LocalDateTime.now())
        private set
    var canNotify by mutableStateOf(Reminders.canNotify(app))
        private set
    var canRingOnTime by mutableStateOf(Reminders.canRingOnTime(app))
        private set

    var tab by mutableStateOf(Tab.TODAY)
    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    /** The "new follow-up" being filled in (pet -> photos -> checking what was read). */
    var flow by mutableStateOf<NewCareFlow?>(null)
        private set

    fun onResume() {
        today = LocalDate.now()
        now = LocalDateTime.now()
        refreshPermissions()
        Reminders.reschedule(getApplication())
    }

    fun tick() {
        now = LocalDateTime.now()
        if (now.toLocalDate() != today) today = now.toLocalDate()
    }

    fun refreshPermissions() {
        canNotify = Reminders.canNotify(getApplication())
        canRingOnTime = Reminders.canRingOnTime(getApplication())
    }

    fun open(screen: Screen) {
        backStack += screen
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        if (backStack.last() == Screen.NewCare && flow?.previousStep() == true) return true
        val leaving = backStack.removeAt(backStack.lastIndex)
        if (leaving == Screen.NewCare) flow?.discard()
        if (leaving == Screen.NewCare) flow = null
        return true
    }

    /** Back to [screen] (or home), dropping what was above. */
    fun backTo(screen: Screen) {
        while (backStack.size > 1 && backStack.last() != screen) backStack.removeAt(backStack.lastIndex)
        if (backStack.last() != screen) backStack += screen
    }

    fun startNewCare(petId: String? = null) {
        flow = NewCareFlow(store, petId)
        open(Screen.NewCare)
    }

    /** Anthropic API key entered in Settings: when set, documents are read by the AI (handwriting included). */
    var aiKeySet by mutableStateOf(AiReader.apiKey(app) != null)
        private set

    fun setAiKey(key: String?) {
        AiReader.setApiKey(getApplication(), key)
        aiKeySet = AiReader.apiKey(getApplication()) != null
    }

    fun readDocument() {
        val f = flow ?: return
        f.reading = true
        f.error = null
        f.aiError = null
        f.readByAi = false
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val key = AiReader.apiKey(getApplication())
            // The AI first (reads handwriting); without key, or when it fails (no network...), the phone's reading.
            val ai = if (key == null) {
                null
            } else {
                val existing = petTreatments(f)
                runCatching { AiReader.read(key, f.pages.toList(), existing.map { it.describe() }) }
                    .onFailure {
                        CrashLog.record(getApplication(), "readDocument AI", it)
                        f.aiError = it.message ?: it.javaClass.simpleName
                    }
                    .getOrNull()
                    ?.let { reading -> reading to existing }
            }
            if (ai != null) {
                val (reading, existing) = ai
                f.text = reading.transcription
                f.drafts.clear()
                f.drafts += draftsFrom(reading.cares, existing, now)
                f.readByAi = true
                f.step = NewCareFlow.Step.REVIEW
            } else {
                runCatching { TextReader.read(getApplication(), f.pages.toList()) }
                    .onSuccess { text ->
                        f.text = text
                        f.drafts.clear()
                        f.drafts += draftsFrom(PrescriptionParser.parse(text), petTreatments(f), now)
                        f.step = NewCareFlow.Step.REVIEW
                    }
                    .onFailure {
                        CrashLog.record(getApplication(), "readDocument", it)
                        f.error = it.message ?: it.javaClass.simpleName
                    }
            }
            f.reading = false
        }
    }

    /**
     * Text of the document read elsewhere and pasted (Google Lens, a chat app...): free, and handwriting included
     * when that tool reads it. Understood offline, like the phone's own reading.
     */
    fun readPastedText(text: String) {
        val f = flow ?: return
        f.error = null
        f.aiError = null
        f.readByAi = false
        f.text = text.trim()
        f.drafts.clear()
        f.drafts += draftsFrom(PrescriptionParser.parse(f.text), petTreatments(f), LocalDateTime.now())
        f.step = NewCareFlow.Step.REVIEW
    }

    private fun petTreatments(f: NewCareFlow): List<Treatment> =
        f.petId?.let { id -> data.value.treatments.filter { it.petId == id } }.orEmpty()

    /** "Manter..." lines continue the pet's treatment they name (the AI's match first, else by name or kind). */
    private fun draftsFrom(cares: List<ParsedCare>, existing: List<Treatment>, now: LocalDateTime): List<Draft> = cares.map { care ->
        val old = existing.find { it.id == care.continuesId } ?: PrescriptionParser.continuedTreatment(care, existing)
        if (old != null) Draft.continuing(care, old, now) else Draft.fromParsed(care, now)
    }

    /** How a treatment already followed is shown to the AI, so it can match "Manter..." lines with it. */
    private fun Treatment.describe(): ExistingCare {
        val status = when {
            stoppedOn != null -> "stopped on $stoppedOn"
            Schedule.isFinished(this, today) -> "finished on ${Schedule.lastDay(this)}"
            else -> "ongoing"
        }
        val frequency = "${times.size} time(s) a day" + if (everyDays > 1) ", every $everyDays days" else ""
        val duration = durationDays?.let { "for $it days" } ?: "continuous"
        return listOf(kind.name.lowercase(), dose.ifBlank { null }, frequency, duration, "since $startDate", status)
            .filterNotNull().joinToString(", ")
            .let { ExistingCare(id, name, it) }
    }

    /** Saves the pet (if new), the document and the checked treatments; opens the pet's page. */
    fun finishNewCare() {
        val f = flow ?: return
        val petId = f.petId ?: Store.newId().also { id ->
            store.addPet(Pet(id, f.newPetName.trim(), f.newPetSpecies, createdAt = System.currentTimeMillis()))
        }
        val document = if (f.pages.isNotEmpty()) {
            VetDocument(f.documentId, petId, System.currentTimeMillis(), f.pages.map { it.absolutePath }, f.text)
        } else {
            null
        }
        val kept = f.drafts.filter { it.include && it.name.isNotBlank() }
        val treatments = kept.map { it.toTreatment(petId, document?.id) }
        // "Manter...": the old treatment stops where its continuation starts (its history stays as it was).
        val continued = kept.mapNotNull { draft ->
            val old = data.value.treatments.find { it.id == draft.continuesId } ?: return@mapNotNull null
            if (old.stoppedOn != null || Schedule.isFinished(old, today)) null else old.copy(stoppedOn = draft.startDate.toString())
        }
        store.saveTreatments(continued + treatments, document)
        f.saved = true
        flow = null
        backStack.clear()
        backStack += Screen.Home
        tab = Tab.PETS
        backStack += Screen.PetDetail(petId)
    }
}

class NewCareFlow(private val store: Store, private val initialPetId: String?) {
    enum class Step { PET, DOCUMENT, REVIEW }

    var step by mutableStateOf(if (initialPetId != null) Step.DOCUMENT else Step.PET)
    var petId by mutableStateOf(initialPetId)
    var newPetName by mutableStateOf("")
    var newPetSpecies by mutableStateOf(Species.DOG)
    val pages = mutableStateListOf<File>()
    var reading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var text by mutableStateOf("")
    /** Read by the AI (Claude) rather than by the phone. */
    var readByAi by mutableStateOf(false)
    /** Why the AI could not read (the phone's reading was used instead). */
    var aiError by mutableStateOf<String?>(null)
    val drafts = mutableStateListOf<Draft>()
    val documentId: String = Store.newId()
    var saved = false

    /** Back inside the steps; false when leaving the flow. */
    fun previousStep(): Boolean {
        step = when {
            reading -> return true
            step == Step.REVIEW -> Step.DOCUMENT
            step == Step.DOCUMENT && initialPetId == null -> Step.PET
            else -> return false
        }
        return true
    }

    private var photoCount = 0

    fun newPhotoFile(): File = File(store.docsDir, "${documentId}_${System.currentTimeMillis()}_${photoCount++}.jpg")

    /** Leaving without saving: the photos taken are not kept. */
    fun discard() {
        if (!saved) pages.forEach { it.delete() }
    }
}
