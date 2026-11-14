package app.docubuds.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.docubuds.core.ReminderDates
import app.docubuds.ui.MainActivity
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Local-only expiry reminders (spec §8). A daily worker checks for records
 * expiring in exactly 30 / 7 / 1 days and posts ONE generic notification —
 * no document names, numbers or amounts in the notification itself. Tapping
 * it opens DocuBuds (locked, requiring unlock). Already-notified
 * (recordId:offset) keys are recorded in the vault so a reminder is never
 * repeated. No network, no server.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val store = VaultStore(applicationContext, KeystoreKeyManager(applicationContext))
            val data = store.load() ?: return Result.success()
            val today = LocalDate.now().toEpochDay()
            val due = mutableListOf<String>()
            var notified = data.notified
            for (record in data.records) {
                val expiry = record.expiryEpochDay ?: continue
                for (offset in ReminderDates.dueOffsets(expiry, today)) {
                    val key = ReminderDates.notificationKey(record.id, offset)
                    if (key !in notified) {
                        due += key
                        notified = notified + key
                    }
                }
            }
            if (due.isNotEmpty()) {
                store.save(store.purgeTrash(data.copy(notified = notified)))
                postGenericNotification(applicationContext)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun postGenericNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "DocuBuds reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Expiry reminders for your documents" }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DocuBuds")
            .setContentText("A document expires soon. Open DocuBuds to check.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(1, notification)
    }

    companion object {
        const val CHANNEL_ID = "docubuds_reminders"
        const val WORK_NAME = "docubuds_daily_reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
