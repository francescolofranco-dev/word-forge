package com.wordforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordforge.data.Word
import com.wordforge.ui.components.QuizContent
import com.wordforge.ui.components.SparksLogo
import com.wordforge.ui.theme.LocalWordForgeColors
import com.wordforge.viewmodel.WordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueReviewScreen(
    viewModel: WordViewModel,
    onFinished: () -> Unit,
) {
    // Snapshot the overdue list at entry — words graded during the session
    // become non-overdue but we still want to walk through them all.
    var queueIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var currentWord by remember { mutableStateOf<Word?>(null) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(queueIds) {
        if (queueIds == null) {
            queueIds = viewModel.getOverdueWords().map { it.id }
        }
    }

    val currentId = queueIds?.getOrNull(index)
    LaunchedEffect(currentId) {
        currentWord = currentId?.let { viewModel.getWordById(it) }
        if (currentId != null && currentWord == null) index++
    }

    fun requestExit() {
        if (index > 0 && index < (queueIds?.size ?: 0)) showExitDialog = true else onFinished()
    }
    BackHandler(onBack = ::requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val ids = queueIds
                    if (ids != null && ids.isNotEmpty() && index < ids.size) {
                        Text(
                            text = "Review ${index + 1} / ${ids.size}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else {
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Exit review"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val ids = queueIds
            when {
                ids == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ids.isEmpty() -> {
                    NothingToReview(onFinished)
                }

                index >= ids.size -> {
                    AllDone(reviewed = ids.size, onFinished = onFinished)
                }

                currentWord?.id != currentId -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> {
                    val reviewWord = currentWord ?: return@Box
                    val isLast = index == ids.size - 1
                    Column(modifier = Modifier.fillMaxSize()) {
                        LinearProgressIndicator(
                            progress = { (index + 1).toFloat() / ids.size },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        ) {
                            QuizContent(
                                word = reviewWord,
                                onCorrect = { viewModel.onAnswerCorrect(reviewWord) },
                                onIncorrect = { viewModel.onAnswerIncorrect(reviewWord) },
                                onAdvance = { index++ },
                                advanceLabel = if (isLast) "Finish review" else "Next item",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Leave this review?") },
            text = { Text("Your answers are saved. You can continue the remaining items later.") },
            confirmButton = {
                TextButton(onClick = onFinished) { Text("Leave review") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Keep reviewing") }
            },
        )
    }
}

@Composable
private fun NothingToReview(onFinished: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing ready",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "All your items are still on schedule.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                text = "Back to list",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun AllDone(reviewed: Int, onFinished: () -> Unit) {
    val wordForgeColors = LocalWordForgeColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(wordForgeColors.correct),
            contentAlignment = Alignment.Center,
        ) {
            SparksLogo(
                size = 76.dp,
                background = wordForgeColors.correct,
                foreground = wordForgeColors.onCorrect,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "All done",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (reviewed == 1) "Reviewed 1 item." else "Reviewed $reviewed items.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                text = "Back to list",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
