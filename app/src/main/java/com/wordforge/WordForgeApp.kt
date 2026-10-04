package com.wordforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wordforge.data.NotificationPreferenceStore
import com.wordforge.data.removeRetiredExerciseData
import com.wordforge.notification.NotificationScheduler

class WordForgeApp : Application() {

    companion object {
        // Channel importance is immutable after creation. A new id upgrades
        // existing installs from the previous default channel to heads-up
        // review reminders while still leaving final control to the user.
        const val NOTIFICATION_CHANNEL_ID = "wordforge_review_alerts_v2"
    }

    override fun onCreate() {
        super.onCreate()
        removeRetiredExerciseData(this)
        createNotificationChannel()
        NotificationScheduler.cancelLegacySchedules(this)
        NotificationScheduler.ensureScheduled(
            this,
            NotificationPreferenceStore(this).reminderFrequency,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Review reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Grouped reminders when learning items are ready to review"
            enableVibration(true)
            setShowBadge(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
