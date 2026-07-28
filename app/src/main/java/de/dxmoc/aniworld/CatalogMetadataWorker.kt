package de.dxmoc.aniworld

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
        setForeground(notification(0, 0, indeterminate = true))
        return try {
            val catalog = app.repository.catalog(forceRefresh = force)
            if (catalog.items.isEmpty()) return Result.success()
            setForeground(notification(0, catalog.items.size, indeterminate = false))
            app.repository.preloadCatalogMetadata(catalog.items, force = force) { completed, total, _ ->
                setProgress(Data.Builder().putInt(KEY_COMPLETED, completed).putInt(KEY_TOTAL, total).build())
                setForeground(notification(completed, total, indeterminate = false))
            }
            app.store.setInitialPreloadCompleted()
            Result.success(Data.Builder().putInt(KEY_COMPLETED, catalog.items.size).putInt(KEY_TOTAL, catalog.items.size).build())
        } catch (error: Exception) {
            AppLogger.error("Metadaten", "Hintergrundaktualisierung fehlgeschlagen", error)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
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
        private const val CHANNEL_ID = "metadata_updates"
        private const val NOTIFICATION_ID = 1410

        fun enqueue(context: Context, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<CatalogMetadataWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putBoolean(KEY_FORCE, force).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
