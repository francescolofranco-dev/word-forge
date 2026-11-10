package com.wordforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordforge.data.ReminderFrequency

/** Completion is recorded only by the final action, never just by opening a page. */
@Composable
fun OnboardingScreen(
    reminderFrequency: ReminderFrequency,
    onReminderFrequencyChange: (ReminderFrequency) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onComplete: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step > 0) { step-- }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            when {
                                step < 2 -> step++
                                notificationsGranted || requestedPermission -> onComplete()
                                else -> {
                                    requestedPermission = true
                                    onRequestNotificationPermission()
                                }
                            }
                        },
                    ) {
                        Text(
                            when {
                                step < 2 -> "Next"
                                notificationsGranted || requestedPermission -> "Get started"
                                else -> "Allow notifications"
                            }
                        )
                    }
                    if (step == 2 && !notificationsGranted && !requestedPermission) {
                        TextButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                            Text("Not now")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("WORDFORGE · ${step + 1} OF 3", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(
                when (step) {
                    0 -> "Make new words stick"
                    1 -> "A little recall goes a long way"
                    else -> "Set your reminder rhythm"
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            when (step) {
                0 -> {
                    Text("Save the words and Spanish verb conjugations you want to remember. Add a meaning or the six forms of a tense, and build your own learning collection.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Your collection stays on your device. No account needed.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                1 -> {
                    Text("When an item is ready, try to recall the answer before revealing it. Tell WordForge whether you remembered.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Remembered items return at longer intervals. Missed items come back sooner, so you spend time on what needs practice.",
                        style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    Text("Choose the maximum number of reminders per day, or hear whenever another item becomes due. Each notification groups the items ready to review.",
                        style = MaterialTheme.typography.bodyLarge)
                    Column {
                        ReminderFrequency.entries.forEach { frequency ->
                            ReminderFrequencyOption(
                                frequency = frequency,
                                selected = reminderFrequency == frequency,
                                onClick = { onReminderFrequencyChange(frequency) },
                            )
                        }
                    }
                    Text("Android may delay reminders to save battery. You can change this choice and enable notifications from the app menu anytime.",
                        style = MaterialTheme.typography.bodySmall)
                    if (notificationsGranted) {
                        Text("Notifications are enabled. You’re ready to go.")
                    } else if (requestedPermission) {
                        Text("If notifications are blocked, you can enable them later from the app menu. You can still review anytime.")
                    } else {
                        Text("Next, allow Android notifications so WordForge can remind you when it’s time to review.")
                    }
                }
            }
        }
    }
}
