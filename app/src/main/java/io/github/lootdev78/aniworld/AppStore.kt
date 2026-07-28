package io.github.lootdev78.aniworld

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.aniWorldDataStore by preferencesDataStore("aniworld_settings")

private data class SettingsSnapshot(
    val languagePriority: List<Language>,
    val hosterPriority: List<String>,
    val verifyStreams: Boolean,
    val favoriteSort: LibrarySort,
    val watchedSort: LibrarySort,
    val permissionIntroSeen: Boolean,
    val useDynamicColors: Boolean,
    val lastHomeTab: String,
    val initialPreloadCompleted: Boolean,
    val webAdBlockEnabled: Boolean,
    val webFilterLists: Set<String>,
    val webSessionPanelExpanded: Boolean,
    val webMediaPanelExpanded: Boolean,
    val autoNextEnabled: Boolean,
    val autoPlayPreferredHoster: Boolean,
    val allowExternalPlayer: Boolean,
    val startupTab: String,
    val accentColor: AppAccent,
    val settingsButtonX: Float,
    val settingsButtonY: Float,
    val catalogViewMode: LibraryViewMode,
    val favoritesViewMode: LibraryViewMode,
    val historyViewMode: LibraryViewMode
)

private data class LibrarySnapshot(
    val favorites: List<FavoriteEntry>,
    val watchlist: List<WatchEntry>,
    val progress: Map<String, ProgressEntry>,
    val recentSearches: List<SearchEntry>,
    val episodeStates: Map<String, EpisodeWatchState>
)

private data class ExtendedLibrarySnapshot(
    val library: LibrarySnapshot,
    val seasonTotals: Map<String, Int>,
    val watchedOrder: List<String>
)

class AppStore(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.get(context)
) {
    private val dao = database.libraryDao()
    private val metadataDao = database.seriesMetadataDao()

    private val settingsFlow: Flow<SettingsSnapshot> = context.aniWorldDataStore.data
        .catch { error ->
            if (error is IOException) {
                AppLogger.error("Speicher", "Einstellungen konnten nicht gelesen werden", error)
                emit(emptyPreferences())
            } else throw error
        }
        .map { prefs ->
            SettingsSnapshot(
                languagePriority = parseLanguagePriority(prefs[LANG_PRIORITY]),
                hosterPriority = parseHosterPriority(prefs[HOSTER_PRIORITY]),
                verifyStreams = prefs[VERIFY_STREAMS] ?: true,
                favoriteSort = parseSort(prefs[FAVORITE_SORT], LibrarySort.CUSTOM),
                watchedSort = parseSort(prefs[WATCHED_SORT], LibrarySort.UPDATED),
                permissionIntroSeen = prefs[PERMISSION_INTRO_SEEN] ?: false,
                useDynamicColors = prefs[DYNAMIC_COLORS] ?: false,
                lastHomeTab = prefs[LAST_HOME_TAB] ?: "START",
                initialPreloadCompleted = prefs[INITIAL_PRELOAD_COMPLETED] ?: false,
                webAdBlockEnabled = prefs[WEB_ADBLOCK_ENABLED] ?: false,
                webFilterLists = parseFilterLists(prefs[WEB_FILTER_LISTS]),
                webSessionPanelExpanded = prefs[WEB_SESSION_PANEL_EXPANDED] ?: true,
                webMediaPanelExpanded = prefs[WEB_MEDIA_PANEL_EXPANDED] ?: true,
                autoNextEnabled = prefs[AUTO_NEXT_ENABLED] ?: true,
                autoPlayPreferredHoster = prefs[AUTO_PLAY_PREFERRED_HOSTER] ?: false,
                allowExternalPlayer = prefs[ALLOW_EXTERNAL_PLAYER] ?: false,
                startupTab = prefs[STARTUP_TAB] ?: "START",
                accentColor = prefs[ACCENT_COLOR]?.let { raw -> AppAccent.entries.firstOrNull { it.name == raw } } ?: AppAccent.RED,
                settingsButtonX = (prefs[SETTINGS_BUTTON_X] ?: 0.92f).coerceIn(0f, 1f),
                settingsButtonY = (prefs[SETTINGS_BUTTON_Y] ?: 0.72f).coerceIn(0f, 1f),
                catalogViewMode = parseViewMode(prefs[CATALOG_VIEW_MODE], LibraryViewMode.DETAILED),
                favoritesViewMode = parseViewMode(prefs[FAVORITES_VIEW_MODE], LibraryViewMode.DETAILED),
                historyViewMode = parseViewMode(prefs[HISTORY_VIEW_MODE], LibraryViewMode.DETAILED)
            )
        }

    private val libraryFlow: Flow<LibrarySnapshot> = combine(
        dao.observeFavorites(),
        dao.observeWatchlist(),
        dao.observeProgress(),
        dao.observeRecentSearches(),
        dao.observeEpisodeStates()
    ) { favorites, watchlist, progress, searches, episodeStates ->
        LibrarySnapshot(
            favorites = favorites.map(FavoriteEntity::toModel),
            watchlist = watchlist.map(WatchlistEntity::toModel),
            progress = progress.associate { it.seriesSlug to it.toModel() },
            recentSearches = searches.map(RecentSearchEntity::toModel),
            episodeStates = episodeStates.associate { it.key to it.toModel() }
        )
    }

    private val extendedLibraryFlow: Flow<ExtendedLibrarySnapshot> = combine(
        libraryFlow,
        dao.observeSeasonTotals(),
        dao.observeWatchedOrder()
    ) { library, totals, watchedOrder ->
        ExtendedLibrarySnapshot(
            library = library,
            seasonTotals = totals.associate { it.key to it.total },
            watchedOrder = watchedOrder.map { it.slug }
        )
    }

    val preferences: Flow<AppPreferences> = combine(settingsFlow, extendedLibraryFlow) { settings, data ->
        val favorites = data.library.favorites
        AppPreferences(
            languagePriority = settings.languagePriority,
            hosterPriority = settings.hosterPriority,
            verifyStreams = settings.verifyStreams,
            watchlist = data.library.watchlist,
            progress = data.library.progress,
            favorites = favorites,
            recentSearches = data.library.recentSearches,
            episodeWatchStates = data.library.episodeStates,
            seasonEpisodeTotals = data.seasonTotals,
            favoriteOrder = favorites.map { it.slug },
            watchedOrder = data.watchedOrder,
            favoriteSort = settings.favoriteSort,
            watchedSort = settings.watchedSort,
            permissionIntroSeen = settings.permissionIntroSeen,
            useDynamicColors = settings.useDynamicColors,
            lastHomeTab = settings.lastHomeTab,
            initialPreloadCompleted = settings.initialPreloadCompleted,
            webAdBlockEnabled = settings.webAdBlockEnabled,
            webFilterLists = settings.webFilterLists,
            webSessionPanelExpanded = settings.webSessionPanelExpanded,
            webMediaPanelExpanded = settings.webMediaPanelExpanded,
            autoNextEnabled = settings.autoNextEnabled,
            autoPlayPreferredHoster = settings.autoPlayPreferredHoster,
            allowExternalPlayer = settings.allowExternalPlayer,
            startupTab = settings.startupTab,
            accentColor = settings.accentColor,
            settingsButtonX = settings.settingsButtonX,
            settingsButtonY = settings.settingsButtonY,
            catalogViewMode = settings.catalogViewMode,
            favoritesViewMode = settings.favoritesViewMode,
            historyViewMode = settings.historyViewMode
        )
    }

    suspend fun migrateLegacyData() {
        val prefs = context.aniWorldDataStore.data.first()
        if (prefs[ROOM_MIGRATION_DONE] == true) return
        database.withTransaction {
            parseLegacyFavorites(prefs[LEGACY_FAVORITES_JSON]).forEachIndexed { index, entry ->
                dao.upsertFavorite(
                    FavoriteEntity(
                        slug = entry.slug,
                        title = entry.title,
                        url = entry.url,
                        coverUrl = entry.coverUrl,
                        description = entry.description,
                        genres = entry.genres.joinToString("\u001F"),
                        updatedAt = entry.updatedAt,
                        sortIndex = index
                    )
                )
            }
            dao.upsertWatchlist(parseLegacyWatchlist(prefs[LEGACY_WATCHLIST_JSON]).map {
                WatchlistEntity(it.slug, it.title, it.url, it.coverUrl, it.updatedAt)
            })
            dao.upsertProgress(parseLegacyProgress(prefs[LEGACY_PROGRESS_JSON]).values.map {
                ProgressEntity(it.seriesSlug, it.seriesTitle, it.seriesUrl, it.coverUrl, it.season, it.episode, it.episodeTitle, it.episodeUrl, it.updatedAt)
            })
            dao.upsertEpisodeStates(parseLegacyEpisodeStates(prefs[LEGACY_EPISODE_STATES_JSON]).values.map { it.toEntity() })
            dao.upsertSeasonTotals(parseLegacyIntMap(prefs[LEGACY_SEASON_TOTALS_JSON]).map { SeasonTotalEntity(it.key, it.value) })
            dao.upsertSearches(parseLegacySearches(prefs[LEGACY_RECENT_SEARCHES_JSON]).map { RecentSearchEntity(it.query, it.updatedAt) })
            val order = parseLegacyStringList(prefs[LEGACY_WATCHED_ORDER])
            dao.upsertWatchedOrders(order.mapIndexed { index, slug -> WatchedOrderEntity(slug, index) })
        }
        context.aniWorldDataStore.edit { mutable ->
            mutable[ROOM_MIGRATION_DONE] = true
            mutable.remove(LEGACY_WATCHLIST_JSON)
            mutable.remove(LEGACY_PROGRESS_JSON)
            mutable.remove(LEGACY_FAVORITES_JSON)
            mutable.remove(LEGACY_RECENT_SEARCHES_JSON)
            mutable.remove(LEGACY_EPISODE_STATES_JSON)
            mutable.remove(LEGACY_SEASON_TOTALS_JSON)
            mutable.remove(LEGACY_FAVORITE_ORDER)
            mutable.remove(LEGACY_WATCHED_ORDER)
        }
        AppLogger.info("Speicher", "Lokale Daten in Room übernommen")
    }

    suspend fun setLanguagePriority(priority: List<Language>) = editSafely("Sprache speichern") {
        it[LANG_PRIORITY] = priority.joinToString(",") { lang -> lang.token }
    }

    suspend fun setHosterPriority(priority: List<String>) = editSafely("Hoster speichern") {
        it[HOSTER_PRIORITY] = priority.joinToString(",")
    }

    suspend fun setVerifyStreams(enabled: Boolean) = editSafely("Stream-Prüfung speichern") { it[VERIFY_STREAMS] = enabled }
    suspend fun setPermissionIntroSeen() = editSafely("Berechtigungsinfo speichern") { it[PERMISSION_INTRO_SEEN] = true }
    suspend fun setFavoriteSort(sort: LibrarySort) = editSafely("Favoriten-Sortierung speichern") { it[FAVORITE_SORT] = sort.name }
    suspend fun setWatchedSort(sort: LibrarySort) = editSafely("Verlauf-Sortierung speichern") { it[WATCHED_SORT] = sort.name }
    suspend fun setDynamicColors(enabled: Boolean) = editSafely("Farbschema speichern") { it[DYNAMIC_COLORS] = enabled }
    suspend fun setLastHomeTab(tab: String) = editSafely("Navigation speichern") { it[LAST_HOME_TAB] = tab }
    suspend fun setInitialPreloadCompleted() = editSafely("Erstinitialisierung speichern") { it[INITIAL_PRELOAD_COMPLETED] = true }
    suspend fun setWebAdBlockEnabled(enabled: Boolean) = editSafely("Webfilter speichern") { it[WEB_ADBLOCK_ENABLED] = enabled }
    suspend fun setWebFilterLists(ids: Set<String>) = editSafely("Filterlisten speichern") {
        it[WEB_FILTER_LISTS] = ids.intersect(WebFilterList.ALL_IDS).sorted().joinToString(",")
    }
    suspend fun setWebSessionPanelExpanded(expanded: Boolean) = editSafely("Webbereich speichern") {
        it[WEB_SESSION_PANEL_EXPANDED] = expanded
    }
    suspend fun setWebMediaPanelExpanded(expanded: Boolean) = editSafely("Medienbereich speichern") {
        it[WEB_MEDIA_PANEL_EXPANDED] = expanded
    }
    suspend fun setAutoNextEnabled(enabled: Boolean) = editSafely("Auto-Next speichern") {
        it[AUTO_NEXT_ENABLED] = enabled
    }
    suspend fun setAutoPlayPreferredHoster(enabled: Boolean) = editSafely("Automatischen Hosterstart speichern") {
        it[AUTO_PLAY_PREFERRED_HOSTER] = enabled
    }
    suspend fun setAllowExternalPlayer(enabled: Boolean) = editSafely("Externen Player speichern") {
        it[ALLOW_EXTERNAL_PLAYER] = enabled
    }
    suspend fun setStartupTab(tab: String) = editSafely("Startbereich speichern") {
        it[STARTUP_TAB] = tab
    }
    suspend fun setAccentColor(accent: AppAccent) = editSafely("Akzentfarbe speichern") {
        it[ACCENT_COLOR] = accent.name
        it[DYNAMIC_COLORS] = false
    }
    suspend fun setSettingsButtonPosition(x: Float, y: Float) = editSafely("Einstellungsposition speichern") {
        it[SETTINGS_BUTTON_X] = x.coerceIn(0f, 1f)
        it[SETTINGS_BUTTON_Y] = y.coerceIn(0f, 1f)
    }
    suspend fun resetSettingsButtonPosition() = setSettingsButtonPosition(0.92f, 0.72f)

    suspend fun clearStoredCovers() = database.withTransaction {
        dao.clearFavoriteCovers()
        dao.clearWatchlistCovers()
        dao.clearProgressCovers()
        dao.clearEpisodeStateCovers()
    }

    suspend fun setCatalogViewMode(mode: LibraryViewMode) = editSafely("Katalogansicht speichern") {
        it[CATALOG_VIEW_MODE] = mode.name
    }
    suspend fun setFavoritesViewMode(mode: LibraryViewMode) = editSafely("Favoritenansicht speichern") {
        it[FAVORITES_VIEW_MODE] = mode.name
    }
    suspend fun setHistoryViewMode(mode: LibraryViewMode) = editSafely("Verlaufsansicht speichern") {
        it[HISTORY_VIEW_MODE] = mode.name
    }

    suspend fun rememberSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        dao.upsertSearch(RecentSearchEntity(clean, System.currentTimeMillis()))
        dao.trimSearches()
    }

    suspend fun clearRecentSearches() = dao.clearSearches()

    suspend fun rememberSeries(series: Series) {
        database.withTransaction {
            dao.upsertWatchlist(WatchlistEntity(series.slug, series.title, series.url, series.coverUrl, System.currentTimeMillis()))
            dao.trimWatchlist()
            ensureOfflineMetadataLocked(series)
        }
    }

    suspend fun removeSeries(slug: String) = dao.deleteWatchlist(slug)

    suspend fun toggleFavorite(series: Series) {
        database.withTransaction {
            val existing = dao.favorite(series.slug)
            if (existing != null) {
                dao.deleteFavorite(series.slug)
            } else {
                val current = dao.favoritesNow()
                current.forEachIndexed { index, item -> dao.updateFavoriteOrder(item.slug, index + 1) }
                dao.upsertFavorite(FavoriteEntity.from(series, sortIndex = 0))
                ensureOfflineMetadataLocked(series)
            }
        }
    }

    suspend fun mergeSeriesMetadata(series: Series) {
        database.withTransaction {
            val genres = series.genres.filter(String::isNotBlank).distinct().joinToString("\u001F")
            dao.updateFavoriteMetadata(series.slug, series.title, series.url, series.coverUrl, series.description, genres)
            dao.updateWatchlistMetadata(series.slug, series.title, series.url, series.coverUrl)
            dao.updateProgressMetadata(series.slug, series.title, series.url, series.coverUrl)
            dao.updateEpisodeMetadata(series.slug, series.title, series.url, series.coverUrl)
            ensureOfflineMetadataLocked(series)
        }
    }

    suspend fun ensureOfflineMetadata(series: Series) {
        database.withTransaction { ensureOfflineMetadataLocked(series) }
    }

    private suspend fun ensureOfflineMetadataLocked(series: Series) {
        if (series.slug.isBlank() || series.title.isBlank()) return
        val existing = metadataDao.get(series.slug)?.toModel()
        val merged = if (existing == null) series else existing.copy(
            title = series.title.ifBlank { existing.title },
            url = series.url.ifBlank { existing.url },
            description = series.description.ifBlank { existing.description },
            coverUrl = series.coverUrl.ifBlank { existing.coverUrl },
            genres = (existing.genres + series.genres).filter(String::isNotBlank).distinct(),
            year = series.year.ifBlank { existing.year },
            ageRating = series.ageRating.ifBlank { existing.ageRating }
        )
        metadataDao.upsert(SeriesMetadataEntity.from(merged))
    }

    suspend fun moveFavorite(slug: String, delta: Int) {
        dao.moveFavorite(slug, delta)
        setFavoriteSort(LibrarySort.CUSTOM)
    }

    suspend fun moveWatched(slug: String, delta: Int) {
        dao.moveWatched(slug, delta)
        setWatchedSort(LibrarySort.CUSTOM)
    }

    suspend fun removeFavorites(slugs: Set<String>) {
        if (slugs.isEmpty()) return
        database.withTransaction { slugs.forEach { dao.deleteFavorite(it) } }
    }

    suspend fun removeWatchedSeries(slug: String) = removeWatchedSeries(setOf(slug))

    suspend fun removeWatchedSeries(slugs: Set<String>) {
        if (slugs.isEmpty()) return
        database.withTransaction {
            slugs.forEach { slug ->
                dao.deleteEpisodeStatesForSeries(slug)
                dao.deleteProgress(slug)
                dao.deleteWatchedOrder(slug)
            }
        }
    }

    suspend fun rememberSeasonTotal(seriesSlug: String, season: Int, total: Int) {
        if (total > 0) dao.upsertSeasonTotal(SeasonTotalEntity(seasonKey(seriesSlug, season), total))
    }

    suspend fun rememberProgress(series: Series, episode: Episode) {
        database.withTransaction {
            writeProgress(series, episode, System.currentTimeMillis())
            ensureOfflineMetadataLocked(series)
            touchWatchedOrder(series.slug)
        }
    }

    suspend fun savePlaybackProgress(
        series: Series,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean
    ) {
        val now = System.currentTimeMillis()
        val finalCompleted = completed || (durationMs > 0L && positionMs >= (durationMs * 0.90).toLong())
        database.withTransaction {
            dao.upsertEpisodeState(
                EpisodeStateEntity(
                    key = episode.key,
                    seriesTitle = series.title,
                    seriesSlug = series.slug,
                    seriesUrl = series.url,
                    coverUrl = series.coverUrl,
                    season = episode.season,
                    episode = episode.number,
                    episodeTitle = episode.title.ifBlank { episode.secondaryTitle },
                    episodeUrl = episode.url,
                    positionMs = if (finalCompleted) durationMs.coerceAtLeast(positionMs) else positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    completed = finalCompleted,
                    updatedAt = now
                )
            )
            writeProgress(series, episode, now)
            ensureOfflineMetadataLocked(series)
            touchWatchedOrder(series.slug)
        }
    }

    suspend fun setEpisodeWatched(series: Series, episode: Episode, watched: Boolean) {
        database.withTransaction {
            val current = dao.episodeState(episode.key)
            if (watched) {
                dao.upsertEpisodeState(
                    EpisodeStateEntity(
                        key = episode.key,
                        seriesTitle = series.title,
                        seriesSlug = series.slug,
                        seriesUrl = series.url,
                        coverUrl = series.coverUrl,
                        season = episode.season,
                        episode = episode.number,
                        episodeTitle = episode.title.ifBlank { episode.secondaryTitle },
                        episodeUrl = episode.url,
                        // Keep the real last playback position. `completed` alone controls the UI state.
                        positionMs = current?.positionMs ?: 0L,
                        durationMs = current?.durationMs ?: 0L,
                        completed = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                ensureOfflineMetadataLocked(series)
                touchWatchedOrder(series.slug)
            } else if (current != null && (current.positionMs > 0L || current.durationMs > 0L)) {
                val restoredPosition = if (current.durationMs > 0L && current.positionMs >= current.durationMs) {
                    (current.durationMs * 0.89).toLong()
                } else current.positionMs
                dao.upsertEpisodeState(
                    current.copy(
                        positionMs = restoredPosition.coerceAtLeast(0L),
                        completed = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                touchWatchedOrder(series.slug)
            } else {
                dao.deleteEpisodeState(episode.key)
                if (dao.episodeStateCount(series.slug) == 0) dao.deleteWatchedOrder(series.slug)
            }
        }
    }

    suspend fun setSeasonWatched(series: Series, episodes: List<Episode>, watched: Boolean) {
        database.withTransaction {
            episodes.forEach { episode ->
                if (watched) {
                    val current = dao.episodeState(episode.key)
                    dao.upsertEpisodeState(
                        EpisodeStateEntity(
                            key = episode.key,
                            seriesTitle = series.title,
                            seriesSlug = series.slug,
                            seriesUrl = series.url,
                            coverUrl = series.coverUrl,
                            season = episode.season,
                            episode = episode.number,
                            episodeTitle = episode.title.ifBlank { episode.secondaryTitle },
                            episodeUrl = episode.url,
                            positionMs = current?.durationMs ?: current?.positionMs ?: 0L,
                            durationMs = current?.durationMs ?: 0L,
                            completed = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.deleteEpisodeState(episode.key)
                }
            }
            if (watched) {
                ensureOfflineMetadataLocked(series)
                touchWatchedOrder(series.slug)
            }
            else if (dao.episodeStateCount(series.slug) == 0) dao.deleteWatchedOrder(series.slug)
        }
    }

    private suspend fun writeProgress(series: Series, episode: Episode, now: Long) {
        dao.upsertProgress(
            ProgressEntity(
                seriesSlug = series.slug,
                seriesTitle = series.title,
                seriesUrl = series.url,
                coverUrl = series.coverUrl,
                season = episode.season,
                episode = episode.number,
                episodeTitle = episode.title.ifBlank { episode.secondaryTitle },
                episodeUrl = episode.url,
                updatedAt = now
            )
        )
        dao.upsertWatchlist(WatchlistEntity(series.slug, series.title, series.url, series.coverUrl, now))
        dao.trimWatchlist()
    }

    private suspend fun touchWatchedOrder(slug: String) {
        val current = dao.watchedOrderNow().filterNot { it.slug == slug }
        dao.upsertWatchedOrder(WatchedOrderEntity(slug, 0))
        current.forEachIndexed { index, item -> dao.upsertWatchedOrder(item.copy(sortIndex = index + 1)) }
    }

    private suspend fun editSafely(action: String, block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.aniWorldDataStore.edit { block(it) }
        } catch (error: Exception) {
            AppLogger.error("Speicher", "$action fehlgeschlagen", error)
            throw error
        }
    }

    private fun parseLanguagePriority(raw: String?): List<Language> {
        val parsed = raw.orEmpty().split(',')
            .mapNotNull { Language.fromToken(it.trim()).takeIf { lang -> lang != Language.UNKNOWN } }
            .distinct()
        return (parsed + Language.DEFAULT_PRIORITY.filterNot(parsed::contains)).ifEmpty { Language.DEFAULT_PRIORITY }
    }

    private fun parseHosterPriority(raw: String?): List<String> = raw.orEmpty().split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { HosterCatalog.DEFAULT_PRIORITY }

    private fun parseSort(raw: String?, default: LibrarySort): LibrarySort =
        runCatching { LibrarySort.valueOf(raw.orEmpty()) }.getOrDefault(default)

    private fun parseViewMode(raw: String?, default: LibraryViewMode): LibraryViewMode =
        runCatching { LibraryViewMode.valueOf(raw.orEmpty()) }.getOrDefault(default)

    private fun parseFilterLists(raw: String?): Set<String> = raw.orEmpty().split(',')
        .map(String::trim)
        .filter { it in WebFilterList.ALL_IDS }
        .toSet()
        .ifEmpty { WebFilterList.ALL_IDS }

    private fun parseLegacyWatchlist(raw: String?): List<WatchEntry> = parseArray(raw) { o ->
        WatchEntry(o.optString("title"), o.optString("slug"), o.optString("url"), o.optString("coverUrl"), o.optLong("updatedAt"))
    }.filter { it.title.isNotBlank() && it.slug.isNotBlank() && it.url.isNotBlank() }

    private fun parseLegacyFavorites(raw: String?): List<FavoriteEntry> = parseArray(raw) { o ->
        FavoriteEntry(
            o.optString("title"),
            o.optString("slug"),
            o.optString("url"),
            o.optString("coverUrl"),
            o.optString("description"),
            o.optJSONArray("genres")?.let { array -> buildList { for (i in 0 until array.length()) add(array.optString(i)) } }.orEmpty(),
            o.optLong("updatedAt")
        )
    }.filter { it.title.isNotBlank() && it.slug.isNotBlank() && it.url.isNotBlank() }

    private fun parseLegacySearches(raw: String?): List<SearchEntry> = parseArray(raw) {
        SearchEntry(it.optString("query"), it.optLong("updatedAt"))
    }.filter { it.query.isNotBlank() }

    private fun parseLegacyProgress(raw: String?): Map<String, ProgressEntry> = parseObjectMap(raw) { o ->
        ProgressEntry(
            o.optString("seriesTitle"), o.optString("seriesSlug"), o.optString("seriesUrl"), o.optString("coverUrl"),
            o.optInt("season"), o.optInt("episode"), o.optString("episodeTitle"), o.optString("episodeUrl"), o.optLong("updatedAt")
        )
    }

    private fun parseLegacyEpisodeStates(raw: String?): Map<String, EpisodeWatchState> = parseObjectMap(raw) { o ->
        EpisodeWatchState(
            o.optString("seriesTitle"), o.optString("seriesSlug"), o.optString("seriesUrl"), o.optString("coverUrl"),
            o.optInt("season"), o.optInt("episode"), o.optString("episodeTitle"), o.optString("episodeUrl"),
            o.optLong("positionMs"), o.optLong("durationMs"), o.optBoolean("completed"), o.optLong("updatedAt")
        )
    }

    private fun parseLegacyIntMap(raw: String?): Map<String, Int> = if (raw.isNullOrBlank()) emptyMap() else runCatching {
        val root = JSONObject(raw)
        buildMap { root.keys().forEach { put(it, root.optInt(it)) } }
    }.getOrDefault(emptyMap())

    private fun parseLegacyStringList(raw: String?): List<String> = if (raw.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(raw)
        buildList { for (i in 0 until array.length()) add(array.optString(i)) }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    private inline fun <T> parseArray(raw: String?, mapper: (JSONObject) -> T): List<T> = if (raw.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(raw)
        buildList { for (i in 0 until array.length()) add(mapper(array.getJSONObject(i))) }
    }.getOrDefault(emptyList())

    private inline fun <T> parseObjectMap(raw: String?, mapper: (JSONObject) -> T): Map<String, T> = if (raw.isNullOrBlank()) emptyMap() else runCatching {
        val root = JSONObject(raw)
        buildMap { root.keys().forEach { key -> put(key, mapper(root.getJSONObject(key))) } }
    }.getOrDefault(emptyMap())

    companion object {
        private val LANG_PRIORITY = stringPreferencesKey("language_priority")
        private val HOSTER_PRIORITY = stringPreferencesKey("hoster_priority")
        private val VERIFY_STREAMS = booleanPreferencesKey("verify_streams")
        private val FAVORITE_SORT = stringPreferencesKey("favorite_sort")
        private val WATCHED_SORT = stringPreferencesKey("watched_sort")
        private val PERMISSION_INTRO_SEEN = booleanPreferencesKey("permission_intro_seen")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        private val LAST_HOME_TAB = stringPreferencesKey("last_home_tab")
        private val INITIAL_PRELOAD_COMPLETED = booleanPreferencesKey("initial_preload_completed")
        private val WEB_ADBLOCK_ENABLED = booleanPreferencesKey("web_adblock_enabled")
        private val WEB_FILTER_LISTS = stringPreferencesKey("web_filter_lists")
        private val WEB_SESSION_PANEL_EXPANDED = booleanPreferencesKey("web_session_panel_expanded")
        private val WEB_MEDIA_PANEL_EXPANDED = booleanPreferencesKey("web_media_panel_expanded")
        private val AUTO_NEXT_ENABLED = booleanPreferencesKey("auto_next_enabled")
        private val AUTO_PLAY_PREFERRED_HOSTER = booleanPreferencesKey("auto_play_preferred_hoster")
        private val ALLOW_EXTERNAL_PLAYER = booleanPreferencesKey("allow_external_player")
        private val STARTUP_TAB = stringPreferencesKey("startup_tab")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val SETTINGS_BUTTON_X = floatPreferencesKey("settings_button_x")
        private val SETTINGS_BUTTON_Y = floatPreferencesKey("settings_button_y")
        private val CATALOG_VIEW_MODE = stringPreferencesKey("catalog_view_mode")
        private val FAVORITES_VIEW_MODE = stringPreferencesKey("favorites_view_mode")
        private val HISTORY_VIEW_MODE = stringPreferencesKey("history_view_mode")
        private val ROOM_MIGRATION_DONE = booleanPreferencesKey("room_migration_done")

        private val LEGACY_WATCHLIST_JSON = stringPreferencesKey("watchlist_json")
        private val LEGACY_PROGRESS_JSON = stringPreferencesKey("progress_json")
        private val LEGACY_FAVORITES_JSON = stringPreferencesKey("favorites_json")
        private val LEGACY_RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
        private val LEGACY_EPISODE_STATES_JSON = stringPreferencesKey("episode_states_json")
        private val LEGACY_SEASON_TOTALS_JSON = stringPreferencesKey("season_totals_json")
        private val LEGACY_FAVORITE_ORDER = stringPreferencesKey("favorite_order")
        private val LEGACY_WATCHED_ORDER = stringPreferencesKey("watched_order")
    }
}

private fun EpisodeWatchState.toEntity() = EpisodeStateEntity(
    key = key,
    seriesTitle = seriesTitle,
    seriesSlug = seriesSlug,
    seriesUrl = seriesUrl,
    coverUrl = coverUrl,
    season = season,
    episode = episode,
    episodeTitle = episodeTitle,
    episodeUrl = episodeUrl,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    updatedAt = updatedAt
)
