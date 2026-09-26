package com.wordforge.domain

import com.wordforge.data.Word
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WordStatisticsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 3, 30)

    private fun word(date: LocalDate, tier: Int = 0, correct: Int = 0, incorrect: Int = 0) = Word(
        word = "hola", meaning = "hello", createdAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        nextPromptAt = 1L, currentTier = tier, totalCorrect = correct, totalIncorrect = incorrect,
    )

    @Test fun emptyCollectionHasZeroBarsAndNoAccuracy() {
        val stats = wordStatistics(emptyList(), today, zone)
        assertEquals(0, stats.totalItems)
        assertEquals(List(9) { 0 }, stats.tierCounts)
        assertEquals(List(7) { 0 }, stats.dailyAdditions.map { it.count })
        assertNull(stats.accuracyPercent)
    }

    @Test fun additionsUseLocalCalendarDaysAcrossDaylightSavingAndFillGaps() {
        val words = listOf(
            word(today.minusDays(7)), // Outside the seven-day window.
            word(today.minusDays(6)),
            word(today.minusDays(1)),
            word(today), // Local midnight is still the previous UTC date.
            word(today),
            word(today.plusDays(1)), // Imported future dates are outside the window.
        )
        val stats = wordStatistics(words, today, zone)
        assertEquals(today.minusDays(6), stats.dailyAdditions.first().date)
        assertEquals(today, stats.dailyAdditions.last().date)
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 2), stats.dailyAdditions.map { it.count })
        assertEquals(6, stats.totalItems)
    }

    @Test fun countsEveryTierAndWeightsAccuracyByAnswers() {
        val stats = wordStatistics(listOf(
            word(today, tier = 0, correct = 1, incorrect = 1),
            word(today, tier = 8, correct = 8),
            word(today, tier = 8),
        ), today, zone)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 2), stats.tierCounts)
        assertEquals(10L, stats.totalAnswers)
        assertEquals(90, stats.accuracyPercent)
    }

    @Test fun answerTotalsDoNotOverflowIntegers() {
        val stats = wordStatistics(List(2) {
            word(today, correct = Int.MAX_VALUE, incorrect = Int.MAX_VALUE)
        }, today, zone)
        assertEquals(8_589_934_588L, stats.totalAnswers)
        assertEquals(50, stats.accuracyPercent)
    }
}
