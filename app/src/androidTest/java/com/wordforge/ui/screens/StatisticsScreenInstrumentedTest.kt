package com.wordforge.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordforge.data.Word
import com.wordforge.ui.theme.WordForgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun emptyStateAndBackAreAvailable() {
        var returned = false
        composeRule.setContent {
            WordForgeTheme {
                StatisticsScreen(emptyList(), onNavigateBack = { returned = true })
            }
        }
        composeRule.onNodeWithText("Add your first item to start seeing your progress here.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(returned)
    }

    @Test fun chartsShowAccessibleCountsAndUpdateAfterReviewAtNarrowWidth() {
        val words = mutableStateOf(listOf(Word(
            word = "hola", meaning = "hello", createdAt = System.currentTimeMillis(),
            nextPromptAt = 1L, currentTier = 3, totalCorrect = 3, totalIncorrect = 1,
        )))
        composeRule.setContent {
            WordForgeTheme {
                Box(Modifier.width(320.dp)) {
                    StatisticsScreen(words.value, onNavigateBack = {})
                }
            }
        }
        composeRule.onNodeWithText("1 saved item").assertIsDisplayed()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(
            hasContentDescription("Tier 3: 1")
        )
        composeRule.onNodeWithContentDescription("Tier 3: 1").assertIsDisplayed()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(
            hasText("75% correct · 4 answers")
        )
        composeRule.onNodeWithText("75% correct · 4 answers").assertIsDisplayed()
        composeRule.runOnIdle {
            words.value = listOf(words.value.single().copy(totalCorrect = 4))
        }
        composeRule.onNodeWithText("80% correct · 5 answers").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Correct: 4").assertExists()
    }
}
