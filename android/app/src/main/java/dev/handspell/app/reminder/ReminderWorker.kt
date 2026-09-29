package dev.handspell.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.handspell.app.HandspellApplication
import dev.handspell.app.MainActivity
import dev.handspell.app.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * The daily streak reminder. Local only: WorkManager runs it on the phone, it reads the saved progress, and it posts
 * at most one notification. Nothing goes over the network. Each run schedules the next one at the chosen time.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HandspellApplication).container
        val prefs = container.appPreferencesStore
        if (!prefs.reminderEnabled.first()) return Result.success()
        val now = System.currentTimeMillis()
        val decision = reminderDecision(container.progressStore.snapshot.first(), now)
        if (decision != ReminderDecision.Skip) post(applicationContext, decision)
        ReminderScheduler.schedule(applicationContext, prefs.reminderSlot.first())
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "streak_reminder"
        private const val NOTIFICATION_ID = 7

        fun post(context: Context, decision: ReminderDecision) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            createChannel(context)
            val title = when (decision) {
                is ReminderDecision.KeepStreak ->
                    context.resources.getQuantityString(R.plurals.reminder_keep_streak, decision.days, decision.days)
                else -> context.getString(R.string.reminder_start_title)
            }
            val body = context.getString(
                if (decision is ReminderDecision.KeepStreak) R.string.reminder_keep_streak_body else R.string.reminder_start_body,
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_streak_flame)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permission withdrawn between the check and the post: stay quiet.
            }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.reminder_channel_body) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/** One unique piece of work, always replaced: turning the reminder on, changing its time or running it reschedules. */
object ReminderScheduler {
    private const val WORK_NAME = "streak-reminder"

    fun schedule(context: Context, slot: ReminderSlot, nowMs: Long = System.currentTimeMillis()) {
        val delay = (nextReminderAt(nowMs, slot) - nowMs).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
