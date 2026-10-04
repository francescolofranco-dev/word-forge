package com.wordforge.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordforge.R
import com.wordforge.data.LearningItemType
import com.wordforge.data.ReminderFrequency
import com.wordforge.data.Word
import com.wordforge.ui.components.OverdueCard
import com.wordforge.ui.components.SparksLogo
import com.wordforge.ui.components.WordCard
import com.wordforge.ui.components.WordForgeFab
import com.wordforge.ui.components.WordForgeSnackbarHost
import com.wordforge.ui.theme.ThemeMode
import com.wordforge.viewmodel.WordViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    viewModel: WordViewModel,
    onNavigateToStatistics: () -> Unit,
    onNavigateToAddWord: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToHowItWorks: () -> Unit,
    onNavigateToOverdueReview: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    reminderFrequency: ReminderFrequency,
    onReminderFrequencyChange: (ReminderFrequency) -> Unit,
    notificationsGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
) {
    val words by viewModel.allWords.collectAsStateWithLifecycle()

    var showDeleteAllDialog1 by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReminderFrequencyDialog by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<WordViewModel.ImportPreview?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.Default) { viewModel.exportToJson() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                }
                snackbarHostState.showSnackbar("Exported ${words.size} items")
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Export failed: ${t.message ?: "unknown error"}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                } ?: return@launch
                importPreview = withContext(Dispatchers.Default) {
                    viewModel.previewImport(json)
                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Import failed: ${t.message ?: "unknown error"}")
            }
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(words) {
        while (true) {
            val untilNext = words.minOfOrNull { it.nextPromptAt - System.currentTimeMillis() }
            delay(if (untilNext != null && untilNext <= 60 * 60 * 1000L) 1.seconds else 30.seconds)
            now = System.currentTimeMillis()
        }
    }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { !listState.canScrollBackward } }

    fun deleteWithUndo(word: Word) {
        viewModel.deleteWord(word)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "\"${word.word}\" deleted",
                actionLabel = "Undo",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreWord(word)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val overdueNow = words.count { it.nextPromptAt <= now }
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WordForge",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (words.isNotEmpty()) {
                            val totalLabel = if (words.size == 1) "1 item" else "${words.size} items"
                            val subtitle = if (overdueNow > 0)
                                "$totalLabel · $overdueNow ready"
                            else
                                totalLabel
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    SparksLogo(
                        size = 36.dp,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToStatistics) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Statistics")
                    }
                    IconButton(onClick = onNavigateToHowItWorks) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "How it works"
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More"
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export items") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("wordforge-export.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import items") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Theme: ${themeMode.label}") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DarkMode, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showThemeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Review reminders")
                                        Text(
                                            text = if (notificationsGranted) {
                                                reminderFrequency.shortLabel()
                                            } else {
                                                "${reminderFrequency.shortLabel()} · permission needed"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showReminderFrequencyDialog = true
                                }
                            )
                            if (words.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete all items",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showDeleteAllDialog1 = true
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Info, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        snackbarHost = { WordForgeSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            WordForgeFab(onClick = onNavigateToAddWord, expanded = fabExpanded)
        }
    ) { innerPadding ->
        if (words.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            val overdueCount = words.count { it.nextPromptAt <= now }
            val upcoming = words.filter { it.nextPromptAt > now }
            val upcomingSections = groupUpcomingWords(upcoming, now)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (overdueCount > 0) {
                    item {
                        OverdueCard(
                            count = overdueCount,
                            onClick = onNavigateToOverdueReview,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                upcomingSections.forEach { (section, sectionWords) ->
                    item(key = "section_$section") {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }

                    items(sectionWords, key = { it.id }) { word ->
                        UpcomingWordCard(
                            word = word,
                            now = now,
                            onClick = { onNavigateToDetail(word.id) },
                            onDelete = { deleteWithUndo(word) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(96.dp)) }
            }
        }
    }

    if (showDeleteAllDialog1) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog1 = false },
            title = { Text("Delete all items") },
            text = { Text("Permanently delete all ${words.size} items and their progress?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllWords()
                        showDeleteAllDialog1 = false
                    }
                ) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showDeleteAllDialog1 = false
                            exportLauncher.launch("wordforge-backup.json")
                        }
                    ) {
                        Text("Export backup")
                    }
                    TextButton(onClick = { showDeleteAllDialog1 = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("Import ${preview.totalCount} items?") },
            text = {
                Text(
                    buildString {
                        append("${preview.newCount} new · ${preview.updatedCount} existing updated")
                        if (preview.updatedCount > 0) {
                            append("\n\nMatching items and review progress will be replaced by the backup.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importPreview = null
                        scope.launch {
                            try {
                                val count = viewModel.commitImport(preview)
                                snackbarHostState.showSnackbar("Imported $count items")
                            } catch (t: Throwable) {
                                snackbarHostState.showSnackbar(
                                    "Import failed: ${t.message ?: "unknown error"}"
                                )
                            }
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            selectedThemeMode = themeMode,
            onSelectThemeMode = { selectedThemeMode ->
                onThemeModeChange(selectedThemeMode)
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showReminderFrequencyDialog) {
        ReminderFrequencyDialog(
            selectedFrequency = reminderFrequency,
            notificationsGranted = notificationsGranted,
            onSelectFrequency = onReminderFrequencyChange,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onDismiss = { showReminderFrequencyDialog = false },
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { context.appVersionName() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About WordForge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Turn new words into long-term memory through spaced repetition.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private fun Context.appVersionName(): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName.orEmpty()
}

@Composable
private fun UpcomingWordCard(
    word: Word,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WordCard(
        word = word.word,
        meaning = listMeaning(word),
        tier = word.currentTier,
        dueLabel = formatCompactDue(word.nextPromptAt, now),
        typeLabel = listTypeLabel(word),
        onClick = onClick,
        onDelete = onDelete,
        modifier = modifier,
    )
}

internal fun listMeaning(word: Word): String? =
    word.meaning.takeIf { word.itemType == LearningItemType.SIMPLE_WORD }

internal fun listTypeLabel(word: Word): String? {
    if (word.itemType != LearningItemType.VERB_CONJUGATION) return null
    return word.verbConjugation
        ?.tense
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase(Locale.ROOT)
}

internal fun groupUpcomingWords(
    words: List<Word>,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, List<Word>> {
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    return words
        .sortedBy { it.nextPromptAt }
        .groupBy { word ->
            val dueDate = Instant.ofEpochMilli(word.nextPromptAt).atZone(zoneId).toLocalDate()
            when (ChronoUnit.DAYS.between(today, dueDate)) {
                0L -> "Later today"
                1L -> "Tomorrow"
                in 2L..7L -> "This week"
                else -> "Later"
            }
        }
}

@Composable
private fun ThemeModeDialog(
    selectedThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { themeMode ->
                    ThemeModeOption(
                        themeMode = themeMode,
                        selected = themeMode == selectedThemeMode,
                        onClick = { onSelectThemeMode(themeMode) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ThemeModeOption(
    themeMode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = themeMode.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = themeMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderFrequencyDialog(
    selectedFrequency: ReminderFrequency,
    notificationsGranted: Boolean,
    onSelectFrequency: (ReminderFrequency) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingFrequency by remember(selectedFrequency) {
        mutableStateOf(selectedFrequency)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review reminders") },
        text = {
            Column {
                Text(
                    text = "Choose how many times per day WordForge may remind you. Each reminder groups every item that is overdue at that time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ReminderFrequency.entries.forEach { frequency ->
                    ReminderFrequencyOption(
                        frequency = frequency,
                        selected = frequency == pendingFrequency,
                        onClick = { pendingFrequency = frequency },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Times are approximate because Android may delay background alarms during battery saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectFrequency(pendingFrequency)
                    onDismiss()
                    if (!notificationsGranted) onRequestNotificationPermission()
                }
            ) {
                Text(if (notificationsGranted) "Done" else "Allow notifications")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ReminderFrequencyOption(
    frequency: ReminderFrequency,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = frequency.longLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = frequency.scheduleLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ReminderFrequency.shortLabel(): String = when (this) {
    ReminderFrequency.ONCE -> "Once daily"
    ReminderFrequency.TWICE -> "Twice daily"
    ReminderFrequency.THREE_TIMES -> "3 times daily"
    ReminderFrequency.FIVE_TIMES -> "5 times daily"
}

private fun ReminderFrequency.longLabel(): String = when (this) {
    ReminderFrequency.ONCE -> "Once per day"
    ReminderFrequency.TWICE -> "Twice per day"
    ReminderFrequency.THREE_TIMES -> "3 times per day"
    ReminderFrequency.FIVE_TIMES -> "5 times per day"
}

private fun ReminderFrequency.scheduleLabel(): String =
    "Around " + slotHours.joinToString(" · ") { hour ->
        "${hour.toString().padStart(2, '0')}:00"
    }

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparks),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(92.dp),
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Your forge is ready",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Add your first item to start hammering it into long-term memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

// Compact mono-style due label: "ready", "2d 14h", "5h 23m",
// "12m 04s", "37s". Two units max for legibility at small sizes.
fun formatCompactDue(nextPromptAt: Long, now: Long): String {
    val diff = nextPromptAt - now
    if (diff <= 0L) return "ready"

    val totalSeconds = diff / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}
