package com.wordforge.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
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
class OnboardingDialogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun walkthroughSelectsFrequencyRequestsPermissionAndFinishesAfterDenial() {
        val frequency = mutableStateOf(ReminderFrequency.ONCE)
        var requests = 0
        var completions = 0
        compose.setContent {
            WordForgeTheme {
                OnboardingDialog(
                    reminderFrequency = frequency.value,
                    onReminderFrequencyChange = { frequency.value = it },
                    notificationsGranted = false,
                    onRequestNotificationPermission = { requests++ },
                    onComplete = { completions++ },
                )
            }
        }
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("Add what you want to learn").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Try to remember").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Add what you want to learn").assertIsDisplayed()
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

    @Test
    fun skippingNotificationsRequiresAcknowledgingTheWarning() {
        var completions = 0
        compose.setContent {
            WordForgeTheme {
                OnboardingDialog(
                    reminderFrequency = ReminderFrequency.ONCE,
                    onReminderFrequencyChange = {},
                    notificationsGranted = false,
                    onRequestNotificationPermission = {},
                    onComplete = { completions++ },
                )
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Continue without reminders?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText("Continue anyway").performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }


    @Test
    fun largeTextKeepsTheReminderChoicesScrollableAndActionsVisible() {
        var completions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                WordForgeTheme {
                    OnboardingDialog(
                        reminderFrequency = ReminderFrequency.ONCE,
                        onReminderFrequencyChange = {},
                        notificationsGranted = true,
                        onRequestNotificationPermission = { error("Permission is already granted") },
                        onComplete = { completions++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Every new word").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.onNodeWithText("Get started").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

}
