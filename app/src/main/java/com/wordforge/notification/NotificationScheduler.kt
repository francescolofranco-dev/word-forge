package com.wordforge.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.work.WorkManager
import com.wordforge.data.ReminderFrequency
import com.wordforge.data.NotificationPreferenceStore
import com.wordforge.data.WordDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Maintains one inexact, idle-capable alarm for the next grouped reminder.
 *
 * A single alarm avoids a backlog of missed slots firing together after a
 * sleeping device wakes. Uncapped reminders follow item due times; capped
 * reminders use daily slots.
 */
object NotificationScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulingMutex = Mutex()

    private fun ensureScheduled(context: Context) {
        // Read the preference inside the lock so rapid setting changes cannot
        // leave an older request's alarm behind.
        scope.launch { scheduleNext(context.applicationContext) }
    }

    suspend fun scheduleNext(context: Context) = schedulingMutex.withLock {
        val preferences = NotificationPreferenceStore(context)
        val frequency = preferences.reminderFrequency
        val nowMillis = System.currentTimeMillis()
        val triggerAtMillis = if (frequency == ReminderFrequency.EVERY_NEW_WORD) {
            val dueTimes = WordDatabase.getDatabase(context).wordDao()
                .getAllOnce().map { it.nextPromptAt }
            nextInstantReminderAt(dueTimes, preferences.lastInstantReminderAt, nowMillis)
        } else {
            val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
            nextReminderAt(now, frequency).toInstant().toEpochMilli()
        }
        if (triggerAtMillis == null) {
            cancelScheduledAlarm(context)
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(
                context.getSystemService(AlarmManager::class.java),
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                reminderPendingIntent(context),
            )
        }
    }

    fun reschedule(context: Context) {
        ReviewNotification.cancel(context)
        ensureScheduled(context)
    }

    fun cancelDisplayedSummary(context: Context) {
        ReviewNotification.cancel(context)
    }

    /**
     * Stops the old daily/instant WorkManager paths. Already-persisted
     * per-item workers use compatibility no-op classes and expire silently.
     */
    fun cancelLegacySchedules(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(DailyCatchUpWorker.WORK_NAME)
        workManager.cancelUniqueWork(LEGACY_SUMMARY_REFRESH_WORK_NAME)
    }

    private fun cancelScheduledAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(reminderPendingIntent(context))
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BatchReminderReceiver::class.java).apply {
            action = BatchReminderReceiver.ACTION_SHOW_BATCH_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            BATCH_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val BATCH_REMINDER_REQUEST_CODE = 4109
    private const val LEGACY_SUMMARY_REFRESH_WORK_NAME = "review_summary_refresh"
}
