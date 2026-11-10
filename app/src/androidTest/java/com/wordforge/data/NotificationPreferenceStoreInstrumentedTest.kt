package com.wordforge.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferenceStoreInstrumentedTest {
    @Test
    fun legacyEducationDoesNotSkipWizardAndCompletionPersistsIndependently() {
        // Use the test APK's preferences, leaving the installed app's settings alone.
        val context = InstrumentationRegistry.getInstrumentation().context
        val preferences = context.getSharedPreferences("wordforge_notification_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = NotificationPreferenceStore(context)
            assertFalse(store.hasCompletedOnboarding)
            store.hasShownEducation = true
            store.reminderFrequency = ReminderFrequency.EVERY_NEW_WORD
            val upgraded = NotificationPreferenceStore(context)
            assertFalse(upgraded.hasCompletedOnboarding)
            assertEquals(ReminderFrequency.EVERY_NEW_WORD, upgraded.reminderFrequency)
            upgraded.hasCompletedOnboarding = true
            assertTrue(NotificationPreferenceStore(context).hasCompletedOnboarding)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
