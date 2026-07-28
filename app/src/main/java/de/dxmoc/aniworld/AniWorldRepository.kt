package de.dxmoc.aniworld

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Duration

class AniWorldRepository(
    private val context: Context,
    private val baseUrl: String = "https://aniworld.to",
    private val sessions: ChallengeSessionManager = ChallengeSessionManager(context),
    private val cache: RepositoryCache? = null,
    private val metadataDao: SeriesMetadataDao? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(WebViewCookieBridgeInterceptor(sessions))
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(Duration.ofSeconds(25))
        .build()
) {
    fun defaultChallengeUrl(): String = "$baseUrl/"

    fun challengeCookieSummary(url: String = defaultChallengeUrl()): String =
        sessions.cookieSummary(url)

    fun clearChallengeSession() = sessions.clear()

    suspend fun verifyChallengeSession(url: String): SessionCheck = withContext(Dispatchers.IO) {
        try {
            val req = baseRequest(url).get().build()
            client.newCall(req).execute().use { response ->
                val body = response.peekBody(512_000).string()
                ChallengeDetector.throwIfRequired(
                    context = context,
                    url = response.request.url.toString(),
                    statusCode = response.code,
                    contentType = response.header("Content-Type"),
                    body = body
                )
                if (response.isSuccessful) {
                    SessionCheck(true, context.getString(R.string.session_verification_applied, sessions.cookieSummary(response.request.url.toString())))
                } else {
                    SessionCheck(false, context.getString(R.string.session_check_failed_http, response.code))
                }
            }
        } catch (e: ChallengeRequiredException) {
            SessionCheck(false, e.challengeReason)
        } catch (e: Exception) {
            SessionCheck(false, e.message ?: context.getString(R.string.session_check_failed))
        }
    }
    suspend fun search(query: String): List<Series> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val body = FormBody.Builder().add("keyword", query.trim()).build()
        val req = Request.Builder()
            .url("$baseUrl/ajax/search")
            .post(body)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", UA)
            .header("Referer", "$baseUrl/")
            .build()
        AppLogger.info("Netzwerk", "POST /ajax/search", "Suchbegriff-Länge: ${query.trim().length}")
        client.newCall(req).execute().use { r ->
            val responseBody = r.body.string()
            ChallengeDetector.throwIfRequired(
                context = context,
                url = r.request.url.toString(),
                statusCode = r.code,
                contentType = r.header("Content-Type"),
                body = responseBody
            )
            if (!r.isSuccessful) error("Suche fehlgeschlagen: HTTP ${r.code}")
            val arr = JSONArray(responseBody)
            val seen = mutableSetOf<String>()
            val out = mutableListOf<Series>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val link = o.optString("link")
                val parts = link.trim('/').split('/')
                if (parts.size != 3 || parts[0] != "anime" || parts[1] != "stream") continue
                val slug = parts[2]
                if (!seen.add(slug)) continue
                out += Series(
                    title = clean(o.optString("title")),
                    slug = slug,
                    url = "$baseUrl$link",
                    description = clean(o.optString("description")),
                    coverUrl = normalizeAnimeImageUrl(
                        o.optString("image")
                            .ifBlank { o.optString("cover") }
                            .ifBlank { o.optString("poster") },
                        "$baseUrl$link"
                    ).orEmpty()
                )
            }
            out
        }
    }

    suspend fun homeFeed(forceRefresh: Boolean = false): HomeFeed = withContext(Dispatchers.IO) {
        val parsed = parseHomeFeed(getText("$baseUrl/", forceRefresh = forceRefresh))
        var feed = parsed
        val hero = feed.featured
        if (hero != null && (hero.coverUrl.isBlank() || hero.description.isBlank())) {
            val detailed = runCatching { enrichSeries(hero, forceRefresh) }.getOrDefault(hero)
            feed = feed.replaceSeries(detailed)
        }
        feed
    }

    suspend fun catalog(forceRefresh: Boolean = false): CatalogData = withContext(Dispatchers.IO) {
        val cachedModels = metadataDao?.all().orEmpty().map(SeriesMetadataEntity::toModel)
        try {
            val bySlug = linkedMapOf<String, Series>()
            val firstPages = mutableListOf<CatalogPage>()
            for (keys in CATALOG_KEYS.chunked(CATALOG_FETCH_CONCURRENCY)) {
                firstPages.addAll(
                    coroutineScope {
                        keys.map { catalogKey ->
                            async {
                                val url = "$baseUrl/katalog/$catalogKey"
                                CatalogPage(catalogKey, 1, url, getText(url, forceRefresh = forceRefresh))
                            }
                        }.awaitAll()
                    }
                )
            }
            firstPages.forEach { page ->
                parseCatalogPage(page.markup, page.url).forEach { mergeCatalogSeries(bySlug, it) }
            }
            val remaining = firstPages.flatMap { first ->
                val pageCount = parseCatalogPageCount(first.markup, first.catalogKey)
                (2..pageCount).map { page ->
                    val url = "$baseUrl/katalog/${first.catalogKey}/$page"
                    Triple(first.catalogKey, page, url)
                }
            }
            val additionalPages = mutableListOf<CatalogPage>()
            for (pages in remaining.chunked(CATALOG_FETCH_CONCURRENCY)) {
                additionalPages.addAll(
                    coroutineScope {
                        pages.map { (catalogKey, page, url) ->
                            async {
                                CatalogPage(catalogKey, page, url, getText(url, forceRefresh = forceRefresh))
                            }
                        }.awaitAll()
                    }
                )
            }
            additionalPages.forEach { page ->
                parseCatalogPage(page.markup, page.url).forEach { mergeCatalogSeries(bySlug, it) }
            }
            cachedModels.forEach { cached ->
                val current = bySlug[cached.slug] ?: return@forEach
                bySlug[cached.slug] = mergeSeriesMetadata(current, cached)
            }
            val items = bySlug.values.sortedBy { it.title.lowercase() }
            CatalogData(
                items = items,
                genres = items.flatMap { it.genres }.filter(String::isNotBlank).distinct().sortedBy { it.lowercase() },
                loadedAt = System.currentTimeMillis(),
                sourcePages = firstPages.size + additionalPages.size
            )
        } catch (error: Exception) {
            if (cachedModels.isNotEmpty()) {
                AppLogger.warn("Katalog", "Offline-Metadaten werden verwendet", error.message.orEmpty())
                CatalogData(
                    items = cachedModels.sortedBy { it.title.lowercase() },
                    genres = cachedModels.flatMap { it.genres }.filter(String::isNotBlank).distinct().sortedBy { it.lowercase() },
                    loadedAt = System.currentTimeMillis(),
                    sourcePages = 0
                )
            } else throw error
        }
    }

    suspend fun enrichSeries(
        series: Series,
        forceRefresh: Boolean = false,
        cacheCatalogMetadata: Boolean = false
    ): Series = withContext(Dispatchers.IO) {
        val cached = if (cacheCatalogMetadata) metadataDao?.get(series.slug) else null
        val cacheFresh = cached != null && System.currentTimeMillis() - cached.updatedAt < METADATA_TTL_MS
        val cachedModel = cached?.toModel()
        val seed = cachedModel?.let { mergeSeriesMetadata(series, it) } ?: series

        if (!forceRefresh && cacheCatalogMetadata && cacheFresh && seed.hasCatalogMetadata()) {
            return@withContext seed
        }

        val parsed = parseSeriesDetails(
            getText(series.url, forceRefresh = forceRefresh || !cacheCatalogMetadata),
            seed
        )
        val detailed = mergeSeriesMetadata(series, parsed)
        if (cacheCatalogMetadata) metadataDao?.upsert(SeriesMetadataEntity.from(detailed))
        detailed
    }

    suspend fun preloadCatalogMetadata(
        items: List<Series>,
        force: Boolean = false,
        onProgress: suspend (completed: Int, total: Int, series: Series?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val existing = metadataDao?.all().orEmpty().associateBy { it.slug }
        val total = items.size
        val queue = if (force) {
            items
        } else {
            items.filter { item ->
                val cached = existing[item.slug]
                cached == null || !cached.toModel().hasCatalogMetadata() ||
                    System.currentTimeMillis() - cached.updatedAt >= METADATA_TTL_MS
            }
        }
        var completed = total - queue.size
        onProgress(completed, total, null)
        for (batch in queue.chunked(3)) {
            val results = coroutineScope {
                batch.map { item ->
                    async {
                        runCatching { enrichSeries(item, forceRefresh = true, cacheCatalogMetadata = true) }
                            .onFailure { AppLogger.warn("Metadaten", "${item.title} konnte nicht vorgeladen werden", it.message.orEmpty()) }
                            .getOrDefault(item)
                    }
                }.awaitAll()
            }
            for (result in results) {
                completed++
                onProgress(completed, total, result)
            }
            delay(180L)
        }
    }

    suspend fun seriesDetails(series: Series): Series = enrichSeries(series)

    suspend fun seasons(series: Series): List<Int> = withContext(Dispatchers.IO) {
        parseSeasons(getText(series.url), series.slug)
    }

    suspend fun episodes(series: Series, season: Int): List<Episode> = withContext(Dispatchers.IO) {
        val path = seasonPath(series.slug, season)
        parseEpisodes(getText(baseUrl + path), series, season)
    }

    suspend fun episodePage(episode: Episode): EpisodePage = withContext(Dispatchers.IO) {
        val markup = getText(episode.url)
        EpisodePage(parseEpisodeDetails(markup, episode), parseHosters(markup))
    }

    suspend fun episodeDetails(episode: Episode): Episode = withContext(Dispatchers.IO) {
        parseEpisodeDetails(getText(episode.url), episode)
    }

    suspend fun listHosters(episode: Episode): List<Hoster> = episodePage(episode).hosters

    suspend fun resolveEpisode(
        episode: Episode,
        languagePriority: List<Language>,
        hosterPriority: List<String>,
        verifyStreams: Boolean = true,
        languageOverride: Language? = null,
        hosterOverride: Hoster? = null
    ): ResolveResult = withContext(Dispatchers.IO) {
        val markup = getText(episode.url)
        val hosters = parseHosters(markup)
        val ordered = if (hosterOverride == null) {
            orderHosters(hosters, languagePriority, hosterPriority, languageOverride)
        } else {
            hosters.filter { it.redirectUrl == hosterOverride.redirectUrl }
        }
        val log = mutableListOf<String>()
        val langs = availableLanguages(hosters, languagePriority)
        if (langs.isNotEmpty()) log += "Sprachen: ${langs.joinToString { it.localizedLabel(context) }}"
        if (languageOverride != null) log += "Sprachfilter: ${languageOverride.localizedLabel(context)}"
        for (hoster in ordered) {
            log += "Prüfe ${hoster.name} (${hoster.lang.localizedLabel(context)})"
            val stream = when (HosterCatalog.normalize(hoster.name)) {
                "voe" -> VoeExtractor.extract(context, hoster.redirectUrl, hoster, client)
                "vidmoly" -> VidmolyExtractor.extract(context, hoster.redirectUrl, hoster, client)
                "doodstream" -> PublicMediaExtractor.extract(context, hoster.redirectUrl, hoster, client)
                "filemoon" -> PublicMediaExtractor.extract(context, hoster.redirectUrl, hoster, client)
                else -> GenericMarkupExtractor.extract(context, hoster.redirectUrl, hoster, client)
            }
            if (stream == null) {
                val normalized = HosterCatalog.normalize(hoster.name)
                if (normalized == "doodstream" || normalized == "filemoon") {
                    log += "${HosterCatalog.displayName(hoster.name)}: kein direkt deklarierter Media-Stream gefunden; extern öffnen möglich"
                } else {
                    log += "${hoster.name}: kein Stream gefunden"
                }
                continue
            }
            if (verifyStreams && !streamIsLive(stream)) {
                log += "${hoster.name}: Stream lieferte keine Daten"
                continue
            }
            log += "${hoster.name}: abspielbarer Stream gefunden"
            return@withContext ResolveResult(stream, hoster, hosters, log)
        }
        ResolveResult(null, null, hosters, log)
    }

    private data class CatalogPage(
        val catalogKey: String,
        val page: Int,
        val url: String,
        val markup: String
    )

    private fun parseCatalogPage(markup: String, pageUrl: String): List<Series> {
        val doc = Jsoup.parse(markup, pageUrl)
        val seen = linkedSetOf<String>()
        return doc.select("a[href]").mapNotNull { anchor ->
            seriesFromAnchor(anchor)?.takeIf { seen.add(it.slug) }
        }
    }

    private fun parseCatalogPageCount(markup: String, catalogKey: String): Int {
        val doc = Jsoup.parse(markup, "$baseUrl/katalog/$catalogKey")
        val pattern = Regex("^/katalog/${Regex.escape(catalogKey)}(?:/(\\d+))?/?$", RegexOption.IGNORE_CASE)
        return doc.select("a[href]").mapNotNull { anchor ->
            val path = pathOf(anchor) ?: return@mapNotNull null
            val match = pattern.matchEntire(path) ?: return@mapNotNull null
            match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        }.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun mergeCatalogSeries(target: MutableMap<String, Series>, incoming: Series) {
        val current = target[incoming.slug]
        target[incoming.slug] = if (current == null) incoming else mergeSeriesMetadata(current, incoming)
    }

    private fun mergeSeriesMetadata(base: Series, metadata: Series): Series = base.copy(
        title = metadata.title.ifBlank { base.title },
        description = metadata.description.ifBlank { base.description },
        coverUrl = normalizeAnimeImageUrl(metadata.coverUrl, metadata.url.ifBlank { base.url })
            ?: normalizeAnimeImageUrl(base.coverUrl, base.url)
            ?: "",
        genres = (base.genres + metadata.genres).filter(String::isNotBlank).distinct(),
        year = metadata.year.ifBlank { base.year },
        ageRating = metadata.ageRating.ifBlank { base.ageRating },
        directors = (base.directors + metadata.directors).filter(String::isNotBlank).distinct(),
        producers = (base.producers + metadata.producers).filter(String::isNotBlank).distinct(),
        actors = (base.actors + metadata.actors).filter(String::isNotBlank).distinct(),
        countries = (base.countries + metadata.countries).filter(String::isNotBlank).distinct(),
        imdbUrl = metadata.imdbUrl.ifBlank { base.imdbUrl },
        userRating = metadata.userRating.ifBlank { base.userRating },
        ratingCount = metadata.ratingCount.takeIf { it > 0 } ?: base.ratingCount
    )

    private fun Series.hasCompleteMetadata(): Boolean =
        title.isNotBlank() && normalizeAnimeImageUrl(coverUrl, url) != null &&
            description.isNotBlank() && genres.isNotEmpty()

    private fun Series.hasCatalogMetadata(): Boolean =
        title.isNotBlank() && description.isNotBlank() && genres.isNotEmpty()

    private fun HomeFeed.withMetadata(cached: Map<String, SeriesMetadataEntity>): HomeFeed {
        fun apply(series: Series): Series = cached[series.slug]?.toModel()?.let { mergeSeriesMetadata(series, it) } ?: series
        return copy(
            featured = featured?.let(::apply),
            popularAtAniWorld = popularAtAniWorld.map(::apply),
            latestEpisodes = latestEpisodes.map { item ->
                val detailed = apply(item.series)
                item.copy(series = detailed, episode = item.episode.copy(seriesTitle = detailed.title))
            },
            newAnimes = newAnimes.map(::apply),
            currentlyPopular = currentlyPopular.map(::apply),
            communityWatching = communityWatching.map(::apply),
            mostWatched = mostWatched.map(::apply)
        )
    }

    private fun HomeFeed.replaceSeries(detailed: Series): HomeFeed {
        fun replace(item: Series): Series = if (item.slug == detailed.slug) detailed else item
        return copy(
            featured = featured?.let(::replace),
            popularAtAniWorld = popularAtAniWorld.map(::replace),
            latestEpisodes = latestEpisodes.map { item ->
                if (item.series.slug == detailed.slug) {
                    item.copy(series = detailed, episode = item.episode.copy(seriesTitle = detailed.title))
                } else item
            },
            newAnimes = newAnimes.map(::replace),
            currentlyPopular = currentlyPopular.map(::replace),
            communityWatching = communityWatching.map(::replace),
            mostWatched = mostWatched.map(::replace)
        )
    }

    private fun parseHomeFeed(markup: String): HomeFeed {
        val doc = Jsoup.parse(markup, "$baseUrl/")
        val popular = parseSeriesSection(doc, "Beliebt bei AniWorld").take(24)
        val latest = parseLatestEpisodes(doc).take(50)
        val newAnimes = parseSeriesSection(doc, "Neue Animes").take(30)
        val currentlyPopular = parseSeriesSection(doc, "Derzeit beliebt").take(24)
        val community = parseSeriesSection(doc, "Das sehen andere AniWorld Nutzer").take(24)
        val mostWatched = sequenceOf("Top 50", "Meistgesehen", "Top 50 Animes")
            .map { heading -> parseSeriesSection(doc, heading) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .distinctBy(Series::slug)
            .take(50)
        val featured = community.firstOrNull { it.description.isNotBlank() && it.coverUrl.isNotBlank() }
            ?: popular.firstOrNull { it.coverUrl.isNotBlank() }
            ?: currentlyPopular.firstOrNull()
            ?: newAnimes.firstOrNull()
        return HomeFeed(
            featured = featured,
            popularAtAniWorld = popular,
            latestEpisodes = latest,
            newAnimes = newAnimes,
            currentlyPopular = currentlyPopular,
            communityWatching = community,
            mostWatched = mostWatched,
            loadedAt = System.currentTimeMillis()
        )
    }

    private fun parseSeriesSection(doc: Document, heading: String): List<Series> {
        val seen = linkedSetOf<String>()
        return sectionAnchors(doc, heading).mapNotNull { anchor ->
            seriesFromAnchor(anchor)?.takeIf { seen.add(it.slug) }
        }
    }

    private fun parseLatestEpisodes(doc: Document): List<HomeEpisode> {
        val episodeRegex = Regex("""^/anime/stream/([^/]+)/staffel-(\d+)/episode-(\d+)/?$""")
        val movieRegex = Regex("""^/anime/stream/([^/]+)/filme/film-(\d+)/?$""")
        val dateRegex = Regex("""(?:Mo|Di|Mi|Do|Fr|Sa|So),?\s*\d{1,2}\.\d{1,2}\.\d{4}\s+\d{1,2}:\d{2}\s+Uhr""", RegexOption.IGNORE_CASE)
        val byPath = linkedMapOf<String, HomeEpisode>()
        sectionAnchors(doc, "Die 50 neuesten Episoden").forEach { anchor ->
            val path = pathOf(anchor) ?: return@forEach
            val episodeMatch = episodeRegex.matchEntire(path)
            val movieMatch = movieRegex.matchEntire(path)
            if (episodeMatch == null && movieMatch == null) return@forEach
            val slug = episodeMatch?.groupValues?.get(1) ?: movieMatch!!.groupValues[1]
            val season = episodeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val number = episodeMatch?.groupValues?.get(3)?.toIntOrNull()
                ?: movieMatch?.groupValues?.get(2)?.toIntOrNull()
                ?: return@forEach
            val container = closestContentContainer(anchor) ?: anchor
            val raw = clean(anchor.text())
            val seriesTitleFromText = raw
                .replace(dateRegex, "")
                .replace(Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE), "")
                .trim(' ', '-', '–', '—', '·')
            val seriesTitle = firstCleanText(
                container.selectFirst(".series-title, .seriesTitle, h3, h4, [itemprop=name]")?.text(),
                anchor.selectFirst(".series-title, .seriesTitle, h3, h4, [itemprop=name]")?.text(),
                seriesTitleFromText
            ).removePrefix("Cover von ").trim().ifBlank {
                slug.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            }
            val imageTitle = container.select("img[alt], img[title]").asSequence()
                .map { firstCleanText(it.attr("alt"), it.attr("title")) }
                .mapNotNull { extractEpisodeTitleFromLabel(it, number) }
                .firstOrNull().orEmpty()
            val explicitEpisodeTitle = firstCleanText(
                container.selectFirst(".episodeTitle, .episode-title, .episodeGermanTitle, .episodeEnglishTitle")?.text(),
                anchor.selectFirst(".episodeTitle, .episode-title, .episodeGermanTitle, .episodeEnglishTitle")?.text(),
                imageTitle
            )
            val releasedAt = dateRegex.find(container.text())?.value.orEmpty()
            val series = Series(
                title = seriesTitle,
                slug = slug,
                url = "$baseUrl/anime/stream/$slug",
                description = firstCleanText(container.selectFirst("p, .description, [itemprop=description]")?.text()),
                coverUrl = imageUrl(container, anchor.absUrl("href")),
                genres = genreTexts(container, seriesTitle)
            )
            val languages = container.select("img[alt], img[title]").mapNotNull { img ->
                classifyLanguage(img.attr("src"), img.attr("alt"), img.attr("title")).takeIf { it != Language.UNKNOWN }
            }.distinct()
            val incoming = HomeEpisode(
                series = series,
                episode = Episode(
                    season = season,
                    number = number,
                    title = normalizeEpisodeTitle(explicitEpisodeTitle, season, number),
                    releasedAt = releasedAt,
                    url = anchor.absUrl("href").ifBlank { "$baseUrl$path" },
                    seriesSlug = slug,
                    seriesTitle = series.title
                ),
                releasedAt = releasedAt,
                languages = languages,
                isNew = container.text().contains("Neu!", ignoreCase = true)
            )
            val current = byPath[path]
            byPath[path] = if (current == null) incoming else current.copy(
                series = mergeSeriesMetadata(current.series, incoming.series),
                episode = current.episode.copy(
                    title = current.episode.title.ifBlank { incoming.episode.title },
                    releasedAt = current.episode.releasedAt.ifBlank { incoming.episode.releasedAt }
                ),
                releasedAt = current.releasedAt.ifBlank { incoming.releasedAt },
                languages = (current.languages + incoming.languages).distinct(),
                isNew = current.isNew || incoming.isNew
            )
        }
        return byPath.values.toList()
    }

    private fun extractEpisodeTitleFromLabel(label: String, number: Int): String? {
        if (label.isBlank()) return null
        val decoded = decodeHtmlEntities(label)
        val match = Regex("""(?:Episode|Folge|Film)\s*$number\s*-\s*(.+?)(?:\s+(?:auf|mit)\s+|$)""", RegexOption.IGNORE_CASE).find(decoded)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun sectionAnchors(doc: Document, heading: String): List<Element> {
        val normalizedTarget = normalizeHeading(heading)
        val all = doc.allElements
        val start = all.indexOfFirst { element ->
            element.tagName() in setOf("h1", "h2") && normalizeHeading(element.text()) == normalizedTarget
        }
        if (start < 0) return emptyList()
        val output = mutableListOf<Element>()
        for (index in (start + 1) until all.size) {
            val element = all[index]
            if (element.tagName() in setOf("h1", "h2")) break
            if (element.tagName() == "a" && element.hasAttr("href")) output.add(element)
        }
        return output.distinctBy { pathOf(it).orEmpty() + "|" + clean(it.text()) }
    }

    private fun seriesFromAnchor(anchor: Element): Series? {
        val path = pathOf(anchor) ?: return null
        val match = Regex("""^/anime/stream/([^/]+)/?$""").matchEntire(path) ?: return null
        val slug = match.groupValues[1]
        val source = closestContentContainer(anchor) ?: anchor
        val linkedGenres = source.select("a[href^=/genre/], a[href*=/genre/]")
            .eachText().map(::clean).filter(::isDisplayGenre).distinct()
        val knownGenres = (linkedGenres + genreTexts(source, ""))
            .filter(::isDisplayGenre)
            .distinct()
        val imageAlt = firstCleanText(
            source.selectFirst("img[alt]")?.attr("alt"),
            anchor.selectFirst("img[alt]")?.attr("alt")
        ).replace(Regex(""",?\s*Cover,?\s*HD,?\s*Anime Stream.*$""", RegexOption.IGNORE_CASE), "")
            .removePrefix("Cover von ").trim()
        val rawText = clean(anchor.text())
        val title = firstCleanText(
            anchor.selectFirst("[itemprop=name], .series-title, .seriesTitle, .title, h3, h4")?.text(),
            source.selectFirst("[itemprop=name], .series-title, .seriesTitle, .title, h3, h4")?.text(),
            anchor.attr("title"),
            imageAlt,
            knownGenres.sortedByDescending(String::length)
                .fold(rawText) { value, genre ->
                    value.replace(Regex("""\s+${Regex.escape(genre)}\s*$""", RegexOption.IGNORE_CASE), "").trim()
                }
        ).removePrefix("Cover von ").trim().ifBlank {
            slug.replace('-', ' ').split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
        }
        return Series(
            title = title,
            slug = slug,
            url = anchor.absUrl("href").ifBlank { "$baseUrl$path" },
            description = firstCleanText(
                source.selectFirst("[itemprop=description], .description, .seriesDescription, .seri_des, p")?.text()
            ),
            coverUrl = imageUrl(source, anchor.absUrl("href").ifBlank { "$baseUrl$path" }),
            genres = (knownGenres + genreTexts(source, title)).filter(::isDisplayGenre).distinct().take(8)
        )
    }

    private fun closestContentContainer(anchor: Element): Element? =
        anchor.closest("li, article, tr, .seriesListContainer, .seriesList, .latestEpisode, .episode, .col, .card, .seriesListItem, .seriesListElement")
            ?: anchor.parent()

    private fun imageUrl(source: Element, base: String): String {
        data class Candidate(val url: String, val score: Int)
        val candidates = source.select("img").flatMap { image ->
            val context = listOf(
                image.attr("alt"), image.attr("title"), image.className(), image.id(),
                image.parent()?.className().orEmpty(), image.parent()?.id().orEmpty()
            ).joinToString(" ").lowercase()
            val rejectedContext = listOf(
                "logo", "brand", "header", "navigation", "navbar", "avatar", "icon",
                "placeholder", "banner", "social", "tracking", "pixel", "spinner"
            ).any(context::contains)
            val width = image.attr("width").filter(Char::isDigit).toIntOrNull()
            val height = image.attr("height").filter(Char::isDigit).toIntOrNull()
            val implausibleSize = width != null && height != null &&
                (width < 100 || height < 120 || width.toFloat() / height.coerceAtLeast(1) > 1.65f)
            if (rejectedContext || implausibleSize) return@flatMap emptyList()
            val baseScore = when {
                image.closest(".seriesCoverBox, .seriesCover, .cover, [class*=cover], [class*=poster]") != null -> 80
                image.hasAttr("itemprop") && image.attr("itemprop").equals("image", true) -> 70
                context.contains("cover") || context.contains("poster") -> 60
                else -> 0
            }
            sequenceOf(
                image.attr("data-src") to 12,
                image.attr("data-lazy-src") to 11,
                image.attr("data-original") to 10,
                image.attr("data-url") to 8,
                image.attr("srcset").substringBefore(' ') to 6,
                image.attr("src") to 4
            ).mapNotNull { (raw, sourceScore) ->
                normalizeAnimeImageUrl(raw, base)?.let { normalized ->
                    val path = normalized.lowercase()
                    val pathScore = when {
                        path.contains("/cover/") || path.contains("poster") -> 35
                        path.contains("anime") || path.contains("series") -> 15
                        else -> 0
                    }
                    Candidate(normalized, baseScore + sourceScore + pathScore)
                }
            }.toList()
        }
        return candidates
            .groupBy(Candidate::url)
            .map { (url, matches) -> Candidate(url, matches.maxOf(Candidate::score)) }
            .maxByOrNull(Candidate::score)
            ?.url
            .orEmpty()
            .ifBlank { backgroundImageUrl(source, base) }
    }

    private fun backgroundImageUrl(source: Element, base: String): String {
        val style = source.attr("style") + " " + source.selectFirst("[style*=background-image]")?.attr("style").orEmpty()
        val raw = Regex("url\\(['\"]?([^'\")]+)").find(style)?.groupValues?.get(1) ?: return ""
        return normalizeAnimeImageUrl(raw, base).orEmpty()
    }

    private fun genreTexts(source: Element, title: String): List<String> {
        val linked = source.select("a[href^=/genre/], a[href*=/genre/]").eachText()
        val fallback = source.select(".genre, .genres, [class*=genre], .label, .badge").eachText()
        return (linked + fallback)
            .flatMap { it.split(',', '·', '|') }
            .map(::clean)
            .filter { value ->
                isDisplayGenre(value) && !value.equals(title, ignoreCase = true) &&
                    !value.equals("Neu!", ignoreCase = true) && value.length <= 40
            }
            .distinct()
            .take(8)
    }


    private fun isDisplayGenre(value: String): Boolean {
        val normalized = clean(value).lowercase().replace("-", "").replace(" ", "")
        return normalized.isNotBlank() && normalized !in setOf(
            "ger", "gersub", "engsub", "deutsch", "englisch", "german", "english",
            "mituntertiteldeutsch", "mituntertitelenglisch"
        )
    }

    private fun pathOf(anchor: Element): String? {
        val href = decodeHtmlEntities(anchor.attr("href")).trim()
        if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")) return null
        return runCatching {
            val uri = URI(href)
            if (uri.isAbsolute) uri.path else URI("$baseUrl/").resolve(uri).path
        }.getOrNull()?.let { if (it.startsWith('/')) it else "/$it" }
    }

    private fun normalizeHeading(value: String): String = clean(value).lowercase().replace(Regex("\\s+"), " ")

    private fun parseSeasons(markup: String, slug: String): List<Int> {
        val doc = Jsoup.parse(markup)
        val found = sortedSetOf<Int>()
        val staffel = Regex("^/anime/stream/${Regex.escape(slug)}/staffel-(\\d+)/?$")
        val filme = Regex("^/anime/stream/${Regex.escape(slug)}/filme/?$")
        doc.select("div#stream a[href]").forEach { a ->
            val href = a.attr("href")
            staffel.matchEntire(href)?.let { found += it.groupValues[1].toInt() }
            if (filme.matches(href)) found += 0
        }
        return found.toList()
    }

    private fun parseEpisodes(markup: String, series: Series, season: Int): List<Episode> {
        val pageUrl = baseUrl + seasonPath(series.slug, season)
        val doc = Jsoup.parse(markup, pageUrl)
        val out = linkedMapOf<Int, Episode>()
        val pattern = if (season == 0) {
            Regex("""^/anime/stream/${Regex.escape(series.slug)}/filme/film-(\d+)/?$""")
        } else {
            Regex("""^/anime/stream/${Regex.escape(series.slug)}/staffel-$season/episode-(\d+)/?$""")
        }
        doc.select("a[href]").forEach { anchor ->
            val path = pathOf(anchor) ?: return@forEach
            val match = pattern.matchEntire(path) ?: return@forEach
            val number = match.groupValues[1].toIntOrNull() ?: return@forEach
            val row = anchor.closest("tr, li, .episode, .seasonEpisode, .episodeListItem") ?: anchor.parent() ?: anchor
            val cells = row.select("td")
            val combinedTitle = firstCleanText(
                row.selectFirst(".episodeGermanTitle, .seasonEpisodeTitle, .episodeTitle, .episode-title, [itemprop=name]")?.text(),
                anchor.selectFirst(".episodeGermanTitle, .seasonEpisodeTitle, .episodeTitle, .episode-title, [itemprop=name]")?.text(),
                cells.getOrNull(1)?.text(),
                anchor.attr("title"),
                anchor.selectFirst("strong")?.text(),
                anchor.text()
            )
            val explicitSecondary = firstCleanText(
                row.selectFirst(".episodeEnglishTitle, .episodeOriginalTitle, small")?.text(),
                anchor.selectFirst(".episodeEnglishTitle, .episodeOriginalTitle, small")?.text(),
                cells.getOrNull(2)?.takeIf { it.select("li[data-link-target], img[data-lang-key]").isEmpty() }?.text()
            )
            val normalizedCombined = normalizeEpisodeTitle(combinedTitle, season, number)
            val splitTitles = if (explicitSecondary.isBlank()) {
                normalizedCombined.split(Regex("""\s+-\s+"""), limit = 2)
            } else {
                listOf(normalizedCombined)
            }
            val primary = splitTitles.firstOrNull().orEmpty().trim()
            val secondary = normalizeEpisodeTitle(
                explicitSecondary.ifBlank { splitTitles.getOrNull(1).orEmpty() },
                season,
                number
            ).takeUnless { it.equals(primary, ignoreCase = true) }.orEmpty()
            val fallback = if (season == 0) "Film $number" else "Folge $number"
            val current = out[number]
            val candidate = Episode(
                season = season,
                number = number,
                title = primary.ifBlank { fallback },
                secondaryTitle = secondary,
                url = anchor.absUrl("href").ifBlank { "$baseUrl$path" },
                seriesSlug = series.slug,
                seriesTitle = series.title
            )
            if (current == null || candidate.title != fallback || current.title == fallback) out[number] = candidate
        }
        return out.values.sortedBy { it.number }
    }

    private fun parseEpisodeDetails(markup: String, episode: Episode): Episode {
        val doc = Jsoup.parse(markup, episode.url)
        val genericHeadings = setOf(
            "episoden der staffel ${episode.season}",
            "wähle einen aniworld stream / hoster:",
            "weitere erstklassige staffeln von ${episode.seriesTitle.lowercase()} als stream"
        )
        val heading = doc.select("#stream h2, main h2, h2, .episodeTitle, .episode-title")
            .firstOrNull { element ->
                val value = clean(element.text())
                value.isNotBlank() && value.lowercase() !in genericHeadings &&
                    !value.startsWith("Weitere ", true) &&
                    !value.startsWith("Folgende Animes", true) &&
                    !value.contains("Stream / Hoster", true)
            }
        val pageTitle = heading?.text()?.let(::clean).orEmpty()
        val currentTitle = episode.title.takeUnless { isGenericEpisodeTitle(it, episode) }.orEmpty()
        val currentSecondary = episode.secondaryTitle
        val title = when {
            currentTitle.isNotBlank() -> currentTitle
            pageTitle.isNotBlank() -> normalizeEpisodeTitle(pageTitle, episode.season, episode.number)
            else -> episode.title
        }
        val secondary = when {
            currentSecondary.isNotBlank() -> currentSecondary
            currentTitle.isNotBlank() && pageTitle.startsWith(currentTitle, ignoreCase = true) ->
                pageTitle.substring(currentTitle.length).trim(' ', '-', '–', '—', ':')
            else -> ""
        }
        val nearbyDescription = heading?.let { episodeHeading ->
            generateSequence(episodeHeading.nextElementSibling()) { it.nextElementSibling() }
                .take(6)
                .flatMap { element -> sequenceOf(element) + element.select("p, .description").asSequence() }
                .map { clean(it.text()) }
                .firstOrNull(::isEpisodeDescriptionCandidate)
        }
        val description = firstCleanText(
            doc.selectFirst("#stream .episodeDescription, #stream .episode-description, #stream .episodeDesc, #stream .episode-desc, .episodeContent .description, .episode-content .description")?.text(),
            nearbyDescription,
            doc.select("#stream p, .episodeContent p, .episode-content p").map { clean(it.text()) }.firstOrNull(::isEpisodeDescriptionCandidate)
        )
        val releasedAt = Regex(
            """(?:Montag|Dienstag|Mittwoch|Donnerstag|Freitag|Samstag|Sonntag),?\s*\d{1,2}\.\d{1,2}\.\d{4}\s+\d{1,2}:\d{2}\s+Uhr""",
            RegexOption.IGNORE_CASE
        ).find(doc.text())?.value.orEmpty()
        return episode.copy(
            title = title,
            secondaryTitle = secondary,
            description = cleanDescription(description).ifBlank { episode.description },
            releasedAt = releasedAt.ifBlank { episode.releasedAt }
        )
    }

    private fun isGenericEpisodeTitle(value: String, episode: Episode): Boolean {
        val normalized = clean(value).lowercase()
        return normalized.isBlank() || normalized in setOf(
            "folge ${episode.number}",
            "episode ${episode.number}",
            "film ${episode.number}",
            "folge %02d".format(episode.number),
            "episode %02d".format(episode.number),
            "film %02d".format(episode.number)
        )
    }

    private fun isEpisodeDescriptionCandidate(value: String): Boolean {
        val text = clean(value)
        return text.length >= 18 &&
            !text.equals("Keine Beschreibung verfügbar.", ignoreCase = true) &&
            !text.contains("Wähle einen AniWorld Stream", ignoreCase = true) &&
            !text.contains("Klicke hier", ignoreCase = true) &&
            !text.contains("mehr anzeigen", ignoreCase = true) &&
            !text.startsWith("Bei uns", ignoreCase = true) &&
            !text.startsWith("Weitere erstklassige", ignoreCase = true)
    }

    private fun parseSeriesDetails(markup: String, series: Series): Series {
        val doc = Jsoup.parse(markup, series.url)
        val title = firstCleanText(
            doc.selectFirst("h1")?.text(),
            doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" | AniWorld")
                ?.substringBefore(" - AniWorld"),
            series.title
        )
        val coverImages = doc.select(
            ".seriesCoverBox img, .seriesCover img, .cover img, [class*=cover] img, " +
                "[class*=poster] img, img[itemprop=image], img[alt*=Cover], " +
                "img[src*='/cover/'], img[data-src*='/cover/']"
        )
        val coverCandidates = buildList {
            coverImages.forEach { image ->
                add(image.attr("data-src"))
                add(image.attr("data-lazy-src"))
                add(image.attr("data-original"))
                add(image.attr("data-url"))
                add(image.attr("srcset").substringBefore(' '))
                add(image.attr("src"))
            }
            add(doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty())
            add(doc.selectFirst("meta[name=twitter:image]")?.attr("content").orEmpty())
        }
        val cover = coverCandidates.asSequence()
            .filterNotNull()
            .mapNotNull { normalizeAnimeImageUrl(it, series.url) }
            .firstOrNull()
            .orEmpty()
            .ifBlank { series.coverUrl }
        val description = listOf(
            doc.selectFirst("[itemprop=description], .seriesDescription, .seri_des, .series-description, .description")?.text(),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst("meta[name=description]")?.attr("content")
        ).firstOrNull { !it.isNullOrBlank() }
            ?.let(::cleanDescription)
            .orEmpty()
            .ifBlank { series.description }
        val genres = doc.select("a[href^=/genre/], a[href*=/genre/], [itemprop=genre]")
            .eachText().map(::clean).filter(::isDisplayGenre).distinct().take(12)
            .ifEmpty { series.genres.filter(::isDisplayGenre) }
        val pageText = doc.text()
        val yearRange = Regex("""\((\d{4})(?:\s*-\s*(\d{4}|Heute))?\)""", RegexOption.IGNORE_CASE)
            .find(pageText)
            ?.let { match ->
                val start = match.groupValues.getOrNull(1).orEmpty()
                val end = match.groupValues.getOrNull(2).orEmpty()
                if (end.isBlank()) start else "$start – $end"
            }
        val year = firstCleanText(
            yearRange,
            doc.selectFirst("a[href^=/produktionsjahr/], a[href*=/produktionsjahr/]")?.text(),
            series.year
        )
        val ageRating = firstCleanText(
            doc.selectFirst(".fsk, [class*=fsk], [itemprop=contentRating]")?.text(),
            Regex("""\bAb:\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(pageText)?.groupValues?.getOrNull(1)?.let { "FSK $it" },
            series.ageRating
        )
        val directors = detailValues(doc, "Regisseure", "Regisseur", "Regie")
        val producers = detailValues(doc, "Produzenten", "Produzent")
        val actors = detailValues(doc, "Schauspieler", "Darsteller")
        val countries = detailValues(doc, "Land", "Länder")
        val imdbUrl = doc.selectFirst("a[href*=imdb.com]")?.absUrl("href")
            ?.ifBlank { doc.selectFirst("a[href*=imdb.com]")?.attr("href") }
            .orEmpty()
        val ratingMatch = Regex(
            """([0-5](?:[\.,]\d+)?)\s*/\s*5\s*von\s*(\d+)\s*Bewertungen""",
            RegexOption.IGNORE_CASE
        ).find(pageText)
        val userRating = ratingMatch?.groupValues?.getOrNull(1)?.replace(',', '.').orEmpty()
        val ratingCount = ratingMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        return series.copy(
            title = title,
            coverUrl = cover,
            description = description,
            genres = genres,
            year = year,
            ageRating = ageRating,
            directors = directors.ifEmpty { series.directors },
            producers = producers.ifEmpty { series.producers },
            actors = actors.ifEmpty { series.actors },
            countries = countries.ifEmpty { series.countries },
            imdbUrl = imdbUrl.ifBlank { series.imdbUrl },
            userRating = userRating.ifBlank { series.userRating },
            ratingCount = ratingCount.takeIf { it > 0 } ?: series.ratingCount
        )
    }

    private fun detailValues(doc: Document, vararg labels: String): List<String> {
        val normalizedLabels = labels.map { normalizeHeading(it).removeSuffix(":") }.toSet()
        val labelNode = doc.allElements.firstOrNull { element ->
            normalizeHeading(element.ownText()).removeSuffix(":") in normalizedLabels
        } ?: return emptyList()
        val container = labelNode.closest("li, tr, dd, div") ?: labelNode.parent() ?: return emptyList()
        return container.select("a[href]")
            .eachText()
            .map(::clean)
            .filter { value ->
                value.isNotBlank() &&
                    normalizeHeading(value).removeSuffix(":") !in normalizedLabels &&
                    !value.startsWith("&") &&
                    !value.contains("weitere", ignoreCase = true)
            }
            .distinct()
            .take(40)
    }

    private fun cleanDescription(value: String): String = clean(value)
        .replace(Regex("""(?:…|\.\.\.)?\s*mehr anzeigen\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '…')

    private fun firstCleanText(vararg values: String?): String = values
        .asSequence()
        .mapNotNull { it?.let(::clean) }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    private fun normalizeEpisodeTitle(value: String, season: Int, number: Int): String {
        if (value.isBlank()) return ""
        val generic = if (season == 0) "Film $number" else "Folge $number"
        return value
            .replace(Regex("^(?:S\\d{1,2}E\\d{1,3}|Episode\\s*\\d+|Folge\\s*\\d+|Film\\s*\\d+)\\s*[-:–—]?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { generic }
    }

    private fun parseHosters(markup: String): List<Hoster> {
        val doc = Jsoup.parse(markup)
        val langMap = parseLanguageMap(markup)
        val hosters = mutableListOf<Hoster>()
        doc.select("li[data-link-target][data-lang-key]").forEach { li ->
            val target = li.attr("data-link-target")
            if (!target.startsWith("/redirect/")) return@forEach
            val langKey = li.attr("data-lang-key").toIntOrNull() ?: return@forEach
            val name = li.selectFirst("h4")?.text()?.trim().orEmpty()
            if (name.isBlank()) return@forEach
            hosters += Hoster(
                name = name,
                langKey = langKey,
                lang = langMap[langKey] ?: Language.UNKNOWN,
                redirectUrl = baseUrl + target
            )
        }
        return hosters
    }

    private fun parseLanguageMap(markup: String): Map<Int, Language> {
        val doc = Jsoup.parse(markup)
        val result = mutableMapOf<Int, Language>()
        doc.select(".changeLanguageBox img[data-lang-key]").forEach { img ->
            val key = img.attr("data-lang-key").toIntOrNull() ?: return@forEach
            result[key] = classifyLanguage(img.attr("src"), img.attr("alt"), img.attr("title"))
        }
        return result
    }

    private fun classifyLanguage(vararg texts: String): Language {
        val blob = texts.joinToString(" ").lowercase()
        return when {
            "japanese-german" in blob || "untertitel deutsch" in blob || "ger-sub" in blob -> Language.GER_SUB
            "japanese-english" in blob || "untertitel englisch" in blob || "english" in blob || "englisch" in blob -> Language.ENG_SUB
            "german" in blob || "deutsch" in blob -> Language.GER_DUB
            else -> Language.UNKNOWN
        }
    }

    fun availableLanguages(hosters: List<Hoster>, languagePriority: List<Language>): List<Language> {
        val priority = languagePriority.filter { it != Language.UNKNOWN }
        val unique = hosters.map { it.lang }.filter { it != Language.UNKNOWN }.distinct()
        return unique.sortedBy { lang ->
            val idx = priority.indexOf(lang)
            if (idx >= 0) idx else priority.size
        }
    }

    private fun orderHosters(
        hosters: List<Hoster>,
        languagePriority: List<Language>,
        hosterPriority: List<String>,
        languageOverride: Language? = null
    ): List<Hoster> {
        val filtered = languageOverride?.let { lang -> hosters.filter { it.lang == lang } } ?: hosters.filter { it.lang != Language.UNKNOWN || languagePriority.contains(Language.UNKNOWN) }
        val normalizedHosters = hosterPriority.map { HosterCatalog.normalize(it) }
        fun langRank(h: Hoster): Int {
            val idx = languagePriority.indexOf(h.lang)
            return if (idx >= 0) idx else languagePriority.size
        }
        fun hostRank(h: Hoster): Int {
            val idx = normalizedHosters.indexOf(HosterCatalog.normalize(h.name))
            return if (idx >= 0) idx else normalizedHosters.size
        }
        return filtered.sortedWith(compareBy({ langRank(it) }, { hostRank(it) }, { it.name }))
    }

    private fun seasonPath(slug: String, season: Int): String =
        if (season == 0) "/anime/stream/$slug/filme" else "/anime/stream/$slug/staffel-$season"

    private fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        forceRefresh: Boolean = false
    ): String {
        val requestLabel = runCatching { URI(url).let { "${it.host}${it.path}" } }.getOrDefault("Web-Anfrage")
        val cacheable = isCacheable(url, headers)
        val cached = if (cacheable) cache?.get(url) else null
        val ttl = cacheTtlMs(url)
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.updatedAt <= ttl) {
            AppLogger.info("Cache", "Treffer für $requestLabel")
            return cached.body
        }

        var lastError: Exception? = null
        repeat(3) { attempt ->
            AppLogger.info("Netzwerk", "GET $requestLabel", "Versuch ${attempt + 1}/3")
            val req = baseRequest(url, headers).get().build()
            try {
                client.newCall(req).execute().use { response ->
                    val body = response.body.string()
                    ChallengeDetector.throwIfRequired(
                        context = context,
                        url = response.request.url.toString(),
                        statusCode = response.code,
                        contentType = response.header("Content-Type"),
                        body = body
                    )
                    if (!response.isSuccessful) error("HTTP ${response.code} für $requestLabel")
                    if (cacheable && body.isNotBlank()) {
                        cache?.put(url, body, response.header("Content-Type").orEmpty())
                    }
                    return body
                }
            } catch (error: ChallengeRequiredException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                AppLogger.warn("Netzwerk", "GET $requestLabel fehlgeschlagen", error.message.orEmpty())
                if (attempt < 2) Thread.sleep(250L * (1L shl attempt))
            }
        }

        if (cached != null) {
            AppLogger.warn("Cache", "Veraltete Kopie für $requestLabel wird offline verwendet")
            return cached.body
        }
        throw lastError ?: IllegalStateException("GET $requestLabel fehlgeschlagen")
    }

    private fun isCacheable(url: String, headers: Map<String, String>): Boolean {
        if (headers.isNotEmpty()) return false
        return runCatching {
            val target = URI(url)
            val base = URI(baseUrl)
            target.host.equals(base.host, ignoreCase = true) &&
                target.path.orEmpty().startsWith("/katalog/", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun cacheTtlMs(url: String): Long = runCatching { URI(url).path.orEmpty() }.getOrDefault("").let { path ->
        when {
            path == "/" || path.isBlank() -> 10L * 60L * 1_000L
            path == "/animes" -> 6L * 60L * 60L * 1_000L
            "/staffel-" in path || path.endsWith("/filme") -> 20L * 60L * 1_000L
            else -> 60L * 60L * 1_000L
        }
    }

    private fun baseRequest(url: String, headers: Map<String, String> = emptyMap()): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder
    }

    private fun streamIsLive(stream: StreamSource): Boolean {
        return try {
            var target = stream.url
            val headers = stream.headers
            if (target.contains(".m3u8", ignoreCase = true)) {
                val master = getText(target, headers)
                val first = firstPlaylistEntry(master, target) ?: return true
                target = if (first.contains(".m3u8", ignoreCase = true)) {
                    val variant = getText(first, headers)
                    firstPlaylistEntry(variant, first) ?: return true
                } else {
                    first
                }
            }
            val req = baseRequest(target, headers)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(req).execute().use { r ->
                val body = r.peekBody(256_000).string()
                ChallengeDetector.throwIfRequired(
                    context = context,
                    url = r.request.url.toString(),
                    statusCode = r.code,
                    contentType = r.header("Content-Type"),
                    body = body
                )
                r.code == 200 || r.code == 206
            }
        } catch (e: ChallengeRequiredException) {
            throw e
        } catch (error: Exception) {
            AppLogger.warn("Stream-Prüfung", "Stream-Liveness konnte nicht bestätigt werden", error.message.orEmpty())
            false
        }
    }

    private fun firstPlaylistEntry(text: String, base: String): String? {
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.let { URI(base).resolve(it).toString() }
    }

    private fun normalizeUrl(value: String, base: String): String? {
        val cleaned = decodeHtmlEntities(value).trim()
        if (cleaned.isBlank()) return null
        return runCatching { URI(base).resolve(cleaned).toString() }.getOrNull()
    }

    private fun normalizeAnimeImageUrl(value: String, base: String): String? {
        val normalized = normalizeUrl(value, base) ?: return null
        val lower = normalized.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        if (lower.startsWith("data:")) return null
        val blockedTokens = listOf(
            "aniworld_logo", "aniworld-logo", "/logo.", "/logos/", "favicon", "apple-touch-icon",
            "placeholder", "loading", "spinner", "avatar", "profile", "facebook", "twitter",
            "instagram", "discord", "yandex", "tracking", "pixel.gif", "blank.gif", "transparent",
            "/branding/", "/header/", "default-avatar", "no-image", "no_image"
        )
        if (blockedTokens.any(lower::contains)) return null
        val path = runCatching { URI(normalized).path.orEmpty().lowercase() }.getOrDefault(lower)
        val fileName = path.substringAfterLast('/')
        if (fileName.startsWith("logo") || fileName.startsWith("header") || fileName.startsWith("brand")) return null
        val extensionLooksLikeImage = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif").any { token ->
            path.endsWith(token) || path.contains("$token/")
        }
        val coverPath = path.contains("cover") || path.contains("poster") || path.contains("series") || path.contains("anime")
        return normalized.takeIf { extensionLooksLikeImage || coverPath }
    }

    private fun clean(v: String): String = Jsoup.parse(v).text().trim()

    private object VidmolyExtractor {
        private val sourcesRe = Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']""", RegexOption.DOT_MATCHES_ALL)

        fun extract(context: Context, embedUrl: String, hoster: Hoster, client: OkHttpClient): StreamSource? {
            return try {
                val resp = get(context, client, embedUrl, mapOf("Referer" to "https://aniworld.to/"))
                val url = sourcesRe.find(resp.body)?.groupValues?.get(1)?.let(::decodeHtmlEntities) ?: return null
                StreamSource(
                    url = url,
                    headers = mapOf("Referer" to origin(resp.finalUrl) + "/", "User-Agent" to UA),
                    hoster = hoster.name,
                    language = hoster.lang,
                    mimeType = DirectMediaDetector.mimeTypeFor(url)
                )
            } catch (e: ChallengeRequiredException) {
                throw e
            } catch (error: Exception) {
                AppLogger.warn("Resolver", "Vidmoly-Extractor fehlgeschlagen", error.message.orEmpty())
                null
            }
        }
    }

    private object VoeExtractor {
        private val jsonRe = Regex("""<script[^>]+type=["']application/json["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val redirectRe = Regex("""window\.location\.href\s*=\s*["']([^"']+)["']""")
        private val junkPairs = listOf("@\$", "^^", "~@", "%?", "*~", "!!", "#&")

        fun extract(context: Context, embedUrl: String, hoster: Hoster, client: OkHttpClient): StreamSource? {
            return try {
                var resp = get(context, client, embedUrl)
                repeat(3) {
                    val hop = redirectRe.find(resp.body)?.groupValues?.get(1) ?: return@repeat
                    resp = get(context, client, hop, mapOf("Referer" to origin(resp.finalUrl)))
                }
                val url = parseEmbed(resp.body) ?: return null
                StreamSource(
                    url = url,
                    headers = mapOf("Referer" to origin(resp.finalUrl), "User-Agent" to UA),
                    hoster = hoster.name,
                    language = hoster.lang,
                    mimeType = DirectMediaDetector.mimeTypeFor(url)
                )
            } catch (e: ChallengeRequiredException) {
                throw e
            } catch (error: Exception) {
                AppLogger.warn("Resolver", "VOE-Extractor fehlgeschlagen", error.message.orEmpty())
                null
            }
        }

        private fun parseEmbed(markup: String): String? {
            val blob = jsonRe.find(markup)?.groupValues?.get(1)?.trim() ?: return null
            val obj = decodeBlob(blob) ?: return null
            return obj.optString("source").takeIf { it.isNotBlank() }
                ?: obj.optString("direct_access_url").takeIf { it.isNotBlank() }
        }

        private fun decodeBlob(blob: String): JSONObject? {
            return try {
                val outerValue = JSONArray(blob).optString(0)
                var s = rot13(outerValue)
                junkPairs.forEach { s = s.replace(it, "") }
                s = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
                s = s.map { (it.code - 3).toChar() }.joinToString("").reversed()
                s = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
                JSONObject(s)
            } catch (_: Exception) {
                null
            }
        }

        private fun rot13(input: String): String = buildString(input.length) {
            input.forEach { c ->
                append(
                    when (c) {
                        in 'a'..'z' -> (((c - 'a' + 13) % 26) + 'a'.code).toChar()
                        in 'A'..'Z' -> (((c - 'A' + 13) % 26) + 'A'.code).toChar()
                        else -> c
                    }
                )
            }
        }
    }

    private object PublicMediaExtractor {
        private val mediaRe = Regex(
            """(?:file|src|source|video)\s*[:=]\s*[\"']([^\"']+\.(?:m3u8|mp4)(?:\?[^\"']*)?)[\"']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        /**
         * Uses only media URLs that are directly present in the returned HTML/JSON.
         * This intentionally does not solve challenges, decrypt obfuscated scripts,
         * execute JavaScript, or reconstruct protected tokens.
         */
        fun extract(context: Context, embedUrl: String, hoster: Hoster, client: OkHttpClient): StreamSource? {
            return try {
                val resp = get(context, client, embedUrl, mapOf("Referer" to "https://aniworld.to/"))
                val url = firstDeclaredMediaUrl(resp.body, resp.finalUrl) ?: return null
                StreamSource(
                    url = url,
                    headers = mapOf("Referer" to origin(resp.finalUrl), "User-Agent" to UA),
                    hoster = HosterCatalog.displayName(hoster.name),
                    language = hoster.lang,
                    mimeType = DirectMediaDetector.mimeTypeFor(url)
                )
            } catch (e: ChallengeRequiredException) {
                throw e
            } catch (error: Exception) {
                AppLogger.warn("Resolver", "Direkt-URL-Extractor für ${HosterCatalog.displayName(hoster.name)} fehlgeschlagen", error.message.orEmpty())
                null
            }
        }

        private fun firstDeclaredMediaUrl(markup: String, finalUrl: String): String? {
            val doc = Jsoup.parse(markup, finalUrl)
            val selectors = listOf(
                "video[src]",
                "source[src]",
                "meta[property=og:video][content]",
                "meta[property=og:video:url][content]",
                "meta[name=twitter:player:stream][content]"
            )
            selectors.forEach { selector ->
                doc.select(selector).forEach { element ->
                    val raw = element.attr("src").ifBlank { element.attr("content") }
                    val normalized = normalizeMediaUrl(raw, finalUrl)
                    if (normalized != null) return normalized
                }
            }
            return mediaRe.findAll(markup)
                .mapNotNull { normalizeMediaUrl(decodeHtmlEntities(it.groupValues[1]), finalUrl) }
                .firstOrNull()
        }

        private fun normalizeMediaUrl(value: String, base: String): String? {
            val cleaned = decodeHtmlEntities(value).trim()
            if (cleaned.isBlank()) return null
            if (!cleaned.contains(".m3u8", ignoreCase = true) && !cleaned.contains(".mp4", ignoreCase = true)) return null
            return URI(base).resolve(cleaned).toString()
        }
    }

    private object GenericMarkupExtractor {
        fun extract(context: Context, embedUrl: String, hoster: Hoster, client: OkHttpClient): StreamSource? =
            PublicMediaExtractor.extract(context, embedUrl, hoster, client)
    }

    private data class FetchResult(val body: String, val finalUrl: String)

    companion object {
        private val CATALOG_KEYS = listOf("0-9") + ('A'..'Z').map(Char::toString)
        private const val METADATA_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val CATALOG_FETCH_CONCURRENCY = 4
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"

        private fun get(context: Context, client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): FetchResult {
            val builder = Request.Builder().url(url).header("User-Agent", UA)
            headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.newCall(builder.get().build()).execute()
            response.use { r ->
                val body = r.body.string()
                ChallengeDetector.throwIfRequired(
                    context = context,
                    url = r.request.url.toString(),
                    statusCode = r.code,
                    contentType = r.header("Content-Type"),
                    body = body
                )
                if (!r.isSuccessful) return FetchResult("", r.request.url.toString())
                return FetchResult(body, r.request.url.toString())
            }
        }

        private fun origin(url: String): String {
            val uri = URI(url)
            return "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
        }

        private fun decodeHtmlEntities(value: String): String = value
            .replace("&amp;", "&")
            .replace("\\/", "/")
    }
}
