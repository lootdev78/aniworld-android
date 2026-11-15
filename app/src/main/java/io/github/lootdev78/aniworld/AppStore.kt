package io.github.lootdev78.aniworld

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val notificationPermissionAsked: Boolean,
    val useDynamicColors: Boolean,
    val lastHomeTab: String,
    val initialPreloadCompleted: Boolean,
    val webAdBlockEnabled: Boolean,
    val webFilterLists: Set<String>,
    val webSessionPanelExpanded: Boolean,
    val webMediaPanelExpanded: Boolean,
    val autoNextEnabled: Boolean,
    val autoNextDelaySeconds: Int,
    val autoPlayPreferredHoster: Boolean,
    val allowExternalPlayer: Boolean,
    val autoSkipOpening: Boolean,
    val autoSkipEnding: Boolean,
    val autoSkipRecap: Boolean,
    val showSkipButtons: Boolean,
    val skipOffsetSeconds: Float,
    val playAfterSkip: Boolean,
    val rememberPlaybackPosition: Boolean,
    val playbackPositionExpiryDays: Int,
    val rememberVolume: Boolean,
    val savedVolumePercent: Int,
    val aniSkipMappings: Map<String, AniSkipAnimeMapping>,
    val aniSkipMappingMode: AniSkipMappingMode,
    val startupTab: String,
    val homeOfflineMode: Boolean,
    val accentColor: AppAccent,
    val customAccentColor: Int,
    val settingsButtonX: Float,
    val settingsButtonY: Float,
    val catalogViewMode: LibraryViewMode,
    val favoritesViewMode: LibraryViewMode,
    val historyViewMode: LibraryViewMode,
    val diagnosticsEnabled: Boolean,
    val castEnabled: Boolean,
    val pipEnabled: Boolean,
    val highlightStartedSeasons: Boolean,
    val startedSeasonHighlightAlpha: Float,
    val homeSectionOrder: List<HomeSection>,
    val hiddenHomeSections: Set<HomeSection>
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
                notificationPermissionAsked = prefs[NOTIFICATION_PERMISSION_ASKED] ?: false,
                useDynamicColors = prefs[DYNAMIC_COLORS] ?: false,
                lastHomeTab = prefs[LAST_HOME_TAB] ?: "START",
                initialPreloadCompleted = prefs[INITIAL_PRELOAD_COMPLETED] ?: false,
                webAdBlockEnabled = prefs[WEB_ADBLOCK_ENABLED] ?: false,
                webFilterLists = parseFilterLists(prefs[WEB_FILTER_LISTS]),
                webSessionPanelExpanded = prefs[WEB_SESSION_PANEL_EXPANDED] ?: true,
                webMediaPanelExpanded = prefs[WEB_MEDIA_PANEL_EXPANDED] ?: true,
                autoNextEnabled = prefs[AUTO_NEXT_ENABLED] ?: true,
                autoNextDelaySeconds = (prefs[AUTO_NEXT_DELAY_SECONDS] ?: 8).coerceIn(0, 30),
                autoPlayPreferredHoster = prefs[AUTO_PLAY_PREFERRED_HOSTER] ?: false,
                allowExternalPlayer = prefs[ALLOW_EXTERNAL_PLAYER] ?: false,
                autoSkipOpening = prefs[AUTO_SKIP_OPENING] ?: false,
                autoSkipEnding = prefs[AUTO_SKIP_ENDING] ?: false,
                autoSkipRecap = prefs[AUTO_SKIP_RECAP] ?: false,
                showSkipButtons = prefs[SHOW_SKIP_BUTTONS] ?: true,
                skipOffsetSeconds = (prefs[SKIP_OFFSET_SECONDS] ?: 0f).coerceIn(0f, 5f),
                playAfterSkip = prefs[PLAY_AFTER_SKIP] ?: true,
                rememberPlaybackPosition = prefs[REMEMBER_PLAYBACK_POSITION] ?: true,
                playbackPositionExpiryDays = (prefs[PLAYBACK_POSITION_EXPIRY_DAYS] ?: 7).coerceIn(1, 30),
                rememberVolume = prefs[REMEMBER_VOLUME] ?: true,
                savedVolumePercent = (prefs[SAVED_VOLUME_PERCENT] ?: -1).coerceIn(-1, 100),
                aniSkipMappings = parseAniSkipMappings(prefs[ANISKIP_MAPPINGS]),
                aniSkipMappingMode = prefs[ANISKIP_MAPPING_MODE]?.let { raw ->
                    AniSkipMappingMode.entries.firstOrNull { it.name == raw }
                } ?: AniSkipMappingMode.AUTOMATIC,
                startupTab = prefs[STARTUP_TAB] ?: "START",
                homeOfflineMode = prefs[HOME_OFFLINE_MODE] ?: false,
                accentColor = prefs[ACCENT_COLOR]?.let { raw -> AppAccent.entries.firstOrNull { it.name == raw } } ?: AppAccent.RED,
                customAccentColor = prefs[CUSTOM_ACCENT_COLOR] ?: 0xFFFF4655.toInt(),
                settingsButtonX = (prefs[SETTINGS_BUTTON_X] ?: 0.92f).coerceIn(0f, 1f),
                settingsButtonY = (prefs[SETTINGS_BUTTON_Y] ?: 0.72f).coerceIn(0f, 1f),
                catalogViewMode = parseViewMode(prefs[CATALOG_VIEW_MODE], LibraryViewMode.DETAILED),
                favoritesViewMode = parseViewMode(prefs[FAVORITES_VIEW_MODE], LibraryViewMode.DETAILED),
                historyViewMode = parseViewMode(prefs[HISTORY_VIEW_MODE], LibraryViewMode.DETAILED),
                diagnosticsEnabled = prefs[DIAGNOSTICS_ENABLED] ?: true,
                castEnabled = prefs[CAST_ENABLED] ?: true,
                pipEnabled = prefs[PIP_ENABLED] ?: true,
                highlightStartedSeasons = prefs[HIGHLIGHT_STARTED_SEASONS] ?: true,
                startedSeasonHighlightAlpha = (prefs[STARTED_SEASON_HIGHLIGHT_ALPHA] ?: 0.22f).coerceIn(0.08f, 0.60f),
                homeSectionOrder = HomeSection.normalizeOrder(parseStoredList(prefs[HOME_SECTION_ORDER])),
                hiddenHomeSections = parseStoredList(prefs[HIDDEN_HOME_SECTIONS]).mapNotNull { raw -> HomeSection.entries.firstOrNull { it.name == raw } }.toSet()
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
            notificationPermissionAsked = settings.notificationPermissionAsked,
            useDynamicColors = settings.useDynamicColors,
            lastHomeTab = settings.lastHomeTab,
            initialPreloadCompleted = settings.initialPreloadCompleted,
            webAdBlockEnabled = settings.webAdBlockEnabled,
            webFilterLists = settings.webFilterLists,
            webSessionPanelExpanded = settings.webSessionPanelExpanded,
            webMediaPanelExpanded = settings.webMediaPanelExpanded,
            autoNextEnabled = settings.autoNextEnabled,
            autoNextDelaySeconds = settings.autoNextDelaySeconds,
            autoPlayPreferredHoster = settings.autoPlayPreferredHoster,
            allowExternalPlayer = settings.allowExternalPlayer,
            autoSkipOpening = settings.autoSkipOpening,
            autoSkipEnding = settings.autoSkipEnding,
            autoSkipRecap = settings.autoSkipRecap,
            showSkipButtons = settings.showSkipButtons,
            skipOffsetSeconds = settings.skipOffsetSeconds,
            playAfterSkip = settings.playAfterSkip,
            rememberPlaybackPosition = settings.rememberPlaybackPosition,
            playbackPositionExpiryDays = settings.playbackPositionExpiryDays,
            rememberVolume = settings.rememberVolume,
            savedVolumePercent = settings.savedVolumePercent,
            aniSkipMappings = settings.aniSkipMappings,
            aniSkipMappingMode = settings.aniSkipMappingMode,
            startupTab = settings.startupTab,
            homeOfflineMode = settings.homeOfflineMode,
            accentColor = settings.accentColor,
            customAccentColor = settings.customAccentColor,
            settingsButtonX = settings.settingsButtonX,
            settingsButtonY = settings.settingsButtonY,
            catalogViewMode = settings.catalogViewMode,
            favoritesViewMode = settings.favoritesViewMode,
            historyViewMode = settings.historyViewMode,
            diagnosticsEnabled = settings.diagnosticsEnabled,
            castEnabled = settings.castEnabled,
            pipEnabled = settings.pipEnabled,
            highlightStartedSeasons = settings.highlightStartedSeasons,
            startedSeasonHighlightAlpha = settings.startedSeasonHighlightAlpha,
            homeSectionOrder = settings.homeSectionOrder,
            hiddenHomeSections = settings.hiddenHomeSections
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
    suspend fun setNotificationPermissionAsked() = editSafely("Benachrichtigungsabfrage speichern") { it[NOTIFICATION_PERMISSION_ASKED] = true }
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
    suspend fun setAutoNextDelaySeconds(seconds: Int) = editSafely("Auto-Next-Verzögerung speichern") {
        it[AUTO_NEXT_DELAY_SECONDS] = seconds.coerceIn(0, 30)
    }
    suspend fun setAutoSkipOpening(enabled: Boolean) = editSafely("Opening-Auto-Skip speichern") { it[AUTO_SKIP_OPENING] = enabled }
    suspend fun setAutoSkipEnding(enabled: Boolean) = editSafely("Ending-Auto-Skip speichern") { it[AUTO_SKIP_ENDING] = enabled }
    suspend fun setAutoSkipRecap(enabled: Boolean) = editSafely("Recap-Auto-Skip speichern") { it[AUTO_SKIP_RECAP] = enabled }
    suspend fun setShowSkipButtons(enabled: Boolean) = editSafely("Skip-Schaltflächen speichern") { it[SHOW_SKIP_BUTTONS] = enabled }
    suspend fun setSkipOffsetSeconds(seconds: Float) = editSafely("Skip-Offset speichern") { it[SKIP_OFFSET_SECONDS] = seconds.coerceIn(0f, 5f) }
    suspend fun setPlayAfterSkip(enabled: Boolean) = editSafely("Wiedergabe nach Skip speichern") { it[PLAY_AFTER_SKIP] = enabled }
    suspend fun setRememberPlaybackPosition(enabled: Boolean) = editSafely("Wiedergabeposition merken speichern") { it[REMEMBER_PLAYBACK_POSITION] = enabled }
    suspend fun setPlaybackPositionExpiryDays(days: Int) = editSafely("Ablauf der Wiedergabeposition speichern") { it[PLAYBACK_POSITION_EXPIRY_DAYS] = days.coerceIn(1, 30) }
    suspend fun setRememberVolume(enabled: Boolean) = editSafely("Lautstärke merken speichern") { it[REMEMBER_VOLUME] = enabled }
    suspend fun setSavedVolumePercent(percent: Int) = editSafely("Player-Lautstärke speichern") { it[SAVED_VOLUME_PERCENT] = percent.coerceIn(0, 100) }

    suspend fun setAniSkipMapping(key: String, mapping: AniSkipAnimeMapping?) = editSafely("AniSkip-Anime-Zuordnung speichern") { prefs ->
        if (key.isBlank()) return@editSafely
        val updated = parseAniSkipMappings(prefs[ANISKIP_MAPPINGS]).toMutableMap()
        if (mapping == null) updated.remove(key) else updated[key] = mapping
        prefs[ANISKIP_MAPPINGS] = JSONObject().apply {
            updated.forEach { (mapKey, value) ->
                put(mapKey, JSONObject().put("malId", value.malId).put("title", value.title).put("url", value.url))
            }
        }.toString()
    }
    suspend fun setAniSkipMappingMode(mode: AniSkipMappingMode) = editSafely("AniSkip-Zuordnungsmodus speichern") {
        it[ANISKIP_MAPPING_MODE] = mode.name
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
    suspend fun setHomeOfflineMode(enabled: Boolean) = editSafely("Startseitenmodus speichern") {
        it[HOME_OFFLINE_MODE] = enabled
    }
    suspend fun setAccentColor(accent: AppAccent) = editSafely("Akzentfarbe speichern") {
        it[ACCENT_COLOR] = accent.name
        it.remove(CUSTOM_ACCENT_COLOR)
        it[DYNAMIC_COLORS] = false
    }
    suspend fun setCustomAccentColor(color: Int) = editSafely("Benutzerdefinierte Akzentfarbe speichern") {
        it[ACCENT_COLOR] = AppAccent.CUSTOM.name
        it[CUSTOM_ACCENT_COLOR] = color
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
    suspend fun setDiagnosticsEnabled(enabled: Boolean) = editSafely("Diagnoseeinstellung speichern") {
        it[DIAGNOSTICS_ENABLED] = enabled
    }
    suspend fun setCastEnabled(enabled: Boolean) = editSafely("Cast-Einstellung speichern") {
        it[CAST_ENABLED] = enabled
    }
    suspend fun setPipEnabled(enabled: Boolean) = editSafely("PiP-Einstellung speichern") {
        it[PIP_ENABLED] = enabled
    }
    suspend fun setHighlightStartedSeasons(enabled: Boolean) = editSafely("Staffel-Hervorhebung speichern") {
        it[HIGHLIGHT_STARTED_SEASONS] = enabled
    }
    suspend fun setStartedSeasonHighlightAlpha(alpha: Float) = editSafely("Staffel-Hervorhebungsstärke speichern") {
        it[STARTED_SEASON_HIGHLIGHT_ALPHA] = alpha.coerceIn(0.08f, 0.60f)
    }
    suspend fun setHomeSectionVisible(section: HomeSection, visible: Boolean) = editSafely("Startseitenbereich speichern") { prefs ->
        val hidden = parseStoredList(prefs[HIDDEN_HOME_SECTIONS]).toMutableSet()
        if (visible) hidden.remove(section.name) else hidden.add(section.name)
        prefs[HIDDEN_HOME_SECTIONS] = hidden.sorted().joinToString(",")
    }
    suspend fun moveHomeSection(section: HomeSection, delta: Int) = editSafely("Startseitenreihenfolge speichern") { prefs ->
        val order = HomeSection.normalizeOrder(parseStoredList(prefs[HOME_SECTION_ORDER])).toMutableList()
        val from = order.indexOf(section)
        if (from < 0 || order.isEmpty()) return@editSafely
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from != to) order.add(to, order.removeAt(from))
        prefs[HOME_SECTION_ORDER] = order.joinToString(",") { it.name }
    }
    suspend fun setHomeSectionOrder(order: List<HomeSection>) = editSafely("Startseitenreihenfolge speichern") { prefs ->
        prefs[HOME_SECTION_ORDER] = HomeSection.normalizeOrder(order.map(HomeSection::name)).joinToString(",") { it.name }
    }

    suspend fun offlineMetadataCount(): Int = metadataDao.count()

    suspend fun offlineMetadataSlugs(): Set<String> = metadataDao.all().mapTo(mutableSetOf(), SeriesMetadataEntity::slug)

    suspend fun removeNewIncompleteOfflineMetadata(downloadedSlugs: Set<String>, originalSlugs: Set<String>) = withContext(Dispatchers.IO) {
        val removable = metadataDao.all().asSequence()
            .filter { it.slug in downloadedSlugs && it.slug !in originalSlugs }
            .filterNot { it.toModel().hasCompleteOfflineMetadata() }
            .map(SeriesMetadataEntity::slug)
            .toList()
        if (removable.isNotEmpty()) database.withTransaction { metadataDao.deleteBySlugs(removable) }
    }

    suspend fun offlineMetadataInventory(expectedSlugs: Set<String>): MetadataInventory = withContext(Dispatchers.IO) {
        val stored = metadataDao.all()
        val bySlug = stored.associateBy(SeriesMetadataEntity::slug)
        val normalizedExpected = expectedSlugs.filter(String::isNotBlank).toSet()
        val missing = normalizedExpected.filterNot(bySlug::containsKey).sorted()
        val incomplete = stored.asSequence()
            .filter { normalizedExpected.isEmpty() || it.slug in normalizedExpected }
            .filterNot { it.toModel().hasCompleteOfflineMetadata() }
            .map(SeriesMetadataEntity::slug)
            .sorted()
            .toList()
        val coverFiles = coverCacheDirectory().walkTopDown().filter { it.isFile }.toList()
        MetadataInventory(
            expectedCount = normalizedExpected.size,
            storedCount = stored.size,
            missingSlugs = missing,
            incompleteSlugs = incomplete,
            coverCacheFiles = coverFiles.size,
            coverCacheBytes = coverFiles.sumOf { it.length() }
        )
    }

    suspend fun deleteOfflineMetadata() {
        metadataDao.clear()
        clearHomeFeedCache()
        withContext(Dispatchers.IO) {
            coverCacheDirectory().deleteRecursively()
            coverCacheDirectory().mkdirs()
        }
        editSafely("Metadatenstatus zurücksetzen") { it[INITIAL_PRELOAD_COMPLETED] = false }
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

    suspend fun saveHomeFeed(feed: HomeFeed) = withContext(Dispatchers.IO) {
        if (feed.isEmpty) return@withContext
        homeFeedCacheFile().writeText(homeFeedToJson(feed).toString())
    }

    suspend fun loadHomeFeed(): HomeFeed? = withContext(Dispatchers.IO) {
        readHomeFeedCache()
    }

    suspend fun clearHomeFeedCache() = withContext(Dispatchers.IO) {
        runCatching { homeFeedCacheFile().delete() }
    }

    suspend fun exportOfflineMetadata(): String = withContext(Dispatchers.IO) {
        val catalogArray = JSONArray()
        metadataDao.all().forEach { entity ->
            catalogArray.put(seriesToJson(entity.toModel()).put("updatedAt", entity.updatedAt))
        }
        val coverFiles = coverCacheDirectory().walkTopDown().filter { it.isFile }.toList()
        JSONObject()
            .put("format", "aniworld-offline-metadata")
            .put("version", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put("catalog", catalogArray)
            .put("coverCache", JSONObject()
                .put("files", coverFiles.size)
                .put("bytes", coverFiles.sumOf { it.length() })
                .put("note", "Cache-Dateien werden lokal verwaltet und nicht in JSON eingebettet."))
            .apply { readHomeFeedCache()?.let { put("homeFeed", homeFeedToJson(it)) } }
            .toString(2)
    }

    suspend fun importOfflineMetadata(raw: String): MetadataImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.optString("format") == "aniworld-offline-metadata") { "Unbekanntes Metadatenformat" }
        val catalog = root.optJSONArray("catalog") ?: JSONArray()
        val entities = buildList {
            for (index in 0 until catalog.length()) {
                val item = catalog.optJSONObject(index) ?: continue
                val series = seriesFromJson(item) ?: continue
                add(SeriesMetadataEntity.from(series, item.optLong("updatedAt", System.currentTimeMillis())))
            }
        }.distinctBy { it.slug }
        require(entities.isNotEmpty()) { "Die Datei enthält keine gültigen Katalog-Metadaten" }
        database.withTransaction { metadataDao.upsertAll(entities) }
        root.optJSONObject("homeFeed")?.let { homeJson ->
            homeFeedFromJson(homeJson)?.takeUnless { it.isEmpty }?.let { feed ->
                homeFeedCacheFile().writeText(homeFeedToJson(feed).toString())
            }
        }
        setInitialPreloadCompleted()
        MetadataImportResult(entities.size, root.optJSONObject("homeFeed") != null)
    }

    suspend fun exportLibraryBackup(): String = withContext(Dispatchers.IO) {
        val favoriteArray = JSONArray().apply {
            dao.favoritesNow().forEach { favorite ->
                put(JSONObject()
                    .put("slug", favorite.slug)
                    .put("title", favorite.title)
                    .put("url", favorite.url)
                    .put("coverUrl", favorite.coverUrl)
                    .put("description", favorite.description)
                    .put("genres", JSONArray(favorite.genres.split("\u001F").filter(String::isNotBlank)))
                    .put("updatedAt", favorite.updatedAt))
            }
        }
        val progressArray = JSONArray().apply {
            dao.progressNow().forEach { progress ->
                put(JSONObject()
                    .put("seriesSlug", progress.seriesSlug)
                    .put("seriesTitle", progress.seriesTitle)
                    .put("seriesUrl", progress.seriesUrl)
                    .put("coverUrl", progress.coverUrl)
                    .put("season", progress.season)
                    .put("episode", progress.episode)
                    .put("episodeTitle", progress.episodeTitle)
                    .put("episodeUrl", progress.episodeUrl)
                    .put("updatedAt", progress.updatedAt))
            }
        }
        val episodeArray = JSONArray().apply {
            dao.episodeStatesNow().forEach { episode ->
                put(JSONObject()
                    .put("seriesSlug", episode.seriesSlug)
                    .put("seriesTitle", episode.seriesTitle)
                    .put("seriesUrl", episode.seriesUrl)
                    .put("coverUrl", episode.coverUrl)
                    .put("season", episode.season)
                    .put("episode", episode.episode)
                    .put("episodeTitle", episode.episodeTitle)
                    .put("episodeUrl", episode.episodeUrl)
                    .put("positionMs", episode.positionMs)
                    .put("durationMs", episode.durationMs)
                    .put("completed", episode.completed)
                    .put("updatedAt", episode.updatedAt))
            }
        }
        JSONObject()
            .put("format", LIBRARY_BACKUP_FORMAT)
            .put("version", LIBRARY_BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("favorites", favoriteArray)
            .put("progress", progressArray)
            .put("episodes", episodeArray)
            .toString(2)
    }

    suspend fun exportSettingsBackup(): String = withContext(Dispatchers.IO) {
        val preferences = context.aniWorldDataStore.data.first()
        val settings = JSONObject()
        preferences.asMap().forEach { (key, value) ->
            if (key.name in SETTINGS_BACKUP_KEY_TYPES && value.isJsonBackupValue()) {
                settings.put(key.name, value)
            }
        }
        JSONObject()
            .put("format", SETTINGS_BACKUP_FORMAT)
            .put("version", SETTINGS_BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("settings", settings)
            .toString(2)
    }

    suspend fun importSettingsBackup(raw: String, replaceExisting: Boolean): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.optString("format") == SETTINGS_BACKUP_FORMAT) { "Unbekanntes Einstellungsformat" }
        require(root.optInt("version", -1) == SETTINGS_BACKUP_VERSION) { "Nicht unterstützte Sicherungsversion" }
        val settings = root.optJSONObject("settings")
            ?: error("Die Sicherung enthält keine Einstellungen")
        require(settings.length() > 0) { "Die Sicherung enthält keine Einstellungen" }

        val imported = linkedMapOf<String, Any>()
        val keys = settings.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val expectedType = SETTINGS_BACKUP_KEY_TYPES[name]
                ?: error("Unbekannte Einstellung in der Sicherung: $name")
            val value = settings.get(name)
            val valid = when (expectedType) {
                "boolean" -> value is Boolean
                "int" -> value is Int
                "float" -> value is Number && value.toFloat().isFinite()
                "string" -> value is String
                else -> false
            }
            require(valid) { "Ungültiger Wert für Einstellung: $name" }
            imported[name] = value
        }

        context.aniWorldDataStore.edit { preferences ->
            if (replaceExisting) {
                SETTINGS_BACKUP_KEY_TYPES.forEach { (name, type) ->
                    removeSettingsBackupValue(preferences, name, type)
                }
            }
            imported.forEach { (name, value) -> writeSettingsBackupValue(preferences, name, value) }
        }
        imported.size
    }

    private fun removeSettingsBackupValue(preferences: MutablePreferences, name: String, type: String) {
        when (type) {
            "boolean" -> preferences.remove(booleanPreferencesKey(name))
            "int" -> preferences.remove(intPreferencesKey(name))
            "float" -> preferences.remove(floatPreferencesKey(name))
            "string" -> preferences.remove(stringPreferencesKey(name))
        }
    }

    private fun writeSettingsBackupValue(preferences: MutablePreferences, name: String, value: Any) {
        when (SETTINGS_BACKUP_KEY_TYPES[name]) {
            "boolean" -> preferences[booleanPreferencesKey(name)] = value as? Boolean
                ?: error("Ungültiger boolescher Wert für Einstellung: $name")
            "int" -> preferences[intPreferencesKey(name)] = value as? Int
                ?: error("Ungültiger Zahlenwert für Einstellung: $name")
            "float" -> preferences[floatPreferencesKey(name)] = (value as? Number)?.toFloat()
                ?: error("Ungültiger Zahlenwert für Einstellung: $name")
            "string" -> preferences[stringPreferencesKey(name)] = value as? String
                ?: error("Ungültiger Textwert für Einstellung: $name")
            else -> error("Unbekannte Einstellung in der Sicherung: $name")
        }
    }

    private fun Any.isJsonBackupValue(): Boolean =
        this is Boolean || this is Int || this is Float || this is String

    suspend fun importLibraryBackup(raw: String): LibraryBackupImportResult = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.optString("format") == LIBRARY_BACKUP_FORMAT) { "Unbekanntes Sicherungsformat" }
        require(root.optInt("version", -1) == LIBRARY_BACKUP_VERSION) { "Nicht unterstützte Sicherungsversion" }
        require(root.has("favorites") || root.has("progress") || root.has("episodes")) {
            "Die Sicherung enthält keine Bibliotheksdaten"
        }

        var skippedEntries = 0
        val favorites = linkedMapOf<String, FavoriteEntity>()
        val favoriteArray = root.optJSONArray("favorites") ?: JSONArray()
        for (index in 0 until favoriteArray.length()) {
            val item = favoriteArray.optJSONObject(index)
            val slug = item?.optString("slug").orEmpty().trim().trim('/').lowercase()
            val title = item?.optString("title").orEmpty().trim()
            if (item == null || !isValidBackupSlug(slug) || title.isBlank()) {
                skippedEntries++
                continue
            }
            val genres = item.optJSONArray("genres")?.let { values ->
                (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotBlank) }.distinct()
            }.orEmpty()
            val candidate = FavoriteEntity(
                slug = slug,
                title = title,
                url = item.optString("url").trim().ifBlank { "https://aniworld.to/anime/stream/$slug" },
                coverUrl = item.optString("coverUrl").trim(),
                description = item.optString("description"),
                genres = genres.joinToString("\u001F"),
                updatedAt = item.optLong("updatedAt", root.optLong("exportedAt", 0L)).coerceAtLeast(0L),
                sortIndex = index
            )
            val previous = favorites[slug]
            if (previous == null || candidate.updatedAt > previous.updatedAt) {
                if (previous != null) skippedEntries++
                favorites[slug] = candidate
            } else skippedEntries++
        }

        val progress = linkedMapOf<String, ProgressEntity>()
        val progressArray = root.optJSONArray("progress") ?: JSONArray()
        for (index in 0 until progressArray.length()) {
            val item = progressArray.optJSONObject(index)
            val slug = item?.optString("seriesSlug").orEmpty().trim().trim('/').lowercase()
            val season = item?.optInt("season", -1) ?: -1
            val episode = item?.optInt("episode", -1) ?: -1
            val title = item?.optString("seriesTitle").orEmpty().trim()
            if (item == null || !isValidBackupSlug(slug) || title.isBlank() || season < 0 || episode < 1) {
                skippedEntries++
                continue
            }
            val candidate = ProgressEntity(
                seriesSlug = slug,
                seriesTitle = title,
                seriesUrl = item.optString("seriesUrl").trim().ifBlank { "https://aniworld.to/anime/stream/$slug" },
                coverUrl = item.optString("coverUrl").trim(),
                season = season,
                episode = episode,
                episodeTitle = item.optString("episodeTitle"),
                episodeUrl = item.optString("episodeUrl"),
                updatedAt = item.optLong("updatedAt", root.optLong("exportedAt", 0L)).coerceAtLeast(0L)
            )
            val previous = progress[slug]
            if (previous == null || candidate.updatedAt > previous.updatedAt) {
                if (previous != null) skippedEntries++
                progress[slug] = candidate
            } else skippedEntries++
        }

        val episodes = linkedMapOf<String, EpisodeStateEntity>()
        val episodeArray = root.optJSONArray("episodes") ?: JSONArray()
        for (index in 0 until episodeArray.length()) {
            val item = episodeArray.optJSONObject(index)
            val slug = item?.optString("seriesSlug").orEmpty().trim().trim('/').lowercase()
            val season = item?.optInt("season", -1) ?: -1
            val episode = item?.optInt("episode", -1) ?: -1
            val title = item?.optString("seriesTitle").orEmpty().trim()
            if (item == null || !isValidBackupSlug(slug) || title.isBlank() || season < 0 || episode < 1) {
                skippedEntries++
                continue
            }
            val duration = item.optLong("durationMs", 0L).coerceAtLeast(0L)
            val completed = item.optBoolean("completed")
            val position = item.optLong("positionMs", 0L).coerceAtLeast(0L)
                .let { if (completed) maxOf(it, duration) else it }
            val candidate = EpisodeStateEntity(
                key = episodeKey(slug, season, episode),
                seriesTitle = title,
                seriesSlug = slug,
                seriesUrl = item.optString("seriesUrl").trim().ifBlank { "https://aniworld.to/anime/stream/$slug" },
                coverUrl = item.optString("coverUrl").trim(),
                season = season,
                episode = episode,
                episodeTitle = item.optString("episodeTitle"),
                episodeUrl = item.optString("episodeUrl"),
                positionMs = position,
                durationMs = duration,
                completed = completed,
                updatedAt = item.optLong("updatedAt", root.optLong("exportedAt", 0L)).coerceAtLeast(0L)
            )
            val previous = episodes[candidate.key]
            if (previous == null || candidate.updatedAt > previous.updatedAt) {
                if (previous != null) skippedEntries++
                episodes[candidate.key] = candidate
            } else skippedEntries++
        }

        var importedFavorites = 0
        var importedProgress = 0
        var importedEpisodes = 0
        database.withTransaction {
            val localFavorites = dao.favoritesNow().associateBy(FavoriteEntity::slug)
            var nextFavoriteOrder = (localFavorites.values.maxOfOrNull(FavoriteEntity::sortIndex) ?: -1) + 1
            val favoritesToWrite = favorites.values.mapNotNull { imported ->
                val local = localFavorites[imported.slug]
                when {
                    local == null -> {
                        importedFavorites++
                        imported.copy(sortIndex = nextFavoriteOrder++)
                    }
                    imported.updatedAt > local.updatedAt -> {
                        importedFavorites++
                        imported.copy(sortIndex = local.sortIndex)
                    }
                    else -> null
                }
            }
            val localProgress = dao.progressNow().associateBy(ProgressEntity::seriesSlug)
            val progressToWrite = progress.values.filter { imported ->
                imported.updatedAt > (localProgress[imported.seriesSlug]?.updatedAt ?: Long.MIN_VALUE)
            }
            importedProgress = progressToWrite.size

            val localEpisodes = dao.episodeStatesNow().associateBy(EpisodeStateEntity::key)
            val episodesToWrite = episodes.values.filter { imported ->
                imported.updatedAt > (localEpisodes[imported.key]?.updatedAt ?: Long.MIN_VALUE)
            }
            importedEpisodes = episodesToWrite.size
            skippedEntries += favorites.size - favoritesToWrite.size
            skippedEntries += progress.size - progressToWrite.size
            skippedEntries += episodes.size - episodesToWrite.size

            if (favoritesToWrite.isNotEmpty()) dao.upsertFavorites(favoritesToWrite)
            if (progressToWrite.isNotEmpty()) dao.upsertProgress(progressToWrite)
            if (episodesToWrite.isNotEmpty()) dao.upsertEpisodeStates(episodesToWrite)
        }
        LibraryBackupImportResult(importedFavorites, importedProgress, importedEpisodes, skippedEntries)
    }

    private fun isValidBackupSlug(slug: String): Boolean =
        slug.matches(Regex("[a-z0-9][a-z0-9._-]*"))

    private fun homeFeedCacheFile() = context.filesDir.resolve(HOME_FEED_CACHE_FILE)
    private fun coverCacheDirectory() = context.filesDir.resolve("anime_cover_cache")

    private fun readHomeFeedCache(): HomeFeed? = runCatching {
        val file = homeFeedCacheFile()
        if (!file.isFile) null else homeFeedFromJson(JSONObject(file.readText()))
    }.onFailure { AppLogger.warn("Speicher", "Offline-Startseite konnte nicht gelesen werden", it.message.orEmpty()) }
        .getOrNull()

    private fun seriesToJson(series: Series): JSONObject = JSONObject()
        .put("title", series.title)
        .put("slug", series.slug)
        .put("url", series.url)
        .put("description", series.description)
        .put("coverUrl", series.coverUrl)
        .put("genres", JSONArray(series.genres))
        .put("year", series.year)
        .put("ageRating", series.ageRating)

    private fun seriesFromJson(item: JSONObject): Series? {
        val slug = item.optString("slug").trim()
        val title = item.optString("title").trim()
        if (slug.isBlank() || title.isBlank()) return null
        val genres = item.optJSONArray("genres")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val genre = array.optString(i)
                    if (genre.isNotBlank()) add(genre)
                }
            }
        }.orEmpty()
        return Series(
            title = title,
            slug = slug,
            url = item.optString("url").ifBlank { "https://aniworld.to/anime/stream/$slug" },
            description = item.optString("description"),
            coverUrl = item.optString("coverUrl"),
            genres = genres,
            year = item.optString("year"),
            ageRating = item.optString("ageRating")
        )
    }

    private fun episodeToJson(episode: Episode): JSONObject = JSONObject()
        .put("season", episode.season)
        .put("number", episode.number)
        .put("title", episode.title)
        .put("secondaryTitle", episode.secondaryTitle)
        .put("description", episode.description)
        .put("releasedAt", episode.releasedAt)
        .put("url", episode.url)
        .put("seriesSlug", episode.seriesSlug)
        .put("seriesTitle", episode.seriesTitle)

    private fun episodeFromJson(item: JSONObject): Episode? {
        val slug = item.optString("seriesSlug")
        val url = item.optString("url")
        if (slug.isBlank() || url.isBlank()) return null
        return Episode(
            season = item.optInt("season"),
            number = item.optInt("number"),
            title = item.optString("title"),
            secondaryTitle = item.optString("secondaryTitle"),
            description = item.optString("description"),
            releasedAt = item.optString("releasedAt"),
            url = url,
            seriesSlug = slug,
            seriesTitle = item.optString("seriesTitle")
        )
    }

    private fun homeFeedToJson(feed: HomeFeed): JSONObject = JSONObject()
        .put("loadedAt", feed.loadedAt)
        .put("news", JSONArray().apply {
            feed.news.forEach { item -> put(JSONObject().put("title", item.title).put("url", item.url).put("imageUrl", item.imageUrl).put("subtitle", item.subtitle)) }
        })
        .put("featured", feed.featured?.let(::seriesToJson))
        .put("popularAtAniWorld", JSONArray(feed.popularAtAniWorld.map(::seriesToJson)))
        .put("latestEpisodes", JSONArray().apply {
            feed.latestEpisodes.forEach { item ->
                put(JSONObject()
                    .put("series", seriesToJson(item.series))
                    .put("episode", episodeToJson(item.episode))
                    .put("releasedAt", item.releasedAt)
                    .put("languages", JSONArray(item.languages.map(Language::token)))
                    .put("isNew", item.isNew))
            }
        })
        .put("newAnimes", JSONArray(feed.newAnimes.map(::seriesToJson)))
        .put("currentlyPopular", JSONArray(feed.currentlyPopular.map(::seriesToJson)))
        .put("communityWatching", JSONArray(feed.communityWatching.map(::seriesToJson)))
        .put("mostWatched", JSONArray(feed.mostWatched.map(::seriesToJson)))

    private fun homeFeedFromJson(root: JSONObject): HomeFeed? = runCatching {
        fun seriesList(name: String): List<Series> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val series = seriesFromJson(item) ?: continue
                    add(series)
                }
            }
        }
        val news = root.optJSONArray("news")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val url = item.optString("url")
                    if (title.isNotBlank() && url.isNotBlank()) add(HomeNews(title, url, item.optString("imageUrl"), item.optString("subtitle")))
                }
            }
        }.orEmpty()
        val latest = root.optJSONArray("latestEpisodes")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val series = item.optJSONObject("series")?.let(::seriesFromJson) ?: continue
                    val episode = item.optJSONObject("episode")?.let(::episodeFromJson) ?: continue
                    val languages = item.optJSONArray("languages")?.let { languageArray ->
                        buildList { for (j in 0 until languageArray.length()) add(Language.fromToken(languageArray.optString(j))) }.filter { it != Language.UNKNOWN }
                    }.orEmpty()
                    add(HomeEpisode(series, episode, item.optString("releasedAt"), languages, item.optBoolean("isNew")))
                }
            }
        }.orEmpty()
        HomeFeed(
            news = news,
            featured = root.optJSONObject("featured")?.let(::seriesFromJson),
            popularAtAniWorld = seriesList("popularAtAniWorld"),
            latestEpisodes = latest,
            newAnimes = seriesList("newAnimes"),
            currentlyPopular = seriesList("currentlyPopular"),
            communityWatching = seriesList("communityWatching"),
            mostWatched = seriesList("mostWatched"),
            loadedAt = root.optLong("loadedAt")
        )
    }.getOrNull()

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

    suspend fun clearFavorites() = database.withTransaction { dao.deleteAllFavorites() }

    suspend fun clearPlaybackProgress() = database.withTransaction {
        dao.deleteAllProgress()
        dao.deleteAllEpisodeStates()
        dao.deleteAllWatchedOrder()
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

    private fun parseStoredList(raw: String?): List<String> = raw.orEmpty().split(',')
        .map(String::trim)
        .filter(String::isNotBlank)

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

    private fun parseAniSkipMappings(raw: String?): Map<String, AniSkipAnimeMapping> = if (raw.isNullOrBlank()) emptyMap() else runCatching {
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { key ->
                val value = root.optJSONObject(key) ?: return@forEach
                val malId = value.optInt("malId", 0)
                val title = value.optString("title").trim()
                if (malId > 0 && title.isNotBlank()) put(key, AniSkipAnimeMapping(malId, title, value.optString("url").orEmpty()))
            }
        }
    }.getOrDefault(emptyMap())

    private fun parseLegacyStringList(raw: String?): List<String> = if (raw.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(raw)
        buildList { for (i in 0 until array.length()) add(array.optString(i)) }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    private fun <T> parseArray(raw: String?, mapper: (JSONObject) -> T): List<T> = if (raw.isNullOrBlank()) emptyList() else runCatching {
        val array = JSONArray(raw)
        buildList { for (i in 0 until array.length()) add(mapper(array.getJSONObject(i))) }
    }.getOrDefault(emptyList())

    private fun <T> parseObjectMap(raw: String?, mapper: (JSONObject) -> T): Map<String, T> = if (raw.isNullOrBlank()) emptyMap() else runCatching {
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
        private val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        private val LAST_HOME_TAB = stringPreferencesKey("last_home_tab")
        private val INITIAL_PRELOAD_COMPLETED = booleanPreferencesKey("initial_preload_completed")
        private val WEB_ADBLOCK_ENABLED = booleanPreferencesKey("web_adblock_enabled")
        private val WEB_FILTER_LISTS = stringPreferencesKey("web_filter_lists")
        private val WEB_SESSION_PANEL_EXPANDED = booleanPreferencesKey("web_session_panel_expanded")
        private val WEB_MEDIA_PANEL_EXPANDED = booleanPreferencesKey("web_media_panel_expanded")
        private val AUTO_NEXT_ENABLED = booleanPreferencesKey("auto_next_enabled")
        private val AUTO_NEXT_DELAY_SECONDS = intPreferencesKey("auto_next_delay_seconds")
        private val AUTO_PLAY_PREFERRED_HOSTER = booleanPreferencesKey("auto_play_preferred_hoster")
        private val ALLOW_EXTERNAL_PLAYER = booleanPreferencesKey("allow_external_player")
        private val AUTO_SKIP_OPENING = booleanPreferencesKey("auto_skip_opening")
        private val AUTO_SKIP_ENDING = booleanPreferencesKey("auto_skip_ending")
        private val AUTO_SKIP_RECAP = booleanPreferencesKey("auto_skip_recap")
        private val SHOW_SKIP_BUTTONS = booleanPreferencesKey("show_skip_buttons")
        private val SKIP_OFFSET_SECONDS = floatPreferencesKey("skip_offset_seconds")
        private val PLAY_AFTER_SKIP = booleanPreferencesKey("play_after_skip")
        private val REMEMBER_PLAYBACK_POSITION = booleanPreferencesKey("remember_playback_position")
        private val PLAYBACK_POSITION_EXPIRY_DAYS = intPreferencesKey("playback_position_expiry_days")
        private val REMEMBER_VOLUME = booleanPreferencesKey("remember_volume")
        private val SAVED_VOLUME_PERCENT = intPreferencesKey("saved_volume_percent")
        private val ANISKIP_MAPPINGS = stringPreferencesKey("aniskip_mappings")
        private val ANISKIP_MAPPING_MODE = stringPreferencesKey("aniskip_mapping_mode")
        private val STARTUP_TAB = stringPreferencesKey("startup_tab")
        private val HOME_OFFLINE_MODE = booleanPreferencesKey("home_offline_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val CUSTOM_ACCENT_COLOR = intPreferencesKey("custom_accent_color")
        private val SETTINGS_BUTTON_X = floatPreferencesKey("settings_button_x")
        private val SETTINGS_BUTTON_Y = floatPreferencesKey("settings_button_y")
        private val CATALOG_VIEW_MODE = stringPreferencesKey("catalog_view_mode")
        private val FAVORITES_VIEW_MODE = stringPreferencesKey("favorites_view_mode")
        private val HISTORY_VIEW_MODE = stringPreferencesKey("history_view_mode")
        private val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
        private val CAST_ENABLED = booleanPreferencesKey("cast_enabled")
        private val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        private val HIGHLIGHT_STARTED_SEASONS = booleanPreferencesKey("highlight_started_seasons")
        private val STARTED_SEASON_HIGHLIGHT_ALPHA = floatPreferencesKey("started_season_highlight_alpha")
        private val HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        private val HIDDEN_HOME_SECTIONS = stringPreferencesKey("hidden_home_sections")
        private val ROOM_MIGRATION_DONE = booleanPreferencesKey("room_migration_done")

        private val LEGACY_WATCHLIST_JSON = stringPreferencesKey("watchlist_json")
        private val LEGACY_PROGRESS_JSON = stringPreferencesKey("progress_json")
        private val LEGACY_FAVORITES_JSON = stringPreferencesKey("favorites_json")
        private val LEGACY_RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
        private val LEGACY_EPISODE_STATES_JSON = stringPreferencesKey("episode_states_json")
        private val LEGACY_SEASON_TOTALS_JSON = stringPreferencesKey("season_totals_json")
        private val LEGACY_FAVORITE_ORDER = stringPreferencesKey("favorite_order")
        private val LEGACY_WATCHED_ORDER = stringPreferencesKey("watched_order")
        private const val LIBRARY_BACKUP_FORMAT = "aniworld-library-backup"
        private const val LIBRARY_BACKUP_VERSION = 1
        private const val SETTINGS_BACKUP_FORMAT = "aniworld-settings-backup"
        private const val SETTINGS_BACKUP_VERSION = 1
        private const val HOME_FEED_CACHE_FILE = "home_feed_offline.json"
        private val SETTINGS_BACKUP_KEY_TYPES = mapOf(
            "language_priority" to "string",
            "hoster_priority" to "string",
            "verify_streams" to "boolean",
            "favorite_sort" to "string",
            "watched_sort" to "string",
            "dynamic_colors" to "boolean",
            "web_adblock_enabled" to "boolean",
            "web_filter_lists" to "string",
            "web_session_panel_expanded" to "boolean",
            "web_media_panel_expanded" to "boolean",
            "auto_next_enabled" to "boolean",
            "auto_next_delay_seconds" to "int",
            "auto_play_preferred_hoster" to "boolean",
            "allow_external_player" to "boolean",
            "auto_skip_opening" to "boolean",
            "auto_skip_ending" to "boolean",
            "auto_skip_recap" to "boolean",
            "show_skip_buttons" to "boolean",
            "skip_offset_seconds" to "float",
            "play_after_skip" to "boolean",
            "remember_playback_position" to "boolean",
            "playback_position_expiry_days" to "int",
            "remember_volume" to "boolean",
            "saved_volume_percent" to "int",
            "aniskip_mappings" to "string",
            "aniskip_mapping_mode" to "string",
            "last_home_tab" to "string",
            "startup_tab" to "string",
            "home_offline_mode" to "boolean",
            "accent_color" to "string",
            "custom_accent_color" to "int",
            "settings_button_x" to "float",
            "settings_button_y" to "float",
            "catalog_view_mode" to "string",
            "favorites_view_mode" to "string",
            "history_view_mode" to "string",
            "diagnostics_enabled" to "boolean",
            "cast_enabled" to "boolean",
            "pip_enabled" to "boolean",
            "highlight_started_seasons" to "boolean",
            "started_season_highlight_alpha" to "float",
            "home_section_order" to "string",
            "hidden_home_sections" to "string"
        )
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
