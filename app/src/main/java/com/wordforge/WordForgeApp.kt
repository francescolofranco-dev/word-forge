package com.wordforge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wordforge.data.WordDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.wordforge.data.removeRetiredExerciseData
import com.wordforge.notification.NotificationScheduler

class WordForgeApp : Application() {

    companion object {
        // Channel importance is immutable after creation. A new id upgrades
        // existing installs from the previous default channel to heads-up
        // review reminders while still leaving final control to the user.
        const val NOTIFICATION_CHANNEL_ID = "wordforge_review_alerts_v2"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        removeRetiredExerciseData(this)
        createNotificationChannel()
        NotificationScheduler.cancelLegacySchedules(this)
        applicationScope.launch {
            // Adding, reviewing, deleting and importing items all update the
            // next instant reminder, including changes made outside a screen.
            WordDatabase.getDatabase(this@WordForgeApp).wordDao().getAll().collect {
                NotificationScheduler.scheduleNext(this@WordForgeApp)
            }
        }
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
