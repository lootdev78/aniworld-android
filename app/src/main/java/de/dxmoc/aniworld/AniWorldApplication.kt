package de.dxmoc.aniworld

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AniWorldApplication : Application(), SingletonImageLoader.Factory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val store: AppStore by lazy { AppStore(this, database) }
    val repository: AniWorldRepository by lazy {
        AniWorldRepository(
            context = this,
            cache = RoomRepositoryCache(database.pageCacheDao()),
            metadataDao = database.seriesMetadataDao()
        )
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.filesDir.resolve("anime_cover_cache"))
                    .maxSizePercent(0.08)
                    .build()
            }
            .build()

    fun prefetchCover(url: String) {
        if (url.isBlank()) return
        SingletonImageLoader.get(this).enqueue(
            ImageRequest.Builder(this)
                .data(url)
                .build()
        )
    }

    fun prefetchCovers(series: Iterable<Series>) {
        series.asSequence()
            .map(Series::coverUrl)
            .filter(String::isNotBlank)
            .distinct()
            .forEach(::prefetchCover)
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        applicationScope.launch {
            runCatching { store.migrateLegacyData() }
                .onFailure { AppLogger.error("Speicher", "Migration in Room fehlgeschlagen", it) }
            runCatching { RoomRepositoryCache(database.pageCacheDao()).prune() }
        }
    }
}
