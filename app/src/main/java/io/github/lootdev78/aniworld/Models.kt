package io.github.lootdev78.aniworld

import androidx.annotation.StringRes
import java.util.UUID

private fun canonicalSeriesUrl(url: String, slug: String): String {
    val marker = "/anime/stream/$slug"
    val markerIndex = url.indexOf(marker, ignoreCase = true)
    return when {
        markerIndex >= 0 -> url.substring(0, markerIndex) + marker
        url.isNotBlank() -> url
        else -> "https://aniworld.to$marker"
    }
}

data class Series(
    val title: String,
    val slug: String,
    val url: String,
    val description: String = "",
    val coverUrl: String = "",
    val genres: List<String> = emptyList(),
    val year: String = "",
    val ageRating: String = "",
    val directors: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val imdbUrl: String = "",
    val userRating: String = "",
    val ratingCount: Int = 0
)

data class Episode(
    val season: Int,
    val number: Int,
    val title: String,
    val secondaryTitle: String = "",
    val description: String = "",
    val releasedAt: String = "",
    val url: String,
    val seriesSlug: String,
    val seriesTitle: String
) {
    val key: String get() = episodeKey(seriesSlug, season, number)
}

data class HomeEpisode(
    val series: Series,
    val episode: Episode,
    val releasedAt: String = "",
    val languages: List<Language> = emptyList(),
    val isNew: Boolean = false
)

data class HomeNews(
    val title: String,
    val url: String,
    val imageUrl: String = "",
    val subtitle: String = ""
)

data class MetadataImportResult(
    val catalogItems: Int,
    val homeFeedImported: Boolean
)

data class HomeFeed(
    val news: List<HomeNews> = emptyList(),
    val featured: Series? = null,
    val popularAtAniWorld: List<Series> = emptyList(),
    val latestEpisodes: List<HomeEpisode> = emptyList(),
    val newAnimes: List<Series> = emptyList(),
    val currentlyPopular: List<Series> = emptyList(),
    val communityWatching: List<Series> = emptyList(),
    val mostWatched: List<Series> = emptyList(),
    val loadedAt: Long = 0L
) {
    val isEmpty: Boolean get() = news.isEmpty() && popularAtAniWorld.isEmpty() && latestEpisodes.isEmpty() &&
        newAnimes.isEmpty() && currentlyPopular.isEmpty() && communityWatching.isEmpty() && mostWatched.isEmpty()
}

data class CatalogData(
    val items: List<Series> = emptyList(),
    val genres: List<String> = emptyList(),
    val letters: List<String> = listOf("#") + ('A'..'Z').map(Char::toString),
    val loadedAt: Long = 0L,
    val sourcePages: Int = 0
)

data class Hoster(val name: String, val langKey: Int, val lang: Language, val redirectUrl: String)

data class StreamSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val hoster: String = "",
    val language: Language = Language.GER_DUB,
    val mimeType: String? = null
)

data class ResolvedPlayback(
    val id: String = UUID.randomUUID().toString(),
    val series: Series,
    val episode: Episode,
    val stream: StreamSource,
    val startPositionMs: Long = 0L,
    val knownDurationMs: Long = 0L
) {
    val seriesTitle: String get() = series.title
}

data class ChallengeRequest(
    val url: String,
    val reason: String,
    val title: String = "",
    val retryAfterSuccess: Boolean = true,
    val mediaDetectionEnabled: Boolean = false
)

data class SessionCheck(val success: Boolean, val message: String)

data class ResolveResult(
    val stream: StreamSource?,
    val selectedHoster: Hoster?,
    val availableHosters: List<Hoster>,
    val log: List<String>,
    val challengeHoster: Hoster? = null,
    val challengeUrl: String = "",
    val challengeReason: String = ""
)

data class EpisodePage(
    val episode: Episode,
    val hosters: List<Hoster>
)

data class SeasonPage(
    val season: Int,
    val title: String,
    val description: String,
    val episodes: List<Episode>
)

data class SeriesCollectionPage(
    val title: String,
    val items: List<Series>
)

data class EpisodeCollectionPage(
    val title: String,
    val items: List<HomeEpisode>
)

enum class Language(val token: String, @StringRes val labelRes: Int, val flag: String) {
    GER_DUB("ger-dub", R.string.language_german_dub, "🇩🇪"),
    GER_SUB("ger-sub", R.string.language_german_sub, "🇩🇪"),
    ENG_DUB("eng-dub", R.string.language_english_dub, "🇬🇧"),
    ENG_SUB("eng-sub", R.string.language_english_sub, "🇬🇧"),
    JPN_DUB("jpn-dub", R.string.language_japanese_dub, "🇯🇵"),
    JPN_SUB("jpn-sub", R.string.language_japanese_sub, "🇯🇵"),
    JPN_ORIGINAL("jpn-original", R.string.language_japanese_original, "🇯🇵"),
    UNKNOWN("unknown", R.string.language_unknown, "🌐");

    companion object {
        val DEFAULT_PRIORITY = listOf(GER_DUB, GER_SUB, ENG_SUB, ENG_DUB, JPN_SUB, JPN_ORIGINAL, JPN_DUB)
        fun fromToken(token: String): Language = entries.firstOrNull { it.token == token } ?: UNKNOWN
    }
}

enum class LibrarySort(@StringRes val labelRes: Int) {
    CUSTOM(R.string.sort_custom),
    UPDATED(R.string.sort_updated),
    ALPHABETICAL(R.string.sort_alphabetical)
}

enum class LibraryViewMode(@StringRes val labelRes: Int) {
    COMPACT(R.string.view_compact),
    DETAILED(R.string.view_detailed),
    GRID(R.string.view_grid)
}

data class ProgressEntry(
    val seriesTitle: String,
    val seriesSlug: String,
    val seriesUrl: String,
    val coverUrl: String = "",
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val updatedAt: Long
)

data class WatchEntry(
    val title: String,
    val slug: String,
    val url: String,
    val coverUrl: String = "",
    val updatedAt: Long
)

data class FavoriteEntry(
    val title: String,
    val slug: String,
    val url: String,
    val coverUrl: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val updatedAt: Long
) {
    fun asSeries(): Series = Series(title, slug, canonicalSeriesUrl(url, slug), description, coverUrl, genres)
}

data class SearchEntry(val query: String, val updatedAt: Long)

data class EpisodeWatchState(
    val seriesTitle: String,
    val seriesSlug: String,
    val seriesUrl: String,
    val coverUrl: String = "",
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
) {
    val key: String get() = episodeKey(seriesSlug, season, episode)
    val progressFraction: Float get() = when {
        completed -> 1f
        durationMs <= 0L -> 0f
        else -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

data class WatchedSeriesEntry(
    val title: String,
    val slug: String,
    val url: String,
    val coverUrl: String,
    val watchedEpisodes: Int,
    val latestSeason: Int,
    val latestEpisode: Int,
    val updatedAt: Long
) {
    fun asSeries(): Series = Series(title, slug, canonicalSeriesUrl(url, slug), coverUrl = coverUrl)
}


enum class AppAccent {
    RED, BLUE, PURPLE, GREEN, ORANGE, CYAN, PINK
}

object HosterCatalog {
    val DEFAULT_PRIORITY = listOf("VOE", "Vidmoly", "Doodstream", "Filemoon")
    fun normalize(name: String): String {
        val compact = name.trim().lowercase().replace(" ", "").replace("-", "")
        return when {
            compact == "voe" || compact.startsWith("voe.") -> "voe"
            "vidmoly" in compact -> "vidmoly"
            "dood" in compact -> "doodstream"
            "filemoon" in compact -> "filemoon"
            else -> compact
        }
    }
    fun displayName(name: String): String = when (normalize(name)) {
        "voe" -> "VOE"
        "vidmoly" -> "Vidmoly"
        "doodstream" -> "Doodstream"
        "filemoon" -> "Filemoon"
        else -> name.trim()
    }
}

enum class HomeSection(@StringRes val labelRes: Int) {
    NEWS(R.string.anime_news),
    FEATURED(R.string.home_section_featured),
    FAVORITES(R.string.favorites),
    CONTINUE_WATCHING(R.string.continue_watching),
    POPULAR_AT_ANIWORLD(R.string.popular_at_aniworld),
    LATEST_EPISODES(R.string.latest_episodes),
    NEW_ANIMES(R.string.new_animes),
    CURRENTLY_POPULAR(R.string.currently_popular),
    COMMUNITY_WATCHING(R.string.community_watching),
    MOST_WATCHED(R.string.most_watched_top_50);

    companion object {
        val DEFAULT_ORDER: List<HomeSection> = entries.toList()
        fun normalizeOrder(raw: List<String>): List<HomeSection> {
            val parsed = raw.mapNotNull { value -> entries.firstOrNull { it.name == value } }.distinct()
            return parsed + DEFAULT_ORDER.filterNot(parsed::contains)
        }
    }
}

data class AppPreferences(
    val languagePriority: List<Language> = Language.DEFAULT_PRIORITY,
    val hosterPriority: List<String> = HosterCatalog.DEFAULT_PRIORITY,
    val verifyStreams: Boolean = true,
    val watchlist: List<WatchEntry> = emptyList(),
    val progress: Map<String, ProgressEntry> = emptyMap(),
    val favorites: List<FavoriteEntry> = emptyList(),
    val recentSearches: List<SearchEntry> = emptyList(),
    val episodeWatchStates: Map<String, EpisodeWatchState> = emptyMap(),
    val seasonEpisodeTotals: Map<String, Int> = emptyMap(),
    val favoriteOrder: List<String> = emptyList(),
    val watchedOrder: List<String> = emptyList(),
    val favoriteSort: LibrarySort = LibrarySort.CUSTOM,
    val watchedSort: LibrarySort = LibrarySort.UPDATED,
    val permissionIntroSeen: Boolean = false,
    val notificationPermissionAsked: Boolean = false,
    val useDynamicColors: Boolean = false,
    val lastHomeTab: String = "START",
    val initialPreloadCompleted: Boolean = false,
    val webAdBlockEnabled: Boolean = false,
    val webFilterLists: Set<String> = setOf("advertising", "tracking", "popups", "redirects"),
    val webSessionPanelExpanded: Boolean = true,
    val webMediaPanelExpanded: Boolean = true,
    val autoNextEnabled: Boolean = true,
    val autoPlayPreferredHoster: Boolean = false,
    val allowExternalPlayer: Boolean = false,
    val startupTab: String = "START",
    val homeOfflineMode: Boolean = false,
    val accentColor: AppAccent = AppAccent.RED,
    val settingsButtonX: Float = 0.92f,
    val settingsButtonY: Float = 0.72f,
    val catalogViewMode: LibraryViewMode = LibraryViewMode.DETAILED,
    val favoritesViewMode: LibraryViewMode = LibraryViewMode.DETAILED,
    val historyViewMode: LibraryViewMode = LibraryViewMode.DETAILED,
    val diagnosticsEnabled: Boolean = true,
    val castEnabled: Boolean = true,
    val pipEnabled: Boolean = true,
    val highlightStartedSeasons: Boolean = true,
    val startedSeasonHighlightAlpha: Float = 0.22f,
    val homeSectionOrder: List<HomeSection> = HomeSection.DEFAULT_ORDER,
    val hiddenHomeSections: Set<HomeSection> = emptySet()
) {
    fun isFavorite(slug: String): Boolean = favorites.any { it.slug == slug }
    fun episodeState(episode: Episode): EpisodeWatchState? = episodeWatchStates[episode.key]
    fun watchedCount(slug: String, season: Int? = null): Int = episodeWatchStates.values.count {
        it.seriesSlug == slug && it.completed && (season == null || it.season == season)
    }
    fun seasonTotal(slug: String, season: Int): Int? = seasonEpisodeTotals[seasonKey(slug, season)]

    fun watchedSeries(): List<WatchedSeriesEntry> = episodeWatchStates.values
        .filter { it.completed || it.positionMs > 0L }
        .groupBy { it.seriesSlug }
        .mapNotNull { (slug, states) ->
            val latest = states.maxByOrNull { it.updatedAt } ?: return@mapNotNull null
            WatchedSeriesEntry(
                title = latest.seriesTitle,
                slug = slug,
                url = latest.seriesUrl,
                coverUrl = latest.coverUrl,
                watchedEpisodes = states.count { it.completed },
                latestSeason = latest.season,
                latestEpisode = latest.episode,
                updatedAt = latest.updatedAt
            )
        }
}

data class DiagnosticEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val area: String,
    val message: String,
    val details: String = ""
) {
    fun asText(): String = buildString {
        append("[").append(level.name).append("] ").append(area).append(": ").append(message)
        if (details.isNotBlank()) append("\n").append(details)
    }
}

enum class LogLevel { INFO, WARNING, ERROR }

fun episodeKey(slug: String, season: Int, episode: Int): String = "$slug|$season|$episode"
fun seasonKey(slug: String, season: Int): String = "$slug|$season"
