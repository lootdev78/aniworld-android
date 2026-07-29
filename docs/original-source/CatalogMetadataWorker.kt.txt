package io.github.lootdev78.aniworld

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class CatalogMetadataWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniWorldApplication
        val force = inputData.getBoolean(KEY_FORCE, true)
        createChannel()
        setProgress(Data.Builder().putInt(KEY_COMPLETED, 0).putInt(KEY_TOTAL, 0).build())
        setForeground(notification(0, 0, indeterminate = true))
        return try {
            val catalog = app.repository.catalog(forceRefresh = force)
            if (catalog.items.isEmpty()) {
                val message = applicationContext.getString(R.string.catalog_metadata_no_titles)
                showFinishedNotification(success = false, message = message)
                Result.failure(Data.Builder().putString(KEY_ERROR, message).build())
            } else {
                setProgress(Data.Builder().putInt(KEY_COMPLETED, 0).putInt(KEY_TOTAL, catalog.items.size).build())
                setForeground(notification(0, catalog.items.size, indeterminate = false))
                app.repository.preloadCatalogMetadata(catalog.items, force = force) { completed, total, _ ->
                    setProgress(Data.Builder().putInt(KEY_COMPLETED, completed).putInt(KEY_TOTAL, total).build())
                    setForeground(notification(completed, total, indeterminate = false))
                }
                app.store.setInitialPreloadCompleted()
                showFinishedNotification(
                    success = true,
                    message = applicationContext.getString(R.string.metadata_notification_complete, catalog.items.size)
                )
                Result.success(
                    Data.Builder()
                        .putInt(KEY_COMPLETED, catalog.items.size)
                        .putInt(KEY_TOTAL, catalog.items.size)
                        .build()
                )
            }
        } catch (error: Exception) {
            AppLogger.error("Metadaten", "Hintergrundaktualisierung fehlgeschlagen", error)
            val message = error.message ?: applicationContext.getString(R.string.catalog_metadata_update_failed)
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                showFinishedNotification(success = false, message = message)
                Result.failure(Data.Builder().putString(KEY_ERROR, message).build())
            }
        }
    }

    private fun showFinishedNotification(success: Boolean, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (success) R.drawable.ic_stat_sync else android.R.drawable.stat_notify_error)
            .setContentTitle(
                applicationContext.getString(
                    if (success) R.string.metadata_notification_finished_title
                    else R.string.metadata_notification_failed_title
                )
            )
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (success) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(FINISHED_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            AppLogger.warn("Metadaten", "Abschlussbenachrichtigung konnte nicht angezeigt werden", error.message.orEmpty())
        }
    }

    private fun notification(completed: Int, total: Int, indeterminate: Boolean): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = applicationContext.getString(R.string.metadata_notification_title)
        val text = if (indeterminate || total <= 0) {
            applicationContext.getString(R.string.metadata_notification_preparing)
        } else {
            applicationContext.getString(R.string.metadata_notification_progress, completed, total)
        }
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(completed < total || indeterminate)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), indeterminate)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, applicationContext.getString(R.string.cancel), cancelIntent)
        return ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.metadata_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = applicationContext.getString(R.string.metadata_notification_channel_description) }
        )
    }

    companion object {
        const val UNIQUE_WORK = "catalog-metadata-refresh"
        const val KEY_FORCE = "force"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "metadata_updates"
        private const val NOTIFICATION_ID = 1410
        private const val FINISHED_NOTIFICATION_ID = 1411

        fun enqueue(context: Context, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<CatalogMetadataWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putBoolean(KEY_FORCE, force).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
