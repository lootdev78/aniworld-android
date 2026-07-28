package de.dxmoc.aniworld

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class UiState(
    val query: String = "",
    val loading: Boolean = false,
    val resolving: Boolean = false,
    val homeLoading: Boolean = false,
    val homeFeed: HomeFeed = HomeFeed(),
    val homeError: String? = null,
    val catalogLoading: Boolean = false,
    val catalog: CatalogData = CatalogData(),
    val catalogQuery: String = "",
    val catalogLetter: String? = null,
    val catalogGenre: String? = null,
    val catalogPage: Int = 0,
    val catalogPageSize: Int = 15,
    val results: List<Series> = emptyList(),
    val selected: Series? = null,
    val seasons: List<Int> = emptyList(),
    val season: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val hosters: List<Hoster> = emptyList(),
    val pendingEpisode: Episode? = null,
    val pendingHosters: List<Hoster> = emptyList(),
    val infoSeries: Series? = null,
    val infoEpisode: Episode? = null,
    val infoLoading: Boolean = false,
    val infoError: String? = null,
    val playback: ResolvedPlayback? = null,
    val preferences: AppPreferences = AppPreferences(),
    val preferencesReady: Boolean = false,
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val challenge: ChallengeRequest? = null,
    val challengeChecking: Boolean = false,
    val challengeStatus: String? = null,
    val status: String? = null,
    val error: String? = null,
    val resolveLog: List<String> = emptyList(),
    val detailScrollIndex: Int = 0,
    val detailScrollOffset: Int = 0,
    val episodeScrollIndex: Int = 0,
    val episodeScrollOffset: Int = 0
) {
    val filteredCatalog: List<Series>
        get() = catalog.items.asSequence()
            .filter { item -> catalogLetter == null || item.title.firstOrNull()?.uppercase() == catalogLetter || (catalogLetter == "#" && item.title.firstOrNull()?.isLetter() == false) }
            .filter { item -> catalogGenre == null || item.genres.any { it.equals(catalogGenre, true) } }
            .filter { item -> catalogQuery.isBlank() || item.title.contains(catalogQuery, true) || item.genres.any { it.contains(catalogQuery, true) } || item.description.contains(catalogQuery, true) }
            .toList()

    val catalogPageCount: Int
        get() = ((filteredCatalog.size + catalogPageSize - 1) / catalogPageSize).coerceAtLeast(1)

    val pagedCatalog: List<Series>
        get() {
            val safePage = catalogPage.coerceIn(0, catalogPageCount - 1)
            return filteredCatalog.drop(safePage * catalogPageSize).take(catalogPageSize)
        }
}

private data class ManualMediaContext(
    val series: Series,
    val episode: Episode,
    val hoster: Hoster
)

class AppViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as AniWorldApplication
    private val repo = app.repository
    private val store = app.store
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var pendingChallengeRetry: (() -> Unit)? = null
    private var lastProgressWriteAt = 0L
    private val metadataInFlight = mutableSetOf<String>()
    private val metadataSemaphore = Semaphore(4)
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var startupTriggered = false
    private var catalogSearchRememberJob: Job? = null
    private var manualMediaContext: ManualMediaContext? = null

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
    private fun languageName(language: Language): String = text(language.labelRes)
    private fun episodeName(episode: Episode): String = episode.localizedLabel(getApplication())

    init {
        viewModelScope.launch {
            store.preferences.collect { prefs ->
                _state.update { it.copy(preferences = prefs, preferencesReady = true) }
                if (!startupTriggered) {
                    startupTriggered = true
                    loadHome()
                    loadCatalog()
                    restoreSavedSelection()
                }
            }
        }
        viewModelScope.launch {
            AppLogger.entries.collect { entries -> _state.update { it.copy(diagnostics = entries) } }
        }
        AppLogger.info("App", "Anwendung gestartet")
    }

    fun loadHome(force: Boolean = false) {
        if (_state.value.homeLoading && !force) return
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true, homeError = null) }
            try {
                val feed = repo.homeFeed(forceRefresh = force)
                app.prefetchCovers(
                    buildList {
                        feed.featured?.let { add(it) }
                        addAll(feed.popularAtAniWorld)
                        addAll(feed.latestEpisodes.map(HomeEpisode::series))
                        addAll(feed.newAnimes)
                        addAll(feed.currentlyPopular)
                        addAll(feed.communityWatching)
                        addAll(feed.mostWatched)
                    }
                )
                _state.update { it.copy(homeFeed = feed, homeError = if (feed.isEmpty) text(R.string.status_home_no_sections) else null) }
                AppLogger.info("Startseite", "${feed.latestEpisodes.size} neue Episoden und ${feed.popularAtAniWorld.size} beliebte Titel geladen")
            } catch (error: Exception) {
                if (error is ChallengeRequiredException) handleFailure(error) { loadHome(true) }
                else {
                    AppLogger.error("Startseite", "Startseite konnte nicht geladen werden", error)
                    _state.update { it.copy(homeError = friendlyMessage(error)) }
                }
            } finally { _state.update { it.copy(homeLoading = false) } }
        }
    }

    fun loadCatalog(force: Boolean = false) {
        if (_state.value.catalogLoading || (_state.value.catalog.items.isNotEmpty() && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(catalogLoading = true, error = null, status = text(R.string.status_catalog_loading)) }
            try {
                val catalog = repo.catalog(forceRefresh = force)
                _state.update {
                    it.copy(
                        catalog = catalog,
                        catalogPage = 0,
                        status = text(R.string.status_catalog_loaded, catalog.items.size, catalog.genres.size)
                    )
                }
                AppLogger.info("Katalog", "${catalog.items.size} Anime geladen", "Genres: ${catalog.genres.joinToString()}")
            } catch (error: Exception) {
                handleFailure(error) { loadCatalog(true) }
            } finally { _state.update { it.copy(catalogLoading = false) } }
        }
    }

    fun setCatalogQuery(value: String) {
        _state.update { it.copy(catalogQuery = value, catalogPage = 0) }
        catalogSearchRememberJob?.cancel()
        if (value.trim().length >= 2) {
            catalogSearchRememberJob = viewModelScope.launch {
                kotlinx.coroutines.delay(800L)
                if (_state.value.catalogQuery.trim() == value.trim()) store.rememberSearch(value)
            }
        }
    }
    fun setCatalogLetter(value: String?) = _state.update { it.copy(catalogLetter = value, catalogPage = 0) }
    fun setCatalogGenre(value: String?) = _state.update { it.copy(catalogGenre = value, catalogPage = 0) }
    fun setCatalogPage(value: Int) = _state.update { state ->
        state.copy(catalogPage = value.coerceIn(0, state.catalogPageCount - 1))
    }
    fun catalogNextPage() = _state.update { state -> state.copy(catalogPage = (state.catalogPage + 1).coerceAtMost(state.catalogPageCount - 1)) }
    fun catalogPreviousPage() = _state.update { state -> state.copy(catalogPage = (state.catalogPage - 1).coerceAtLeast(0)) }

    fun refreshCatalogMetadata() {
        CatalogMetadataWorker.enqueue(app, force = true)
        _state.update { it.copy(status = text(R.string.metadata_update_started)) }
    }

    fun enrichCatalogItem(series: Series) {
        if (isUsableCoverUrl(series.coverUrl) && series.description.isNotBlank() && series.genres.isNotEmpty()) return
        if (!metadataInFlight.add(series.slug)) return
        viewModelScope.launch {
            try {
                metadataSemaphore.withPermit {
                    val catalogItem = _state.value.catalog.items.any { it.slug == series.slug }
                    val detailed = repo.enrichSeries(series, cacheCatalogMetadata = catalogItem)
                    mergeSeriesIntoState(detailed)
                    app.prefetchCover(detailed.coverUrl, detailed.slug)
                }
            } catch (error: Exception) {
                if (error is ChallengeRequiredException) {
                    AppLogger.warn("Metadaten", "Metadaten benötigen eine Web-Verifizierung", series.title)
                } else {
                    AppLogger.warn("Metadaten", "${series.title} konnte nicht angereichert werden", error.message.orEmpty())
                }
            } finally {
                metadataInFlight.remove(series.slug)
            }
        }
    }

    private fun mergeSeriesIntoState(detailed: Series) {
        _state.update { state ->
            val update: (Series) -> Series = { item -> if (item.slug == detailed.slug) detailed else item }
            val catalogItems = state.catalog.items.map(update)
            state.copy(
                selected = state.selected?.let(update),
                results = state.results.map(update),
                catalog = state.catalog.copy(
                    items = catalogItems,
                    genres = catalogItems.flatMap { it.genres }.filter(String::isNotBlank).distinct().sortedBy { it.lowercase() }
                ),
                homeFeed = state.homeFeed.copy(
                    featured = state.homeFeed.featured?.let(update),
                    popularAtAniWorld = state.homeFeed.popularAtAniWorld.map(update),
                    latestEpisodes = state.homeFeed.latestEpisodes.map { homeEpisode ->
                        if (homeEpisode.series.slug == detailed.slug) {
                            homeEpisode.copy(
                                series = detailed,
                                episode = homeEpisode.episode.copy(seriesTitle = detailed.title)
                            )
                        } else homeEpisode
                    },
                    newAnimes = state.homeFeed.newAnimes.map(update),
                    currentlyPopular = state.homeFeed.currentlyPopular.map(update),
                    communityWatching = state.homeFeed.communityWatching.map(update),
                    mostWatched = state.homeFeed.mostWatched.map(update)
                )
            )
        }
    }

    private fun isUsableCoverUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return listOf(
            "aniworld_logo", "aniworld-logo", "/logo.", "/logos/", "favicon", "placeholder",
            "loading", "spinner", "avatar", "profile", "tracking", "pixel.gif", "blank.gif"
        ).none(lower::contains)
    }

    fun query(value: String) = _state.update { it.copy(query = value) }

    fun search(queryOverride: String? = null, rememberQuery: Boolean = true) {
        val q = (queryOverride ?: _state.value.query).trim()
        if (q.isBlank()) return
        val generation = ++searchGeneration
        _state.update { it.copy(query = q, loading = true, error = null, status = text(R.string.status_searching)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = repo.search(q)
                if (generation != searchGeneration || _state.value.query.trim() != q) return@launch
                if (rememberQuery) store.rememberSearch(q)
                _state.update {
                    it.copy(
                        results = results,
                        selected = null,
                        seasons = emptyList(),
                        season = null,
                        episodes = emptyList(),
                        hosters = emptyList(),
                        pendingEpisode = null,
                        pendingHosters = emptyList(),
                        resolveLog = emptyList(),
                        status = if (results.isEmpty()) text(R.string.status_no_results_for, q) else text(R.string.status_results_count, results.size)
                    )
                }
                AppLogger.info("Suche", "Suche nach „$q“ abgeschlossen", "${results.size} Treffer")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == searchGeneration) handleFailure(error) { search(q, rememberQuery) }
            } finally {
                if (generation == searchGeneration) _state.update { it.copy(loading = false) }
            }
        }
    }

    fun select(series: Series) {
        saveSelection(series, null)
        viewModelScope.launch {
            _state.update { it.copy(selected = series, season = null, episodes = emptyList(), hosters = emptyList(), pendingEpisode = null, pendingHosters = emptyList(), resolveLog = emptyList(), error = null) }
            runLoad(text(R.string.status_anime_loading), { select(series) }) {
                val detailed = repo.enrichSeries(series)
                app.prefetchCover(detailed.coverUrl, detailed.slug)
                store.rememberSeries(detailed)
                val seasons = repo.seasons(detailed)
                mergeSeriesIntoState(detailed)
                _state.update { state -> state.copy(selected = detailed, seasons = seasons, status = if (seasons.isEmpty()) text(R.string.status_no_seasons) else text(R.string.status_areas_found, seasons.size)) }
            }
        }
    }

    fun openHomeEpisode(item: HomeEpisode) {
        saveSelection(item.series, item.episode.season)
        viewModelScope.launch {
            _state.update { it.copy(selected = item.series, season = item.episode.season, episodes = emptyList(), error = null) }
            runLoad(text(R.string.status_episode_opening), { openHomeEpisode(item) }) {
                val detailed = runCatching { repo.enrichSeries(item.series) }.getOrDefault(item.series)
                val seasons = repo.seasons(detailed)
                val episodes = repo.episodes(detailed, item.episode.season)
                store.rememberSeries(detailed)
                store.rememberSeasonTotal(detailed.slug, item.episode.season, episodes.size)
                _state.update { it.copy(selected = detailed, seasons = seasons, episodes = episodes) }
                episodes.firstOrNull { it.number == item.episode.number }?.let(::inspectEpisode)
            }
        }
    }

    fun selectWatch(entry: WatchEntry) = select(Series(entry.title, entry.slug, entry.url, coverUrl = entry.coverUrl))
    fun selectFavorite(entry: FavoriteEntry) = select(entry.asSeries())
    fun selectWatched(entry: WatchedSeriesEntry) = select(entry.asSeries())

    fun openProgress(progress: ProgressEntry) {
        val series = Series(progress.seriesTitle, progress.seriesSlug, progress.seriesUrl, coverUrl = progress.coverUrl)
        saveSelection(series, progress.season)
        viewModelScope.launch {
            _state.update { it.copy(selected = series, season = progress.season, episodes = emptyList(), error = null) }
            runLoad(text(R.string.status_episodes_loading), { openProgress(progress) }) {
                val detailed = runCatching { repo.enrichSeries(series) }.getOrDefault(series)
                val seasons = repo.seasons(detailed)
                val episodes = repo.episodes(detailed, progress.season)
                store.rememberSeasonTotal(detailed.slug, progress.season, episodes.size)
                _state.update { it.copy(selected = detailed, seasons = seasons, episodes = episodes) }
            }
        }
    }

    fun season(number: Int) {
        val series = _state.value.selected ?: return
        saveSelection(series, number)
        viewModelScope.launch {
            _state.update { it.copy(season = number, episodes = emptyList(), hosters = emptyList(), error = null) }
            runLoad(text(R.string.status_episodes_loading), { season(number) }) {
                val episodes = repo.episodes(series, number)
                store.rememberSeasonTotal(series.slug, number, episodes.size)
                _state.update { it.copy(episodes = episodes, status = if (number == 0) text(R.string.status_movie_count, episodes.size) else text(R.string.status_episode_count, episodes.size)) }
            }
        }
    }

    fun backToSearch() {
        clearSavedSelection()
        _state.update { it.copy(selected = null, seasons = emptyList(), season = null, episodes = emptyList(), hosters = emptyList(), pendingEpisode = null, pendingHosters = emptyList(), error = null, status = null, resolveLog = emptyList()) }
    }

    fun backToSeasons() {
        _state.value.selected?.let { saveSelection(it, null) }
        _state.update { it.copy(season = null, episodes = emptyList(), hosters = emptyList(), pendingEpisode = null, pendingHosters = emptyList(), error = null, status = null, resolveLog = emptyList()) }
    }

    fun inspectEpisode(episode: Episode) {
        viewModelScope.launch {
            _state.update { it.copy(pendingEpisode = episode, pendingHosters = emptyList(), loading = true, status = text(R.string.status_hosters_loading), error = null) }
            try {
                val page = repo.episodePage(episode)
                val updatedEpisodes = _state.value.episodes.map { if (it.key == episode.key) page.episode else it }
                _state.update {
                    it.copy(
                        episodes = updatedEpisodes,
                        pendingEpisode = page.episode,
                        pendingHosters = page.hosters,
                        hosters = page.hosters,
                        status = if (page.hosters.isEmpty()) text(R.string.status_no_hosters) else text(R.string.status_hosters_found, page.hosters.size)
                    )
                }
            } catch (error: Exception) { handleFailure(error) { inspectEpisode(episode) } }
            finally { _state.update { it.copy(loading = false) } }
        }
    }

    fun dismissEpisodeOptions() = _state.update { it.copy(pendingEpisode = null, pendingHosters = emptyList()) }

    fun openAnimeInfo(series: Series) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    infoSeries = series,
                    infoEpisode = null,
                    infoLoading = true,
                    infoError = null
                )
            }
            try {
                val detailed = repo.enrichSeries(series, forceRefresh = true)
                mergeSeriesIntoState(detailed)
                _state.update { it.copy(infoSeries = detailed) }
            } catch (error: Exception) {
                _state.update { it.copy(infoError = error.message ?: text(R.string.error_unknown)) }
            } finally {
                _state.update { it.copy(infoLoading = false) }
            }
        }
    }

    fun openEpisodeInfo(episode: Episode) {
        val series = _state.value.selected
            ?.takeIf { it.slug == episode.seriesSlug }
            ?: Series(
                title = episode.seriesTitle,
                slug = episode.seriesSlug,
                url = "https://aniworld.to/anime/stream/${episode.seriesSlug}"
            )
        viewModelScope.launch {
            _state.update {
                it.copy(
                    infoSeries = series,
                    infoEpisode = episode,
                    infoLoading = true,
                    infoError = null
                )
            }
            try {
                val detailedSeries = repo.enrichSeries(series, forceRefresh = true)
                val detailedEpisode = repo.episodeDetails(episode)
                mergeSeriesIntoState(detailedSeries)
                _state.update {
                    it.copy(
                        infoSeries = detailedSeries,
                        infoEpisode = detailedEpisode,
                        episodes = it.episodes.map { existing ->
                            if (existing.key == detailedEpisode.key) detailedEpisode else existing
                        }
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(infoError = error.message ?: text(R.string.error_unknown)) }
            } finally {
                _state.update { it.copy(infoLoading = false) }
            }
        }
    }

    fun dismissInfoDialog() = _state.update {
        it.copy(infoSeries = null, infoEpisode = null, infoLoading = false, infoError = null)
    }

    fun playEpisode(episode: Episode, languageOverride: Language? = null, hosterOverride: Hoster? = null) {
        val series = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(resolving = true, pendingEpisode = null, pendingHosters = emptyList(), status = text(R.string.status_stream_resolving), error = null, resolveLog = emptyList()) }
            try {
                val prefs = _state.value.preferences
                val languages: List<Language> = if (languageOverride != null) listOf(languageOverride) + prefs.languagePriority.filterNot { old -> old == languageOverride } else prefs.languagePriority
                val result = repo.resolveEpisode(episode, languages, prefs.hosterPriority, prefs.verifyStreams, languageOverride, hosterOverride)
                result.log.forEach { AppLogger.info("Resolver", it) }
                _state.update { it.copy(hosters = result.availableHosters, status = result.log.lastOrNull(), resolveLog = result.log) }
                val stream = result.stream
                if (stream == null) {
                    if (hosterOverride != null) {
                        showHosterDetector(series, episode, hosterOverride)
                        return@launch
                    }
                    throw IllegalStateException(
                        text(
                            R.string.status_no_playable_stream,
                            result.availableHosters.joinToString { h ->
                                "${h.name} (${languageName(h.lang)})"
                            }.ifBlank { text(R.string.status_none) }
                        )
                    )
                }
                if (languageOverride != null) store.setLanguagePriority(languages)
                store.rememberProgress(series, episode)
                val previous = prefs.episodeWatchStates[episode.key]
                _state.update {
                    it.copy(
                        playback = ResolvedPlayback(
                            series = series,
                            episode = episode,
                            stream = stream,
                            startPositionMs = previous?.takeUnless { state -> state.completed }?.positionMs ?: 0L
                        ),
                        status = text(R.string.status_playing_via, stream.hoster, languageName(stream.language))
                    )
                }
            } catch (error: Exception) { handleFailure(error) { playEpisode(episode, languageOverride, hosterOverride) } }
            finally { _state.update { it.copy(resolving = false) } }
        }
    }


    fun playPreviousEpisode() = playAdjacentEpisode(-1)

    fun playNextEpisode() = playAdjacentEpisode(1)

    private fun playAdjacentEpisode(delta: Int) {
        val current = _state.value.playback?.episode ?: return
        val episodes = _state.value.episodes
        val index = episodes.indexOfFirst { it.key == current.key }
        val target = episodes.getOrNull(index + delta) ?: return
        playEpisode(target)
    }

    fun onPlaybackProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val playback = _state.value.playback ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastProgressWriteAt < 4_500L) return
        lastProgressWriteAt = now
        viewModelScope.launch {
            runCatching { store.savePlaybackProgress(playback.series, playback.episode, positionMs, durationMs, false) }
                .onFailure { AppLogger.error("Player", "Fortschritt konnte nicht gespeichert werden", it) }
        }
    }

    fun onPlaybackEnded(positionMs: Long, durationMs: Long) {
        val playback = _state.value.playback ?: return
        viewModelScope.launch {
            val safeDuration = durationMs.takeIf { it > 0L } ?: positionMs.coerceAtLeast(0L)
            store.savePlaybackProgress(
                playback.series,
                playback.episode,
                positionMs.coerceAtLeast(0L),
                safeDuration,
                true
            )
            _state.update { it.copy(status = text(R.string.status_episode_marked_watched, episodeName(playback.episode))) }
        }
    }

    fun closePlayer(positionMs: Long = 0L, durationMs: Long = 0L) {
        val playback = _state.value.playback
        if (playback != null) {
            if (positionMs > 0L || durationMs > 0L) onPlaybackProgress(positionMs, durationMs, true)
            saveSelection(playback.series, playback.episode.season)
            if (_state.value.selected?.slug != playback.series.slug || _state.value.season != playback.episode.season) {
                _state.update { it.copy(selected = playback.series, season = playback.episode.season) }
                season(playback.episode.season)
            }
        }
        _state.update { it.copy(playback = null) }
    }

    fun reportPlayerError(message: String) {
        AppLogger.error("Player", message)
        _state.update { it.copy(error = message) }
    }

    fun toggleEpisodeWatched(episode: Episode) {
        val series = _state.value.selected ?: return
        val watched = _state.value.preferences.episodeWatchStates[episode.key]?.completed == true
        viewModelScope.launch { store.setEpisodeWatched(series, episode, !watched) }
    }

    fun toggleSeasonWatched() {
        val series = _state.value.selected ?: return
        val episodes = _state.value.episodes
        if (episodes.isEmpty()) return
        val allWatched = episodes.all { _state.value.preferences.episodeWatchStates[it.key]?.completed == true }
        viewModelScope.launch { store.setSeasonWatched(series, episodes, !allWatched) }
    }

    fun toggleFavorite(series: Series) = viewModelScope.launch { store.toggleFavorite(series) }.let { Unit }
    fun removeWatch(slug: String) = viewModelScope.launch { store.removeSeries(slug) }.let { Unit }
    fun removeWatched(slug: String) = viewModelScope.launch { store.removeWatchedSeries(slug) }.let { Unit }
    fun moveFavorite(slug: String, delta: Int) = viewModelScope.launch { store.moveFavorite(slug, delta) }.let { Unit }
    fun moveWatched(slug: String, delta: Int) = viewModelScope.launch { store.moveWatched(slug, delta) }.let { Unit }
    fun setFavoriteSort(sort: LibrarySort) = viewModelScope.launch { store.setFavoriteSort(sort) }.let { Unit }
    fun setWatchedSort(sort: LibrarySort) = viewModelScope.launch { store.setWatchedSort(sort) }.let { Unit }
    fun setCatalogViewMode(mode: LibraryViewMode) = viewModelScope.launch { store.setCatalogViewMode(mode) }.let { Unit }
    fun setFavoritesViewMode(mode: LibraryViewMode) = viewModelScope.launch { store.setFavoritesViewMode(mode) }.let { Unit }
    fun clearRecentSearches() = viewModelScope.launch { store.clearRecentSearches() }.let { Unit }
    fun markPermissionIntroSeen() = viewModelScope.launch { store.setPermissionIntroSeen() }.let { Unit }
    fun clearDiagnostics() = AppLogger.clear()
    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissStatus() = _state.update { it.copy(status = null) }

    fun openDeepLink(value: String?) {
        val uri = runCatching { Uri.parse(value.orEmpty()) }.getOrNull() ?: return
        if (uri.scheme != "aniworldapp" || uri.host != "anime") return
        val slug = uri.lastPathSegment.orEmpty().trim()
        if (slug.isBlank()) return
        select(Series(slug.replace('-', ' ').replaceFirstChar { it.uppercase() }, slug, "https://aniworld.to/anime/stream/$slug"))
    }

    fun openManualPage(url: String, title: String = "") {
        pendingChallengeRetry = null
        manualMediaContext = null
        _state.update {
            it.copy(
                pendingEpisode = null,
                pendingHosters = emptyList(),
                challenge = ChallengeRequest(
                    url = url,
                    reason = text(R.string.challenge_instruction),
                    title = title.ifBlank { text(R.string.challenge_protected_page) },
                    retryAfterSuccess = false,
                    mediaDetectionEnabled = false
                ),
                challengeStatus = repo.challengeCookieSummary(url),
                error = null
            )
        }
    }

    fun openHosterPage(episode: Episode, hoster: Hoster) {
        val series = _state.value.selected ?: return
        showHosterDetector(series, episode, hoster)
    }

    private fun showHosterDetector(series: Series, episode: Episode, hoster: Hoster) {
        pendingChallengeRetry = null
        manualMediaContext = ManualMediaContext(series, episode, hoster)
        _state.update {
            it.copy(
                pendingEpisode = null,
                pendingHosters = emptyList(),
                challenge = ChallengeRequest(
                    url = hoster.redirectUrl,
                    reason = text(R.string.media_detector_instruction),
                    title = localizedHosterName(getApplication<Application>(), hoster.name),
                    retryAfterSuccess = false,
                    mediaDetectionEnabled = true
                ),
                challengeStatus = repo.challengeCookieSummary(hoster.redirectUrl),
                status = text(R.string.media_detector_opening),
                error = null
            )
        }
    }

    fun playDetectedMedia(candidate: DetectedMediaCandidate) {
        val context = manualMediaContext ?: return
        viewModelScope.launch {
            val previous = _state.value.preferences.episodeWatchStates[context.episode.key]
            store.rememberProgress(context.series, context.episode)
            manualMediaContext = null
            _state.update {
                it.copy(
                    challenge = null,
                    challengeChecking = false,
                    challengeStatus = null,
                    playback = ResolvedPlayback(
                        series = context.series,
                        episode = context.episode,
                        stream = StreamSource(
                            url = candidate.url,
                            headers = candidate.headers,
                            hoster = localizedHosterName(getApplication<Application>(), context.hoster.name),
                            language = context.hoster.lang,
                            mimeType = candidate.mimeType
                        ),
                        startPositionMs = previous?.takeUnless { state -> state.completed }?.positionMs ?: 0L
                    ),
                    status = text(
                        R.string.media_detector_playing,
                        candidate.formatLabel,
                        localizedHosterName(getApplication<Application>(), context.hoster.name)
                    ),
                    error = null
                )
            }
        }
    }

    fun openDefaultChallenge() = openManualPage(repo.defaultChallengeUrl(), text(R.string.aniworld_verification))

    fun closeChallenge() {
        pendingChallengeRetry = null
        manualMediaContext = null
        _state.update {
            it.copy(
                challenge = null,
                challengeChecking = false,
                challengeStatus = null
            )
        }
    }

    fun verifyChallenge(currentUrl: String) {
        val request = _state.value.challenge ?: return
        viewModelScope.launch {
            _state.update { it.copy(challengeChecking = true, challengeStatus = text(R.string.webview_session_checking), error = null) }
            val verifyUrl: String = if (currentUrl.isBlank()) request.url else currentUrl
            val result = repo.verifyChallengeSession(verifyUrl)
            if (result.success) {
                val retry = if (request.retryAfterSuccess) pendingChallengeRetry else null
                pendingChallengeRetry = null
                manualMediaContext = null
                _state.update { it.copy(challenge = null, challengeChecking = false, challengeStatus = null, status = result.message) }
                retry?.invoke()
            } else _state.update { it.copy(challengeChecking = false, challengeStatus = result.message) }
        }
    }

    fun clearChallengeSession() { repo.clearChallengeSession(); _state.update { it.copy(challengeStatus = it.challenge?.url?.let(repo::challengeCookieSummary) ?: text(R.string.webview_cookies_cleared)) } }
    fun setPrimaryLanguage(language: Language) = viewModelScope.launch { store.setLanguagePriority(listOf(language) + listOf(Language.GER_DUB, Language.GER_SUB, Language.ENG_SUB).filterNot { it == language }) }.let { Unit }
    fun setPrimaryHoster(hoster: String) = viewModelScope.launch { val picked = HosterCatalog.displayName(hoster); store.setHosterPriority(listOf(picked) + HosterCatalog.DEFAULT_PRIORITY.filterNot { HosterCatalog.normalize(it) == HosterCatalog.normalize(picked) }) }.let { Unit }
    fun setVerifyStreams(enabled: Boolean) = viewModelScope.launch { store.setVerifyStreams(enabled) }.let { Unit }
    fun setDynamicColors(enabled: Boolean) = viewModelScope.launch { store.setDynamicColors(enabled) }.let { Unit }
    fun setAutoNextEnabled(enabled: Boolean) = viewModelScope.launch { store.setAutoNextEnabled(enabled) }.let { Unit }
    fun setWebAdBlockEnabled(enabled: Boolean) = viewModelScope.launch { store.setWebAdBlockEnabled(enabled) }.let { Unit }
    fun setWebFilterLists(ids: Set<String>) = viewModelScope.launch { store.setWebFilterLists(ids) }.let { Unit }
    fun setWebSessionPanelExpanded(expanded: Boolean) = viewModelScope.launch { store.setWebSessionPanelExpanded(expanded) }.let { Unit }
    fun setWebMediaPanelExpanded(expanded: Boolean) = viewModelScope.launch { store.setWebMediaPanelExpanded(expanded) }.let { Unit }
    fun setSettingsButtonPosition(x: Float, y: Float) = viewModelScope.launch { store.setSettingsButtonPosition(x, y) }.let { Unit }
    fun resetSettingsButtonPosition() = viewModelScope.launch { store.resetSettingsButtonPosition() }.let { Unit }

    fun resetCoverDataAndCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    app.database.seriesMetadataDao().clear()
                    app.database.pageCacheDao().clear()
                    store.clearStoredCovers()
                    app.clearImageCaches()
                }
            }.onSuccess {
                _state.update { state ->
                    state.copy(
                        homeFeed = HomeFeed(),
                        catalog = CatalogData(),
                        status = text(R.string.cover_cache_reset_done)
                    )
                }
                loadHome(true)
                loadCatalog(true)
            }.onFailure { error ->
                AppLogger.error("Cover", "Coverdaten konnten nicht zurückgesetzt werden", error)
                _state.update { it.copy(error = friendlyMessage(error)) }
            }
        }
    }

    fun saveDetailScroll(index: Int, offset: Int) = _state.update {
        it.copy(detailScrollIndex = index.coerceAtLeast(0), detailScrollOffset = offset.coerceAtLeast(0))
    }

    fun saveEpisodeScroll(index: Int, offset: Int) = _state.update {
        it.copy(episodeScrollIndex = index.coerceAtLeast(0), episodeScrollOffset = offset.coerceAtLeast(0))
    }

    fun resumeSelected() {
        val series = _state.value.selected ?: return
        val progress = _state.value.preferences.progress[series.slug]
        viewModelScope.launch {
            runLoad(text(R.string.status_episodes_loading), { resumeSelected() }) {
                val detailed = repo.enrichSeries(series)
                val seasons = repo.seasons(detailed)
                val targetSeason = progress?.season ?: seasons.firstOrNull { it == 1 } ?: seasons.firstOrNull() ?: return@runLoad
                val episodes = repo.episodes(detailed, targetSeason)
                store.rememberSeasonTotal(detailed.slug, targetSeason, episodes.size)
                _state.update { it.copy(selected = detailed, seasons = seasons, season = targetSeason, episodes = episodes) }
                val target = progress?.let { saved -> episodes.firstOrNull { it.number == saved.episode } }
                    ?: episodes.firstOrNull { _state.value.preferences.episodeWatchStates[it.key]?.completed != true }
                    ?: episodes.firstOrNull()
                target?.let(::playEpisode)
            }
        }
    }

    fun setLastHomeTab(tab: HomeTab) = viewModelScope.launch { store.setLastHomeTab(tab.name) }.let { Unit }

    private fun saveSelection(series: Series, season: Int?) {
        savedStateHandle["selected_title"] = series.title
        savedStateHandle["selected_slug"] = series.slug
        savedStateHandle["selected_url"] = series.url
        savedStateHandle["selected_cover"] = series.coverUrl
        if (season == null) savedStateHandle.remove<Int>("selected_season") else savedStateHandle["selected_season"] = season
    }

    private fun clearSavedSelection() {
        listOf("selected_title", "selected_slug", "selected_url", "selected_cover", "selected_season").forEach { key -> savedStateHandle.remove<Any?>(key) }
    }

    private fun restoreSavedSelection() {
        val slug = savedStateHandle.get<String>("selected_slug").orEmpty()
        val url = savedStateHandle.get<String>("selected_url").orEmpty()
        if (slug.isBlank() || url.isBlank()) return
        val series = Series(
            title = savedStateHandle.get<String>("selected_title").orEmpty().ifBlank { slug.replace('-', ' ') },
            slug = slug,
            url = url,
            coverUrl = savedStateHandle.get<String>("selected_cover").orEmpty()
        )
        val restoredSeason = savedStateHandle.get<Int>("selected_season")
        viewModelScope.launch {
            _state.update { it.copy(selected = series, season = restoredSeason, loading = true) }
            try {
                val detailed = runCatching { repo.enrichSeries(series) }.getOrDefault(series)
                val seasons = repo.seasons(detailed)
                val episodes = restoredSeason?.let { repo.episodes(detailed, it) }.orEmpty()
                if (restoredSeason != null && episodes.isNotEmpty()) store.rememberSeasonTotal(detailed.slug, restoredSeason, episodes.size)
                _state.update { it.copy(selected = detailed, seasons = seasons, episodes = episodes, loading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false) }
                handleFailure(error) { restoreSavedSelection() }
            }
        }
    }

    private suspend fun runLoad(status: String, retry: () -> Unit, block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null, status = status) }
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { handleFailure(error, retry) }
        finally { _state.update { it.copy(loading = false) } }
    }

    private fun handleFailure(error: Exception, retry: () -> Unit) {
        if (error is ChallengeRequiredException && isAniWorldUrl(error.challengeUrl)) {
            pendingChallengeRetry = retry
            AppLogger.warn("Web-Schutz", error.challengeReason, error.challengeUrl)
            _state.update { it.copy(loading = false, resolving = false, challenge = ChallengeRequest(error.challengeUrl, error.challengeReason), challengeStatus = repo.challengeCookieSummary(error.challengeUrl), status = error.challengeReason, error = null) }
        } else {
            val message = friendlyMessage(error)
            AppLogger.error("App", message, error)
            _state.update { it.copy(error = message, status = null) }
        }
    }

    private fun isAniWorldUrl(value: String): Boolean = runCatching {
        val host = Uri.parse(value).host.orEmpty().lowercase()
        host == "aniworld.to" || host.endsWith(".aniworld.to")
    }.getOrDefault(false)

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> text(R.string.error_no_internet)
        is java.net.SocketTimeoutException -> text(R.string.error_timeout)
        is javax.net.ssl.SSLException -> text(R.string.error_ssl)
        is org.json.JSONException -> text(R.string.error_unexpected_response)
        else -> error.message?.takeIf { it.isNotBlank() } ?: text(R.string.error_unknown)
    }
}
