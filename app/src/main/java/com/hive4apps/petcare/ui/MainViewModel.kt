package com.hive4apps.petcare.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hive4apps.petcare.core.Pet
import com.hive4apps.petcare.core.PrescriptionParser
import com.hive4apps.petcare.core.Species
import com.hive4apps.petcare.core.VetDocument
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

    fun readDocument() {
        val f = flow ?: return
        f.reading = true
        f.error = null
        viewModelScope.launch {
            runCatching { TextReader.read(getApplication(), f.pages.toList()) }
                .onSuccess { text ->
                    f.text = text
                    f.drafts.clear()
                    f.drafts += PrescriptionParser.parse(text).map { Draft.fromParsed(it, LocalDateTime.now()) }
                    f.step = NewCareFlow.Step.REVIEW
                }
                .onFailure {
                    CrashLog.record(getApplication(), "readDocument", it)
                    f.error = it.message ?: it.javaClass.simpleName
                }
            f.reading = false
        }
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
        val treatments = f.drafts.filter { it.include && it.name.isNotBlank() }
            .map { it.toTreatment(petId, document?.id) }
        store.saveTreatments(treatments, document)
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
