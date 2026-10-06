package com.ayush.baymax.platform

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ayush.baymax.MainActivity
import com.ayush.baymax.R
import com.ayush.baymax.data.Reminder
import com.ayush.baymax.data.ReminderScheduler
import java.util.concurrent.TimeUnit

/**
 * Reminders as WorkManager jobs that post a notification (FR-25). They survive reboots and
 * need no network (NFR-6). WorkManager's shortest repeat is 15 minutes.
 */
class WorkManagerReminderScheduler(
    context: Context,
    private val onNeedsPermission: () -> Unit = {},
) : ReminderScheduler {
    private val app = context.applicationContext
    private val work = WorkManager.getInstance(app)

    override fun schedule(reminder: Reminder) {
        if (!canNotify(app)) onNeedsPermission()
        val data = workDataOf(KEY_TEXT to reminder.text, KEY_ID to reminder.id)
        val delay = (reminder.firstTime - System.currentTimeMillis()).coerceAtLeast(0)
        val repeat = reminder.repeatIntervalMinutes
        if (repeat != null) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(repeat.coerceAtLeast(15), TimeUnit.MINUTES)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(TAG)
                .build()
            work.enqueueUniquePeriodicWork(name(reminder.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(name(reminder.id), ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancel(id: Long) {
        work.cancelUniqueWork(name(id))
    }

    override fun cancelAll() {
        work.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "baymax-reminder"
        const val KEY_TEXT = "text"
        const val KEY_ID = "id"
        const val CHANNEL = "reminders"
        private fun name(id: Long) = "reminder-$id"

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val text = inputData.getString(WorkManagerReminderScheduler.KEY_TEXT) ?: return Result.success()
        val id = inputData.getLong(WorkManagerReminderScheduler.KEY_ID, 0L)
        notify(applicationContext, id, text)
        return Result.success()
    }

    private fun notify(context: Context, id: Long, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(WorkManagerReminderScheduler.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (!WorkManagerReminderScheduler.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, WorkManagerReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Baymax")
            .setContentText("It is time to $text.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("It is time to $text. I am here if you need me."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
    }
}
