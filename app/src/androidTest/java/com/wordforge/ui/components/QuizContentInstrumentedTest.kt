package com.wordforge.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.data.Word
import com.wordforge.ui.theme.WordForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizContentInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun masteredConjugationFitsPhoneAndAdvancesWithoutScrolling() {
        val word = mutableStateOf(verbWord())
        var correctAnswers = 0
        var advances = 0
        composeRule.setContent {
            WordForgeTheme(darkTheme = true) {
                Box(Modifier.size(width = 360.dp, height = 600.dp).testTag("quiz_viewport")) {
                    QuizContent(
                        word = word.value,
                        onCorrect = { correctAnswers++ },
                        onIncorrect = {},
                        onAdvance = {
                            advances++
                            word.value = verbWord().copy(id = "next-verb", currentTier = 2)
                        },
                        advanceLabel = if (advances == 0) "Next item" else "Finish review",
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    )
                }
            }
        }

        revealVerb()
        composeRule.onNodeWithText("Got it!").performScrollTo().performClick()

        composeRule.onNodeWithText("Mastered — this conjugation is forged into memory.")
            .assertIsDisplayed()
        verbWord().verbConjugation!!.rows().forEach {
            composeRule.onNodeWithText(it.form).assertIsDisplayed()
        }
        assertNoScrollingNeeded()
        assertAdvanceFullyVisible("Next item")
        composeRule.onNodeWithText("Next item").performClick()
        composeRule.onNodeWithText("0 of 6 revealed").assertExists()
        composeRule.onNodeWithText("TIER 2 OF 8").assertIsDisplayed()
        assertEquals(0f, scrollRange().value(), 0f)

        revealVerb()
        composeRule.onNodeWithText("Got it!").performScrollTo().performClick()
        assertAdvanceFullyVisible("Finish review")
        composeRule.onNodeWithText("Finish review").performClick()
        assertEquals(2, advances)
        assertEquals(2, correctAnswers)
    }

    @Test
    fun incorrectConjugationCanFinishOnShortScreenWithLargeText() {
        showQuiz(verbWord(), height = 300.dp, fontScale = 1.5f)
        revealVerb()
        composeRule.onNodeWithText("Nope").performScrollTo().performClick()

        assertAdvanceFullyVisible("Done")
        assertTrue(scrollRange().maxValue() > 0f)
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, advanceCount)
        assertEquals(1, incorrectCount)
    }

    @Test
    fun correctConjugationCanAdvanceOnShortScreenWithLargeText() {
        showQuiz(verbWord(), height = 300.dp, fontScale = 1.5f)
        revealVerb()
        composeRule.onNodeWithText("Got it!").performScrollTo().performClick()

        assertAdvanceFullyVisible("Done")
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, advanceCount)
        assertEquals(1, correctCount)
    }

    @Test
    fun wordResultCanAdvanceOnShortScreenWithLargeText() {
        showQuiz(
            word = Word(
                word = "hola",
                meaning = "hello",
                currentTier = 7,
                nextPromptAt = 0L,
                createdAt = 0L,
                randomlyFlip = false,
            ),
            height = 300.dp,
            fontScale = 1.5f,
        )
        composeRule.onNodeWithText("Reveal meaning").performScrollTo().performClick()
        composeRule.onNodeWithText("Got it!").performScrollTo().performClick()

        assertAdvanceFullyVisible("Done")
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(1, advanceCount)
        assertEquals(1, correctCount)
    }

    private var advanceCount = 0
    private var correctCount = 0
    private var incorrectCount = 0

    private fun showQuiz(word: Word, height: Dp, fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                WordForgeTheme {
                    Box(Modifier.size(width = 320.dp, height = height).testTag("quiz_viewport")) {
                        QuizContent(
                            word = word,
                            onCorrect = { correctCount++ },
                            onIncorrect = { incorrectCount++ },
                            onAdvance = { advanceCount++ },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }
    }

    private fun revealVerb() {
        verbWord().verbConjugation!!.rows().forEach {
            composeRule.onNodeWithContentDescription("Reveal ${it.person} conjugation")
                .performScrollTo().performClick()
        }
    }

    private fun scrollRange() = composeRule.onNode(hasScrollAction())
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun assertNoScrollingNeeded() {
        assertEquals(0f, scrollRange().maxValue(), 0f)
    }

    private fun assertAdvanceFullyVisible(label: String) {
        val button = composeRule.onNodeWithText(label).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val viewport = composeRule.onNodeWithTag("quiz_viewport").getUnclippedBoundsInRoot()
        assertTrue("Advance button must fit inside the viewport", button.top >= viewport.top)
        assertTrue("Advance button must fit inside the viewport", button.bottom <= viewport.bottom)
        assertTrue("Advance button must keep its full touch height", button.bottom - button.top >= 56.dp)
    }

    private fun verbWord() = Word(
        id = "morir",
        word = "morir",
        meaning = "",
        currentTier = 7,
        nextPromptAt = 0L,
        createdAt = 0L,
        itemType = LearningItemType.VERB_CONJUGATION,
        verbConjugation = VerbConjugation(
            tense = "futuro simple",
            yo = "moriré",
            tu = "morirás",
            elEllaUsted = "morirá",
            nosotros = "moriremos",
            vosotros = "moriréis",
            ellosEllasUstedes = "morirán",
        ),
    )
}
