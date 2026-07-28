package de.dxmoc.aniworld

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
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

    fun prefetchCover(url: String, animeKey: String = url) {
        if (!isUsableCoverUrl(url)) return
        val cacheKey = coverCacheKey(animeKey, url)
        SingletonImageLoader.get(this).enqueue(
            ImageRequest.Builder(this)
                .data(url)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .build()
        )
    }

    fun prefetchCovers(series: Iterable<Series>) {
        series.asSequence()
            .filter { isUsableCoverUrl(it.coverUrl) }
            .distinctBy { it.slug to it.coverUrl }
            .forEach { prefetchCover(it.coverUrl, it.slug) }
    }

    fun clearImageCaches() {
        val loader = SingletonImageLoader.get(this)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun coverCacheKey(animeKey: String, url: String): String =
        "anime-cover:${animeKey.trim().lowercase()}:${url.trim()}"

    private fun isUsableCoverUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return listOf(
            "aniworld_logo", "aniworld-logo", "/logo.", "/logos/", "favicon", "placeholder",
            "loading", "spinner", "avatar", "profile", "tracking", "pixel.gif", "blank.gif"
        ).none(lower::contains)
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
