package com.wordforge.data

import android.content.Context
import androidx.core.content.edit

/** Stores review-notification education and scheduling preferences. */
class NotificationPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "wordforge_notification_preferences",
        Context.MODE_PRIVATE,
    )

    var hasCompletedOnboarding: Boolean
        get() = preferences.getBoolean("has_completed_onboarding", false)
        set(value) { preferences.edit { putBoolean("has_completed_onboarding", value) } }

    var lastInstantReminderAt: Long
        get() = preferences.getLong("last_instant_reminder_at", 0L)
        set(value) { preferences.edit { putLong("last_instant_reminder_at", value) } }

    var hasShownEducation: Boolean
        get() = preferences.getBoolean(KEY_HAS_SHOWN_EDUCATION, false)
        set(value) {
            preferences.edit { putBoolean(KEY_HAS_SHOWN_EDUCATION, value) }
        }

    var reminderFrequency: ReminderFrequency
        get() = ReminderFrequency.fromStoredCount(
            preferences.getInt(
                KEY_DAILY_REMINDER_COUNT,
                ReminderFrequency.ONCE.notificationsPerDay,
            )
        )
        set(value) {
            preferences.edit {
                putInt(KEY_DAILY_REMINDER_COUNT, value.notificationsPerDay)
            }
        }

    private companion object {
        const val KEY_HAS_SHOWN_EDUCATION = "has_shown_education"
        const val KEY_DAILY_REMINDER_COUNT = "daily_reminder_count"
    }
}
