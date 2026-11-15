package io.github.lootdev78.aniworld

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.security.MessageDigest

class CatalogMetadataWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniWorldApplication
        val force = inputData.getBoolean(KEY_FORCE, true)
        var catalogSlugs = emptySet<String>()
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
                val checkpoint = checkpointPreferences()
                catalogSlugs = catalog.items.map(Series::slug).filter(String::isNotBlank).toSet()
                val originalSlugs = app.store.offlineMetadataSlugs()
                val fingerprint = catalogFingerprint(catalog.items)
                val completedSlugs = loadCompletedSlugs(checkpoint, fingerprint).toMutableSet()
                check(
                    checkpoint.edit()
                        .putString(KEY_CATALOG_SLUGS, JSONArray(catalogSlugs.sorted()).toString())
                        .putString(KEY_ORIGINAL_SLUGS, JSONArray(originalSlugs.sorted()).toString())
                        .commit()
                ) { "Metadaten-Checkpoint konnte nicht gespeichert werden" }
                setProgress(Data.Builder().putInt(KEY_COMPLETED, completedSlugs.size).putInt(KEY_TOTAL, catalog.items.size).build())
                setForeground(notification(completedSlugs.size, catalog.items.size, indeterminate = false))
                app.repository.preloadCatalogMetadata(
                    items = catalog.items,
                    force = force,
                    alreadyCompletedSlugs = completedSlugs
                ) { completed, total, series ->
                    if (series?.hasCompleteOfflineMetadata() == true && completedSlugs.add(series.slug)) {
                        saveCheckpoint(checkpoint, fingerprint, completedSlugs)
                    }
                    setProgress(Data.Builder().putInt(KEY_COMPLETED, completed).putInt(KEY_TOTAL, total).build())
                    setForeground(notification(completed, total, indeterminate = false))
                }
                val inventory = app.store.offlineMetadataInventory(catalog.items.map(Series::slug).toSet())
                if (inventory.isComplete) checkpoint.edit().clear().apply()
                else saveCheckpoint(checkpoint, fingerprint, completedSlugs)
                app.store.setInitialPreloadCompleted()
                showFinishedNotification(
                    success = inventory.isComplete,
                    message = if (inventory.isComplete) {
                        applicationContext.getString(R.string.metadata_notification_complete, catalog.items.size)
                    } else {
                        "${inventory.completeCount}/${inventory.expectedCount} vollständig · ${inventory.missingSlugs.size} fehlend · ${inventory.incompleteSlugs.size} unvollständig. Beim nächsten Start wird fortgesetzt."
                    }
                )
                Result.success(
                    Data.Builder()
                        .putInt(KEY_COMPLETED, inventory.completeCount)
                        .putInt(KEY_TOTAL, inventory.expectedCount)
                        .build()
                )
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    val checkpoint = checkpointPreferences()
                    app.store.removeNewIncompleteOfflineMetadata(
                        loadStringSet(checkpoint, KEY_CATALOG_SLUGS),
                        loadStringSet(checkpoint, KEY_ORIGINAL_SLUGS)
                    )
                    checkpoint.edit().clear().commit()
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                    checkpointPreferences().edit().clear().putBoolean(KEY_CLEANUP_FAILED, true).commit()
                    AppLogger.error("Metadaten", "Abgebrochene Metadaten konnten nicht vollständig bereinigt werden", cleanupError)
                }
            }
            throw error
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

    private fun checkpointPreferences(): SharedPreferences =
        applicationContext.getSharedPreferences(CHECKPOINT_PREFERENCES, Context.MODE_PRIVATE)

    private fun catalogFingerprint(items: List<Series>): String {
        val canonical = items.map(Series::slug).filter(String::isNotBlank).sorted().joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun loadCompletedSlugs(preferences: SharedPreferences, fingerprint: String): Set<String> {
        if (preferences.getString(KEY_FINGERPRINT, null) != fingerprint) {
            preferences.edit().clear().putString(KEY_FINGERPRINT, fingerprint).apply()
            return emptySet()
        }
        val raw = preferences.getString(KEY_COMPLETED_SLUGS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
        }.getOrDefault(emptySet())
    }

    private fun loadStringSet(preferences: SharedPreferences, key: String): Set<String> {
        val raw = preferences.getString(key, "[]").orEmpty()
        val array = JSONArray(raw)
        return buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun saveCheckpoint(preferences: SharedPreferences, fingerprint: String, completed: Set<String>) {
        val array = JSONArray()
        completed.sorted().forEach(array::put)
        preferences.edit()
            .putString(KEY_FINGERPRINT, fingerprint)
            .putString(KEY_COMPLETED_SLUGS, array.toString())
            .apply()
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
        private const val KEY_CLEANUP_FAILED = "cleanup_failed"
        private const val CHANNEL_ID = "metadata_updates"
        private const val NOTIFICATION_ID = 1410
        private const val FINISHED_NOTIFICATION_ID = 1411
        private const val CHECKPOINT_PREFERENCES = "catalog_metadata_checkpoint"
        private const val KEY_FINGERPRINT = "catalog_fingerprint"
        private const val KEY_COMPLETED_SLUGS = "completed_slugs"
        private const val KEY_CATALOG_SLUGS = "catalog_slugs"
        private const val KEY_ORIGINAL_SLUGS = "original_slugs"

        fun enqueue(context: Context, force: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<CatalogMetadataWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(Data.Builder().putBoolean(KEY_FORCE, force).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }

        suspend fun cancelAndClearCheckpoint(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            withContext(Dispatchers.IO) {
                workManager.cancelUniqueWork(UNIQUE_WORK).result.get()
            }

            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
            while (true) {
                val work = withContext(Dispatchers.IO) {
                    workManager.getWorkInfosForUniqueWork(UNIQUE_WORK).get()
                }
                if (work.none {
                        it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                    }
                ) {
                    break
                }
                check(android.os.SystemClock.elapsedRealtime() < deadline) {
                    context.getString(R.string.metadata_import_cancel_timeout)
                }
                kotlinx.coroutines.delay(100L)
            }

            val checkpoint = context.applicationContext.getSharedPreferences(CHECKPOINT_PREFERENCES, Context.MODE_PRIVATE)
            if (checkpoint.getBoolean(KEY_CLEANUP_FAILED, false)) {
                checkpoint.edit().clear().commit()
                error(context.getString(R.string.metadata_import_checkpoint_cleanup_failed))
            }
            val cleared = checkpoint.edit().clear().commit()
            check(cleared) { context.getString(R.string.metadata_import_checkpoint_cleanup_failed) }
        }
    }
}
