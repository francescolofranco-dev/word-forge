package com.wordforge.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordforge.data.Word
import com.wordforge.domain.SpacedRepetition
import com.wordforge.ui.theme.LocalWordForgeColors

@Composable
fun VerbConjugationQuizContent(
    word: Word,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    advanceLabel: String = "Done",
) {
    val conjugation = word.verbConjugation
    if (conjugation == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "This conjugation is missing its forms.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val rows = conjugation.rows()
    val allRevealedMask = (1 shl rows.size) - 1
    var revealedMask by rememberSaveable(word.id) { mutableIntStateOf(0) }
    var answered by rememberSaveable(word.id) { mutableStateOf(false) }
    var wasCorrect by rememberSaveable(word.id) { mutableStateOf<Boolean?>(null) }
    val startingTier = rememberSaveable(word.id) { word.currentTier }
    val view = LocalView.current
    val revealedCount = Integer.bitCount(revealedMask)
    val allRevealed = revealedMask == allRevealedMask

    fun reveal(index: Int) {
        val personMask = 1 shl index
        if (revealedMask and personMask == 0) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            revealedMask = revealedMask or personMask
        }
    }

    QuizContentLayout(
        wordId = word.id,
        answered = answered,
        onAdvance = onAdvance,
        advanceLabel = advanceLabel,
        modifier = modifier,
    ) {
        Text(
            text = "TIER $startingTier OF ${SpacedRepetition.MAX_TIER}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(if (answered) 8.dp else 14.dp))

        Text(
            text = word.word,
            style = if (answered) {
                MaterialTheme.typography.displayMedium
            } else {
                MaterialTheme.typography.displayLarge
            },
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(if (answered) 8.dp else 10.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = conjugation.tense.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(if (answered) 16.dp else 22.dp))

        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    val isRevealed = revealedMask and (1 shl index) != 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = if (answered) 48.dp else 64.dp)
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.person,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        if (isRevealed) {
                            Text(
                                text = row.form,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        } else {
                            Text(
                                text = "••••••",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(
                            onClick = { reveal(index) },
                            enabled = !isRevealed && !answered,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = if (isRevealed) {
                                    "${row.person} conjugation revealed"
                                } else {
                                    "Reveal ${row.person} conjugation"
                                },
                                tint = if (isRevealed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (answered) 16.dp else 20.dp))

        when {
            !allRevealed -> {
                Text(
                    text = "Say each form, then tap its eye to check.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "$revealedCount of ${rows.size} revealed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            !answered -> {
                Text(
                    text = "Did you get the whole verb right?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                VerbAnswerButtons(
                    onIncorrect = {
                        answered = true
                        wasCorrect = false
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.REJECT
                            } else {
                                HapticFeedbackConstants.LONG_PRESS
                            }
                        )
                        onIncorrect()
                    },
                    onCorrect = {
                        answered = true
                        wasCorrect = true
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.CONFIRM
                            } else {
                                HapticFeedbackConstants.VIRTUAL_KEY
                            }
                        )
                        onCorrect()
                    },
                )
            }

            else -> {
                VerbAnswerFeedback(
                    correct = wasCorrect == true,
                    startingTier = startingTier,
                )
            }
        }
    }
}

@Composable
private fun VerbAnswerButtons(
    onIncorrect: () -> Unit,
    onCorrect: () -> Unit,
) {
    val wordForgeColors = LocalWordForgeColors.current
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onIncorrect,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = wordForgeColors.incorrect,
            ),
        ) {
            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nope", style = MaterialTheme.typography.titleLarge)
        }

        Button(
            onClick = onCorrect,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = wordForgeColors.correct,
                contentColor = wordForgeColors.onCorrect,
            ),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Got it!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun VerbAnswerFeedback(
    correct: Boolean,
    startingTier: Int,
) {
    val wordForgeColors = LocalWordForgeColors.current
    val feedbackAccent = if (correct) wordForgeColors.correct else wordForgeColors.incorrect
    val feedbackAccentContent = if (correct) {
        wordForgeColors.onCorrect
    } else {
        wordForgeColors.onIncorrect
    }
    val feedbackBg = if (correct) {
        wordForgeColors.correctContainer
    } else {
        wordForgeColors.incorrectContainer
    }
    val feedbackContent = if (correct) {
        wordForgeColors.onCorrectContainer
    } else {
        wordForgeColors.onIncorrectContainer
    }
    val feedbackText = when {
        correct && startingTier >= SpacedRepetition.MAX_TIER ->
            "Mastery maintained — this conjugation is holding strong."
        correct && startingTier == SpacedRepetition.MAX_TIER - 1 ->
            "Mastered — this conjugation is forged into memory."
        correct -> "Nice — moving the whole conjugation to the next tier."
        else -> "No worries — you’ll review the whole conjugation again sooner."
    }
    val feedbackIcon = if (correct) Icons.Rounded.Check else Icons.Rounded.Close

    Surface(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = feedbackBg,
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(feedbackAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = feedbackIcon,
                    contentDescription = null,
                    tint = feedbackAccentContent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = feedbackText,
                style = MaterialTheme.typography.titleMedium,
                color = feedbackContent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
