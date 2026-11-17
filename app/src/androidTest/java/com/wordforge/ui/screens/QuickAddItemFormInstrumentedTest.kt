package com.wordforge.ui.screens

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordforge.data.LearningItemDraft
import com.wordforge.data.LearningItemType
import com.wordforge.data.Word
import com.wordforge.ui.theme.WordForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAddItemFormInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun duplicateWordCannotBeSubmittedAgainWithIme() {
        val submitted = mutableListOf<LearningItemDraft>()
        composeRule.setContent {
            WordForgeTheme {
                QuickAddItemForm(onSubmit = { submitted.add(it) }, onNavigateBack = {})
            }
        }

        repeat(2) {
            composeRule.onNodeWithTag("quick_add_term").performTextInput("serendipity")
            composeRule.onNodeWithTag("quick_add_meaning").performTextInput("a happy accident")
            composeRule.onNodeWithTag("quick_add_meaning").performImeAction()
        }

        composeRule.runOnIdle { assertEquals(1, submitted.size) }
        composeRule.onNodeWithText("This word was already added in this session").assertExists()
        composeRule.onNodeWithText("a happy accident").assertExists()
    }

    @Test
    fun savedWordWarningBlocksButtonAndImeAndClearsForNewWord() {
        val submitted = mutableListOf<LearningItemDraft>()
        composeRule.setContent {
            WordForgeTheme {
                AddWordScreen(
                    onAddItem = { submitted.add(it) },
                    onNavigateBack = {},
                    existingItems = listOf(
                        Word(
                            word = "serendipity",
                            meaning = "a happy accident",
                            nextPromptAt = 1L,
                            createdAt = 1L,
                        ),
                    ),
                    shouldOfferNotifications = false,
                    onNotificationEducationShown = {},
                    onRequestNotificationPermission = {},
                )
            }
        }

        composeRule.onNodeWithTag("quick_add_term").performTextInput(" SERENDIPITY ")
        composeRule.onNodeWithTag("quick_add_meaning").performTextInput("another meaning")
        composeRule.onNodeWithText("Add word").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick_add_meaning").performImeAction()
        composeRule.runOnIdle { assertEquals(0, submitted.size) }
        composeRule.onNodeWithText("another meaning").assertExists()

        composeRule.onNodeWithTag("quick_add_term").performTextReplacement("petrichor")
        composeRule.onNodeWithTag("quick_add_meaning").performImeAction()
        composeRule.runOnIdle { assertEquals("petrichor", submitted.single().term) }
    }

    @Test
    fun simpleWordUsesImeOnlyAndRefocusesForTheNextItem() {
        var submitted: LearningItemDraft? = null
        composeRule.setContent {
            WordForgeTheme {
                QuickAddItemForm(
                    onSubmit = { submitted = it },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("quick_add_term").performTextInput("hola")
        composeRule.onNodeWithTag("quick_add_term").performImeAction()
        composeRule.onNodeWithTag("quick_add_meaning").assertIsFocused()
        composeRule.onNodeWithTag("quick_add_meaning").performTextInput("hello")
        composeRule.onNodeWithTag("quick_add_meaning").performImeAction()

        composeRule.runOnIdle {
            assertEquals(LearningItemType.SIMPLE_WORD, submitted?.type)
            assertEquals("hola", submitted?.term)
            assertEquals("hello", submitted?.meaning)
        }
        composeRule.onNodeWithTag("quick_add_term").assertIsFocused()
    }

    @Test
    fun verbImeChainSubmitsAllSixForms() {
        var submitted: LearningItemDraft? = null
        composeRule.setContent {
            WordForgeTheme {
                QuickAddItemForm(
                    onSubmit = { submitted = it },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("quick_add_type_verb_conjugation").performClick()
        composeRule.onNodeWithTag("quick_add_meaning").assertDoesNotExist()
        composeRule.onNodeWithTag("quick_add_term").performTextInput("decir")
        composeRule.onNodeWithTag("quick_add_term").performImeAction()
        composeRule.onNodeWithTag("quick_add_tense").assertExists()
        composeRule.onNodeWithTag("verb_tense_option_0").performClick()

        listOf(
            "YO conjugation" to "digo",
            "TÚ conjugation" to "dices",
            "ÉL / ELLA / USTED conjugation" to "dice",
            "NOSOTROS / AS conjugation" to "decimos",
            "VOSOTROS / AS conjugation" to "decís",
            "ELLOS / ELLAS / UDS. conjugation" to "dicen",
        ).forEach { (description, form) ->
            composeRule.onNodeWithContentDescription(description).performTextInput(form)
            composeRule.onNodeWithContentDescription(description).performImeAction()
        }

        composeRule.runOnIdle {
            val draft = submitted
            assertNotNull(draft)
            assertEquals(LearningItemType.VERB_CONJUGATION, draft?.type)
            assertEquals("decir", draft?.term)
            assertEquals("", draft?.meaning)
            assertEquals("presente de indicativo", draft?.verbConjugation?.tense)
            assertEquals(
                listOf("digo", "dices", "dice", "decimos", "decís", "dicen"),
                draft?.verbConjugation?.rows()?.map { it.form },
            )
        }
        composeRule.onNodeWithTag("quick_add_term").assertIsFocused()
    }
}
