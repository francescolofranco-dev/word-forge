package com.wordforge.notification

import com.wordforge.data.ReminderFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun frequenciesExposeOnlyTheSupportedDailyCounts() {
        assertEquals(
            listOf(1, 2, 3, 5, 0),
            ReminderFrequency.entries.map { it.notificationsPerDay },
        )
        assertEquals(
            ReminderFrequency.ONCE,
            ReminderFrequency.fromStoredCount(99),
        )
    }

    @Test
    fun choosesTheNextSlotLaterToday() {
        val now = localTime(2026, 7, 27, 10, 30)

        assertEquals(
            localTime(2026, 7, 27, 14, 0),
            nextReminderAt(now, ReminderFrequency.THREE_TIMES),
        )
    }

    @Test
    fun rollsForwardToTomorrowAfterTheLastSlot() {
        val now = localTime(2026, 7, 27, 22, 0)

        assertEquals(
            localTime(2026, 7, 28, 9, 0),
            nextReminderAt(now, ReminderFrequency.FIVE_TIMES),
        )
    }

    @Test
    fun exactCurrentSlotIsNotScheduledTwice() {
        val now = localTime(2026, 7, 27, 9, 0)

        assertEquals(
            localTime(2026, 7, 27, 18, 0),
            nextReminderAt(now, ReminderFrequency.TWICE),
        )
    }

    @Test
    fun uncappedPreferenceRoundTrips() {
        assertEquals(ReminderFrequency.EVERY_NEW_WORD, ReminderFrequency.fromStoredCount(0))
    }

    @Test
    fun instantReminderUsesEarliestUnannouncedDueTime() {
        assertEquals(8_000L, nextInstantReminderAt(listOf(3_000L, 9_000L, 8_000L), 5_000L, 6_000L))
    }

    @Test
    fun missedInstantReminderCatchesUpWithoutRepeatingOldOverdueItems() {
        assertEquals(11_000L, nextInstantReminderAt(listOf(3_000L, 8_000L), 5_000L, 10_000L))
        assertEquals(null, nextInstantReminderAt(listOf(3_000L, 8_000L), 10_000L, 12_000L))
        assertEquals(null, nextInstantReminderAt(emptyList(), 0L, 12_000L))
    }

    private fun localTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): ZonedDateTime = ZonedDateTime.of(
        year,
        month,
        day,
        hour,
        minute,
        0,
        0,
        madrid,
    )
}
