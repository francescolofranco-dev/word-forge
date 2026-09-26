package com.wordforge.domain

import com.wordforge.data.Word
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyAdditions(val date: LocalDate, val count: Int)

data class WordStatistics(
    val totalItems: Int,
    val tierCounts: List<Int>,
    val dailyAdditions: List<DailyAdditions>,
    val correct: Long,
    val incorrect: Long,
) {
    val totalAnswers: Long get() = correct + incorrect
    val accuracyPercent: Int?
        get() = if (totalAnswers == 0L) null else
            (correct * 100.0 / totalAnswers).toInt()
}

/** Statistics describe saved items; deleted items and their answers are excluded. */
fun wordStatistics(
    words: List<Word>,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): WordStatistics {
    val addedByDate = words.groupingBy {
        Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate()
    }.eachCount()
    val tiers = words.groupingBy { it.currentTier }.eachCount()
    return WordStatistics(
        totalItems = words.size,
        tierCounts = (SpacedRepetition.MIN_TIER..SpacedRepetition.MAX_TIER).map {
            tiers[it] ?: 0
        },
        dailyAdditions = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DailyAdditions(date, addedByDate[date] ?: 0)
        },
        correct = words.sumOf { it.totalCorrect.toLong() },
        incorrect = words.sumOf { it.totalIncorrect.toLong() },
    )
}
