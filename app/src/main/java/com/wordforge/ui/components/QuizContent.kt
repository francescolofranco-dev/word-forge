package com.wordforge.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordforge.data.LearningItemType
import com.wordforge.data.Word
import com.wordforge.domain.SpacedRepetition
import com.wordforge.ui.theme.LocalWordForgeColors
import kotlin.random.Random

@Composable
fun QuizContent(
    word: Word,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    advanceLabel: String = "Done",
) {
    if (word.itemType == LearningItemType.VERB_CONJUGATION) {
        VerbConjugationQuizContent(
            word = word,
            onCorrect = onCorrect,
            onIncorrect = onIncorrect,
            onAdvance = onAdvance,
            modifier = modifier,
            advanceLabel = advanceLabel,
        )
        return
    }

    var revealed by rememberSaveable(word.id) { mutableStateOf(false) }
    var answered by rememberSaveable(word.id) { mutableStateOf(false) }
    var wasCorrect by rememberSaveable(word.id) { mutableStateOf<Boolean?>(null) }
    val startingTier = rememberSaveable(word.id) { word.currentTier }
    val wordForgeColors = LocalWordForgeColors.current
    val view = LocalView.current

    // Save this coin flip as session state so rotating never changes the question mid-recall.
    var promptIsWord by rememberSaveable(word.id) {
        mutableStateOf(if (word.randomlyFlip) Random.nextBoolean() else true)
    }
    // If an edit turns random flipping off while this card is open, honor it immediately.
    if (!word.randomlyFlip && !promptIsWord) promptIsWord = true

    val promptText = if (promptIsWord) word.word else word.meaning
    val revealText = if (promptIsWord) word.meaning else word.word
    val recallQuestion = if (promptIsWord) {
        "Do you remember what this means?"
    } else {
        "Do you remember the word?"
    }
    val revealButtonLabel = if (promptIsWord) "Reveal meaning" else "Reveal word"

    fun reveal() {
        if (!revealed) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            revealed = true
        }
    }

    var heroVisible by rememberSaveable(word.id) { mutableStateOf(false) }
    LaunchedEffect(word.id) { heroVisible = true }
    val heroScale by animateFloatAsState(
        targetValue = if (heroVisible) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "heroScale",
    )
    val flipRotation by animateFloatAsState(
        targetValue = if (revealed) 180f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "cardFlip",
    )
    val density = LocalDensity.current.density
    val showingBack = flipRotation > 90f

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

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 168.dp)
                .scale(heroScale)
                .graphicsLayer {
                    rotationY = flipRotation
                    cameraDistance = 12f * density
                }
                .clickable(enabled = !revealed, onClick = ::reveal),
            shape = RoundedCornerShape(24.dp),
            color = if (showingBack) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shadowElevation = if (showingBack) 0.dp else 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 168.dp)
                    .padding(24.dp)
                    .graphicsLayer { if (showingBack) rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showingBack) revealText else promptText,
                    style = when {
                        showingBack && promptIsWord -> MaterialTheme.typography.titleLarge
                        showingBack -> MaterialTheme.typography.headlineMedium
                        promptIsWord -> MaterialTheme.typography.displayLarge
                        else -> MaterialTheme.typography.headlineMedium
                    },
                    textAlign = TextAlign.Center,
                    color = if (showingBack) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!revealed) {
            Text(
                text = recallQuestion,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = ::reveal,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(revealButtonLabel, style = MaterialTheme.typography.titleLarge)
            }
        } else if (!answered) {
            Text(
                text = "Did you get it right?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
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
                    onClick = {
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
        } else {
            val correct = wasCorrect == true
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
                    "Mastery maintained — this one is holding strong."
                correct && startingTier == SpacedRepetition.MAX_TIER - 1 ->
                    "Mastered — this one is forged into memory."
                correct -> "Nice — moving to the next tier."
                else -> "No worries — you'll see this one again sooner."
            }
            val feedbackIcon = if (correct) Icons.Rounded.Check else Icons.Rounded.Close

            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = feedbackBg,
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .graphicsLayer { scaleX = 1f; scaleY = 1f },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(shape = CircleShape, color = feedbackAccent) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = feedbackIcon,
                                    contentDescription = null,
                                    tint = feedbackAccentContent,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = feedbackContent,
                    )
                }
            }
        }
    }
}
