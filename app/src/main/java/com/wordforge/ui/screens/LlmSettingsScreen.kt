package com.wordforge.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wordforge.exercise.LlmProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    persistedProvider: LlmProvider,
    persistedModel: String,
    isConnected: Boolean,
    errorMessage: String?,
    onSave: (LlmProvider, String, String) -> Boolean,
    onDisconnect: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    ProtectCredentialScreen()

    var selectedProviderName by rememberSaveable(persistedProvider.name) {
        mutableStateOf(persistedProvider.name)
    }
    var model by rememberSaveable(persistedModel) { mutableStateOf(persistedModel) }
    // Never place the secret in saved-instance state, which is outside the encrypted store.
    var apiKey by remember { mutableStateOf("") }
    var revealApiKey by remember { mutableStateOf(false) }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }

    val selectedProvider = LlmProvider.entries.firstOrNull {
        it.name == selectedProviderName
    } ?: persistedProvider
    var eligibilityConfirmed by rememberSaveable(selectedProviderName) {
        mutableStateOf(false)
    }
    val trimmedModel = model.trim()
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LLM CONNECTION",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Connect your model",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "WordForge uses your provider's developer API to create custom exercise sessions.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ConnectionDisclosure()

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                ConnectedStatus(
                    provider = persistedProvider,
                    model = persistedModel,
                    onDisconnect = { showDisconnectConfirmation = true },
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "PROVIDER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LlmProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = {
                            selectedProviderName = provider.name
                            model = provider.defaultModel
                        },
                        label = { Text(settingsProviderLabel(provider)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = eligibilityConfirmed,
                        role = Role.Checkbox,
                        onValueChange = { eligibilityConfirmed = it },
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = eligibilityConfirmed,
                    onCheckedChange = null,
                )
                Text(
                    text = if (selectedProvider == LlmProvider.GEMINI) {
                        "I confirm I am at least 18 and that my Gemini API use meets its current professional/business, region, audience, and purpose requirements."
                    } else {
                        "I confirm that my use meets ${selectedProvider.displayName}'s current API age, account, region, audience, and purpose requirements."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                supportingText = {
                    Text("Enter a model available to your developer account.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Developer API key") },
                supportingText = {
                    Text(
                        if (isConnected) {
                            "Enter a key to replace the saved connection. The current key is never displayed."
                        } else {
                            "The key is encrypted locally and is never included in exports."
                        }
                    )
                },
                singleLine = true,
                visualTransformation = if (revealApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { revealApiKey = !revealApiKey },
                        modifier = Modifier.semantics {
                            contentDescription = if (revealApiKey) {
                                "Hide API key"
                            } else {
                                "Show API key"
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (revealApiKey) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (onSave(selectedProvider, trimmedModel, apiKey.trim())) {
                        apiKey = ""
                        revealApiKey = false
                    }
                },
                enabled = trimmedModel.isNotEmpty() && apiKey.isNotBlank() &&
                    eligibilityConfirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (isConnected) "Save new connection" else "Save connection")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "The key is verified when you generate a session with ${settingsProviderLabel(selectedProvider)}. Provider usage limits and charges may apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            icon = {
                Icon(Icons.Rounded.LinkOff, contentDescription = null)
            },
            title = { Text("Disconnect ${settingsProviderLabel(persistedProvider)}?") },
            text = {
                Text("The encrypted API key will be removed from this device. Your saved words will not be affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectConfirmation = false
                        apiKey = ""
                        onDisconnect()
                    },
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text("Keep connected")
                }
            },
        )
    }
}

@Composable
private fun ProtectCredentialScreen() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasAlreadySecure = (
            window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) ?: 0
        ) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasAlreadySecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ConnectionDisclosure() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Developer access required",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "This does not sign in to a ChatGPT or Gemini subscription. You need a developer API key from OpenAI or Google AI Studio.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Connect only if the provider's API terms allow your age, region, audience, and intended use.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = "Your key is encrypted locally. API usage may cost money.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedStatus(
    provider: LlmProvider,
    model: String,
    onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connected to ${settingsProviderLabel(provider)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (model.isNotBlank()) {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        }
    }
}

private fun settingsProviderLabel(provider: LlmProvider): String = when (provider.name) {
    "OPENAI" -> "OpenAI"
    "GEMINI" -> "Gemini"
    else -> provider.name
        .lowercase()
        .replaceFirstChar { it.titlecase() }
}
