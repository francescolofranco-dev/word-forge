package com.wordforge.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordforge.data.LearningItemDraft
import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.ui.theme.WordForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningItemFormScaffoldInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editingAVerbUsesTheFixedTenseDropdownWithoutMeaning() {
        var submitted: LearningItemDraft? = null
        composeRule.setContent {
            WordForgeTheme {
                LearningItemFormScaffold(
                    topBarLabel = "EDIT ITEM",
                    headline = "Refine this item",
                    subtitle = "Update its content.",
                    submitLabel = "Save changes",
                    onSubmit = { submitted = it },
                    onNavigateBack = {},
                    initialDraft = LearningItemDraft(
                        type = LearningItemType.VERB_CONJUGATION,
                        term = "decir",
                        meaning = "legacy meaning",
                        verbConjugation = VerbConjugation(
                            tense = "legacy custom tense",
                            yo = "digo",
                            tu = "dices",
                            elEllaUsted = "dice",
                            nosotros = "decimos",
                            vosotros = "decís",
                            ellosEllasUstedes = "dicen",
                        ),
                    ),
                    allowTypeSelection = false,
                )
            }
        }

        composeRule.onNodeWithText("MEANING").assertDoesNotExist()
        composeRule.onNodeWithText("legacy custom tense").assertExists()
        composeRule.onNodeWithTag("edit_verb_tense").performClick()
        composeRule.onNodeWithTag("verb_tense_option_1").performClick()
        composeRule.onNodeWithText("pretérito perfecto simple").assertExists()
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertNotNull(submitted)
            assertEquals("", submitted?.meaning)
            assertEquals(
                "pretérito perfecto simple",
                submitted?.verbConjugation?.tense,
            )
        }
    }
}
