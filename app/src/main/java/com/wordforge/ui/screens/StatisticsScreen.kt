package com.wordforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.wordforge.data.Word
import com.wordforge.domain.wordStatistics
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(words: List<Word>, onNavigateBack: () -> Unit) {
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            zone = ZoneId.systemDefault()
            today = LocalDate.now(zone)
            delay(60_000)
        }
    }
    val stats = remember(words, today, zone) { wordStatistics(words, today, zone) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    if (stats.totalItems == 1) "1 saved item" else "${stats.totalItems} saved items",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Words and verb conjugations currently in your collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (words.isEmpty()) {
                item {
                    Text("Add your first item to start seeing your progress here.")
                }
            }
            item {
                ChartCard("Items added", "Last 7 days · ${stats.dailyAdditions.sumOf { it.count }} added") {
                    val max = stats.dailyAdditions.maxOf { it.count }.coerceAtLeast(1)
                    val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
                    stats.dailyAdditions.forEach { day ->
                        ChartBar(day.date.format(dateFormat), day.count.toLong(), max.toLong())
                    }
                }
            }
            item {
                ChartCard("Learning progress", "Current items at each tier · 8 is the highest") {
                    val max = stats.tierCounts.max().coerceAtLeast(1).toLong()
                    stats.tierCounts.forEachIndexed { tier, count ->
                        ChartBar("Tier $tier", count.toLong(), max)
                    }
                }
            }
            item {
                ChartCard(
                    "Review results",
                    stats.accuracyPercent?.let { "$it% correct · ${stats.totalAnswers} answers" }
                        ?: "No reviews yet · answer a review to get started",
                ) {
                    ChartBar("Correct", stats.correct, stats.totalAnswers.coerceAtLeast(1))
                    ChartBar(
                        "Incorrect", stats.incorrect, stats.totalAnswers.coerceAtLeast(1),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item {
                Text(
                    "Charts use saved items and their review totals. Deleting an item removes it from these statistics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ChartBar(
    label: String,
    count: Long,
    maximum: Long,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "$label: $count"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (count > 0) {
                Box(Modifier.fillMaxWidth((count.toDouble() / maximum).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
            }
        }
    }
}
