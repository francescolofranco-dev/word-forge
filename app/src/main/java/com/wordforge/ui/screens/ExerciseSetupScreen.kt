package com.wordforge.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordforge.exercise.ExerciseConfig
import com.wordforge.exercise.ExerciseType
import com.wordforge.exercise.LlmProvider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSetupScreen(
    initialConfig: ExerciseConfig,
    provider: LlmProvider,
    isConnected: Boolean,
    totalWordCount: Int,
    simpleWordCount: Int,
    isGenerating: Boolean,
    errorMessage: String?,
    onStart: (ExerciseConfig) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val initialTypeNames = initialConfig.selectedTypes.joinToString(",") { it.name }
    var exerciseCount by rememberSaveable(initialConfig.exerciseCount) {
        mutableIntStateOf(initialConfig.exerciseCount)
    }
    var selectedTypeNames by rememberSaveable(initialTypeNames) {
        mutableStateOf(initialTypeNames)
    }
    var coveragePercent by rememberSaveable(initialConfig.coveragePercent) {
        mutableIntStateOf(initialConfig.coveragePercent)
    }
    var showEmptyTypeValidation by rememberSaveable { mutableStateOf(false) }
    var showVocabularyDisclosure by rememberSaveable { mutableStateOf(false) }
    var sendEligibilityConfirmed by rememberSaveable { mutableStateOf(false) }

    val selectedNameSet = selectedTypeNames
        .split(',')
        .filterTo(mutableSetOf()) { it.isNotBlank() }
    val selectedTypes = ExerciseType.entries
        .filterTo(linkedSetOf()) { it.name in selectedNameSet }
    val config = ExerciseConfig(
        exerciseCount = exerciseCount,
        selectedTypes = selectedTypes,
        coveragePercent = coveragePercent,
    )
    val requestedItemCount = config.requestedWordCount(totalWordCount)
    val targetItemCount = config.targetWordCount(totalWordCount, simpleWordCount)
    val compatibilityMessage = when {
        ExerciseType.TRANSLATION in selectedTypes && simpleWordCount == 0 ->
            "Translation needs at least one simple word with a stored meaning."
        ExerciseType.MATCHING in selectedTypes && totalWordCount < 2 ->
            "Matching needs at least two saved learning items."
        else -> null
    }
    val coverageDetail = when {
        compatibilityMessage != null -> compatibilityMessage
        targetItemCount < minOf(requestedItemCount, ExerciseConfig.MAX_SELECTED_WORDS) ->
            "Limited by the chosen exercise mix and compatible vocabulary. Increase the exercise count or include more matching exercises to cover more items."
        requestedItemCount > ExerciseConfig.MAX_SELECTED_WORDS ->
            "Capped at ${ExerciseConfig.MAX_SELECTED_WORDS} items to keep generation reliable."
        targetItemCount > requestedItemCount ->
            "At least two items are used so matching remains interactive."
        else -> "From $totalWordCount items currently in your forge."
    }
    val canStart = isConnected && targetItemCount > 0 && !isGenerating

    fun requestStart() {
        if (canStart) {
            sendEligibilityConfirmed = false
            showVocabularyDisclosure = true
        }
    }

    BackHandler(enabled = isGenerating) {
        onCancel()
        onNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "EXERCISE SESSION",
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isGenerating) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Go back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings, enabled = !isGenerating) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Open LLM settings",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Forge a practice set",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose the mix. ${provider.displayName} will build a fresh interactive session from your vocabulary.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            when {
                totalWordCount <= 0 -> NoVocabularyState()
                !isConnected -> NotConnectedState(
                    provider = provider,
                    onOpenSettings = onOpenSettings,
                )
                compatibilityMessage != null -> IncompatibleVocabularyState(
                    message = compatibilityMessage,
                )
                else -> ConnectedProviderState(provider)
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("NUMBER OF EXERCISES")
            Spacer(modifier = Modifier.height(10.dp))
            ExerciseCountSelector(
                count = exerciseCount,
                minimumCount = maxOf(ExerciseConfig.MIN_EXERCISE_COUNT, selectedTypes.size),
                enabled = !isGenerating,
                onDecrease = {
                    exerciseCount = (exerciseCount - 1)
                        .coerceAtLeast(
                            maxOf(ExerciseConfig.MIN_EXERCISE_COUNT, selectedTypes.size)
                        )
                },
                onIncrease = {
                    exerciseCount = (exerciseCount + 1)
                        .coerceAtMost(ExerciseConfig.MAX_EXERCISE_COUNT)
                },
            )

            Spacer(modifier = Modifier.height(28.dp))
            SectionLabel("EXERCISE TYPES")
            Spacer(modifier = Modifier.height(10.dp))
            ExerciseType.entries.chunked(2).forEach { rowTypes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTypes.forEach { type ->
                        val selected = type in selectedTypes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (isGenerating) return@FilterChip
                                val updated = if (selected) {
                                    selectedTypes - type
                                } else {
                                    selectedTypes + type
                                }
                                if (updated.isEmpty()) {
                                    showEmptyTypeValidation = true
                                } else {
                                    selectedTypeNames = updated.joinToString(",") { it.name }
                                    exerciseCount = exerciseCount.coerceAtLeast(updated.size)
                                    showEmptyTypeValidation = false
                                }
                            },
                            enabled = !isGenerating,
                            label = { Text(type.displayName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTypes.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Text(
                text = if (showEmptyTypeValidation) {
                    "Keep at least one exercise type selected."
                } else {
                    "Selected types are distributed across the session."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (showEmptyTypeValidation) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("VOCABULARY COVERAGE")
                Text(
                    text = "$coveragePercent%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = coveragePercent.toFloat(),
                onValueChange = {
                    coveragePercent = it.roundToInt().coerceIn(
                        ExerciseConfig.MIN_COVERAGE_PERCENT,
                        ExerciseConfig.MAX_COVERAGE_PERCENT,
                    )
                },
                enabled = !isGenerating,
                valueRange = ExerciseConfig.MIN_COVERAGE_PERCENT.toFloat()..
                    ExerciseConfig.MAX_COVERAGE_PERCENT.toFloat(),
                steps = 14,
                modifier = Modifier.semantics {
                    contentDescription = "Vocabulary coverage"
                    stateDescription = "$coveragePercent percent, $targetItemCount items targeted"
                },
            )
            CoverageSummary(
                targetItemCount = targetItemCount,
                totalWordCount = totalWordCount,
                detail = coverageDetail,
            )

            if (isGenerating) {
                Spacer(modifier = Modifier.height(20.dp))
                GenerationInProgress(
                    provider = provider,
                    onCancel = onCancel,
                )
            } else if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                GenerationError(
                    message = errorMessage,
                    canRetry = canStart,
                    onRetry = ::requestStart,
                    onOpenSettings = onOpenSettings,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = ::requestStart,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Generate session")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Only the selected vocabulary content is sent. Review scheduling data and learning statistics stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showVocabularyDisclosure) {
        AlertDialog(
            onDismissRequest = { showVocabularyDisclosure = false },
            icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
            title = { Text("Send vocabulary to ${provider.displayName}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "$targetItemCount of your $totalWordCount saved items will be included to generate $exerciseCount exercises. Terms, meanings, and relevant verb forms may be sent."
                    )
                    Text(
                        "The request is processed under ${provider.displayName}'s terms and privacy policy. API usage may incur charges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (provider == LlmProvider.GEMINI) {
                            "By continuing, you confirm that this use meets Gemini API eligibility and usage terms. The current terms require users to be at least 18 and restrict clients intended for people under 18."
                        } else {
                            "By continuing, you confirm that this use meets ${provider.displayName} API eligibility and usage terms."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = sendEligibilityConfirmed,
                                role = Role.Checkbox,
                                onValueChange = { sendEligibilityConfirmed = it },
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = sendEligibilityConfirmed,
                            onCheckedChange = null,
                        )
                        Text(
                            text = if (provider == LlmProvider.GEMINI) {
                                "I am at least 18 and this professional/business API use meets the current Gemini terms."
                            } else {
                                "I meet ${provider.displayName}'s current API eligibility and usage terms."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVocabularyDisclosure = false
                        onStart(config)
                    },
                    enabled = sendEligibilityConfirmed,
                ) {
                    Text("Send and generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVocabularyDisclosure = false }) {
                    Text("Not now")
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ExerciseCountSelector(
    count: Int,
    minimumCount: Int,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = enabled && count > minimumCount,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer exercises")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onIncrease,
                enabled = enabled && count < ExerciseConfig.MAX_EXERCISE_COUNT,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "More exercises")
            }
        }
    }
}

@Composable
private fun CoverageSummary(
    targetItemCount: Int,
    totalWordCount: Int,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$targetItemCount items targeted",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (totalWordCount <= 0) {
                        "Add vocabulary before generating a session."
                    } else {
                        detail
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IncompatibleVocabularyState(message: String) {
    StatusSurface(
        icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
        title = "Adjust this exercise mix",
        body = message,
        isError = true,
    )
}

@Composable
private fun NoVocabularyState() {
    StatusSurface(
        icon = { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = null) },
        title = "Add vocabulary first",
        body = "An exercise session needs at least one saved word or verb.",
        isError = true,
    )
}

@Composable
private fun NotConnectedState(
    provider: LlmProvider,
    onOpenSettings: () -> Unit,
) {
    StatusSurface(
        icon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
        title = "${provider.displayName} is not connected",
        body = "Add a developer API key before generating a session.",
        isError = true,
        action = {
            FilledTonalButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Connection settings")
            }
        },
    )
}

@Composable
private fun ConnectedProviderState(provider: LlmProvider) {
    StatusSurface(
        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
        title = "${provider.displayName} connected",
        body = "Ready to create a new session.",
        isError = false,
    )
}

@Composable
private fun GenerationInProgress(
    provider: LlmProvider,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.width(28.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Generating with ${provider.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "This can take a moment.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Cancel, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel generation")
            }
        }
    }
}

@Composable
private fun GenerationError(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Couldn't generate the session",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = onRetry, enabled = canRetry) {
                    Text("Try again")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Check connection")
                }
            }
        }
    }
}

@Composable
private fun StatusSurface(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    isError: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = body, style = MaterialTheme.typography.bodySmall)
                }
            }
            action?.invoke()
        }
    }
}
