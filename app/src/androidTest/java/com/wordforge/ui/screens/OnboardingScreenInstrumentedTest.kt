package com.wordforge.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wordforge.data.ReminderFrequency
import com.wordforge.ui.theme.WordForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun walkthroughSelectsFrequencyRequestsPermissionAndFinishesAfterDenial() {
        val frequency = mutableStateOf(ReminderFrequency.ONCE)
        var requests = 0
        var completions = 0
        compose.setContent {
            WordForgeTheme {
                OnboardingScreen(
                    reminderFrequency = frequency.value,
                    onReminderFrequencyChange = { frequency.value = it },
                    notificationsGranted = false,
                    onRequestNotificationPermission = { requests++ },
                    onComplete = { completions++ },
                )
            }
        }
        compose.onNodeWithText("Make new words stick").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("A little recall goes a long way").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Make new words stick").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Every new word").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReminderFrequency.EVERY_NEW_WORD, frequency.value) }
        compose.onNodeWithText("Allow notifications").performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            assertEquals(0, completions)
        }
        compose.onNodeWithText("Get started").performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }
}
