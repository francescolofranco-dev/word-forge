package com.wordforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wordforge.data.ReminderFrequency

/** Completion is recorded only by the final action, never just by opening a page. */
@Composable
fun OnboardingDialog(
    reminderFrequency: ReminderFrequency,
    onReminderFrequencyChange: (ReminderFrequency) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onComplete: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    var showSkipWarning by rememberSaveable { mutableStateOf(false) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = { if (step > 0) step-- },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp)
                .fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "STEP ${step + 1} OF 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = (step + 1).toFloat(), range = 0f..3f, steps = 2,
                        )
                    },
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(3) { index ->
                        Spacer(
                            Modifier.weight(1f).height(4.dp).background(
                                color = if (index <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                            )
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        when (step) {
                            0 -> "Add what you want to learn"
                            1 -> "Try to remember"
                            else -> "Set your reminder rhythm"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    when (step) {
                        0 -> {
                            Text("Save a word and its meaning, or a Spanish verb tense.",
                                style = MaterialTheme.typography.bodyLarge)
                        }
                        1 -> {
                            Text("Recall the answer, then tap to reveal it.",
                                style = MaterialTheme.typography.bodyLarge)
                            Text("Words you miss come back sooner. Words you know wait longer.",
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
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight(),
                        ) { Text("Back") }
                    }
                    Button(
                        modifier = Modifier.weight(if (step == 2) 2f else 1f)
                            .heightIn(min = 48.dp).fillMaxHeight(),
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
                }
                if (step == 2 && !notificationsGranted && !requestedPermission) {
                    TextButton(onClick = { showSkipWarning = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Not now")
                    }
                }
            }
        }
    }

    if (showSkipWarning) {
        AlertDialog(
            onDismissRequest = { showSkipWarning = false },
            title = { Text("Continue without reminders?") },
            text = {
                Text("Without notifications, WordForge can’t remind you when words are ready to review. You can still review in the app and turn notifications on later.")
            },
            containerColor = MaterialTheme.colorScheme.background,
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showSkipWarning = false
                            requestedPermission = true
                            onRequestNotificationPermission()
                        },
                    ) { Text("Allow notifications") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showSkipWarning = false
                            onComplete()
                        },
                    ) { Text("Continue anyway") }
                }
            },
        )
    }
}
