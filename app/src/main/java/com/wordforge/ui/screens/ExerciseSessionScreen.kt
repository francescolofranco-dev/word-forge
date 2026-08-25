package com.wordforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wordforge.ui.components.ExerciseWebView

/**
 * Native shell for the app-owned interactive HTML exercise renderer.
 * Answers live only in the WebView, so leaving intentionally clears them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSessionScreen(
    title: String,
    sessionJson: String,
    answerStateJson: String,
    onAnswerStateChange: (String) -> Unit,
    onLeave: () -> Unit,
) {
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    fun requestExit() {
        showExitDialog = true
    }

    BackHandler(onBack = ::requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { "AI practice" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Leave exercise session",
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
        ExerciseWebView(
            sessionJson = sessionJson,
            restoredStateJson = answerStateJson,
            onStateChange = onAnswerStateChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Leave this session?") },
            text = {
                Text("Answers in this exercise page are not saved and will be cleared.")
            },
            confirmButton = {
                TextButton(onClick = onLeave) { Text("Leave session") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Keep practising") }
            },
        )
    }
}
