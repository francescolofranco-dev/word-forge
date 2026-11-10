package com.wordforge.data

/**
 * Review reminder limits; zero means every newly due item with no daily cap.
 * Slots are intentionally spread across normal waking hours.
 */
enum class ReminderFrequency(
    val notificationsPerDay: Int,
    val slotHours: List<Int>,
) {
    ONCE(
        notificationsPerDay = 1,
        slotHours = listOf(9),
    ),
    TWICE(
        notificationsPerDay = 2,
        slotHours = listOf(9, 18),
    ),
    THREE_TIMES(
        notificationsPerDay = 3,
        slotHours = listOf(9, 14, 19),
    ),
    FIVE_TIMES(
        notificationsPerDay = 5,
        slotHours = listOf(9, 12, 15, 18, 21),
    ),
    EVERY_NEW_WORD(
        notificationsPerDay = 0,
        slotHours = emptyList(),
    );

    companion object {
        fun fromStoredCount(value: Int): ReminderFrequency =
            entries.firstOrNull { it.notificationsPerDay == value } ?: ONCE
    }
}
