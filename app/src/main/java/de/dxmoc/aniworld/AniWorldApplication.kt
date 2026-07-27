package de.dxmoc.aniworld

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AniWorldApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val store: AppStore by lazy { AppStore(this, database) }
    val repository: AniWorldRepository by lazy {
        AniWorldRepository(this, cache = RoomRepositoryCache(database.pageCacheDao()))
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
