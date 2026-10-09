package com.hive4apps.petcare

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hive4apps.petcare.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Goes through the app like a user: new pet, a treatment typed by hand, the plan, a dose validated. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-rFR-w411dp-h891dp")
class AppFlowTest {
    init {
        Store.resetForTests()
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun newFollowUpByHand() {
        rule.onNodeWithText("Aucun suivi pour l'instant").assertIsDisplayed()
        rule.onAllNodes(hasText("Nouveau suivi")).onFirst().performClick()

        // 1. New pet.
        rule.onNodeWithText("Pour quel animal ?").assertIsDisplayed()
        rule.onNodeWithText("🐱  Chat").performClick()
        rule.onNodeWithText("Nom de l'animal").performTextInput("Minou")
        rule.onNodeWithText("Continuer").performClick()

        // 2. No photo: typed by hand.
        rule.onNodeWithText("Documents du vétérinaire").assertIsDisplayed()
        rule.onNodeWithText("Saisir les soins à la main").performClick()

        // 3. One treatment.
        rule.onNodeWithText("Vérifiez le plan de soins").assertIsDisplayed()
        rule.onNodeWithText("Médicament ou soin").performTextInput("Metacam")
        rule.onNodeWithText("Créer le plan (1 soin)").performClick()

        // The pet's page, with the plan.
        rule.waitForIdle()
        rule.onNodeWithText("Minou").assertIsDisplayed()
        rule.onNodeWithText("Metacam").assertIsDisplayed()
        rule.onNodeWithText("Plan complet").performClick()
        rule.onNodeWithText("7 jours de soins · 7 prises").assertIsDisplayed()

        // Validate the first dose.
        rule.onAllNodesWithContentDescription("Marquer comme fait").onFirst().performClick()
        rule.waitForIdle()
        val data = Store.get(rule.activity).data.value
        assertEquals(1, data.pets.size)
        assertEquals("Metacam", data.treatments.single().name)
        assertEquals(1, data.done.size)
        assertTrue(data.done.keys.single().startsWith(data.treatments.single().id))

        // Share the first day of the plan: the text says the dose is done, and it can be copied.
        rule.onAllNodesWithContentDescription("Partager la journée").onFirst().performClick()
        rule.onNodeWithText("Copier le texte").performClick()
        val clipboard = rule.activity.getSystemService(android.content.ClipboardManager::class.java)
        val shared = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(shared, shared.startsWith("🐾 Soins du "))
        assertTrue(shared, shared.contains("• 🐱 Minou : Metacam (prise 1/7) ✓ fait à "))

        // Back home: the day's plan.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Aujourd'hui", useUnmergedTree = true)
    }
}
