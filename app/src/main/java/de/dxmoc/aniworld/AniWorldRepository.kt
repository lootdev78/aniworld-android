package de.dxmoc.aniworld

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
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
                    coverUrl = normalizeUrl(
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
        val feed = parseHomeFeed(getText("$baseUrl/", forceRefresh = forceRefresh))
        val hero = feed.featured
        if (hero == null || (hero.coverUrl.isNotBlank() && hero.description.isNotBlank())) {
            feed
        } else {
            val detailed = runCatching { parseSeriesDetails(getText(hero.url), hero) }.getOrDefault(hero)
            feed.copy(
                featured = detailed,
                popularAtAniWorld = feed.popularAtAniWorld.map { if (it.slug == detailed.slug) detailed else it },
                communityWatching = feed.communityWatching.map { if (it.slug == detailed.slug) detailed else it },
                currentlyPopular = feed.currentlyPopular.map { if (it.slug == detailed.slug) detailed else it },
                newAnimes = feed.newAnimes.map { if (it.slug == detailed.slug) detailed else it }
            )
        }
    }

    suspend fun catalog(forceRefresh: Boolean = false): CatalogData = withContext(Dispatchers.IO) {
        val markup = getText("$baseUrl/animes", forceRefresh = forceRefresh)
        parseCatalog(markup)
    }

    suspend fun enrichSeries(series: Series): Series = withContext(Dispatchers.IO) {
        if (series.coverUrl.isNotBlank() && series.description.isNotBlank()) series
        else parseSeriesDetails(getText(series.url), series)
    }

    suspend fun seriesDetails(series: Series): Series = withContext(Dispatchers.IO) {
        val markup = getText(series.url)
        parseSeriesDetails(markup, series)
    }

    suspend fun seasons(series: Series): List<Int> = withContext(Dispatchers.IO) {
        parseSeasons(getText(series.url), series.slug)
    }

    suspend fun episodes(series: Series, season: Int): List<Episode> = withContext(Dispatchers.IO) {
        val path = seasonPath(series.slug, season)
        parseEpisodes(getText(baseUrl + path), series, season)
    }

    suspend fun listHosters(episode: Episode): List<Hoster> = withContext(Dispatchers.IO) {
        parseHosters(getText(episode.url))
    }

    suspend fun resolveEpisode(
        episode: Episode,
        languagePriority: List<Language>,
        hosterPriority: List<String>,
        verifyStreams: Boolean = true,
        languageOverride: Language? = null
    ): ResolveResult = withContext(Dispatchers.IO) {
        val markup = getText(episode.url)
        val hosters = parseHosters(markup)
        val ordered = orderHosters(hosters, languagePriority, hosterPriority, languageOverride)
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

    private fun parseCatalog(markup: String): CatalogData {
        val doc = Jsoup.parse(markup, "$baseUrl/animes")
        val bySlug = linkedMapOf<String, Series>()
        val genres = linkedSetOf<String>()
        doc.select("h3").forEach { heading ->
            val genre = clean(heading.text())
            if (genre.isBlank() || genre.length > 40) return@forEach
            val sectionGenres = mutableListOf<Element>()
            var sibling = heading.nextElementSibling()
            while (sibling != null && sibling.tagName() != "h3") {
                sectionGenres += sibling
                sibling = sibling.nextElementSibling()
            }
            val anchors = sectionGenres.flatMap { it.select("a[href]") }
            var accepted = 0
            anchors.forEach { anchor ->
                val series = seriesFromAnchor(anchor) ?: return@forEach
                val existing = bySlug[series.slug]
                bySlug[series.slug] = if (existing == null) {
                    series.copy(genres = (series.genres + genre).filter(String::isNotBlank).distinct())
                } else {
                    existing.copy(
                        coverUrl = existing.coverUrl.ifBlank { series.coverUrl },
                        description = existing.description.ifBlank { series.description },
                        genres = (existing.genres + series.genres + genre).filter(String::isNotBlank).distinct()
                    )
                }
                accepted++
            }
            if (accepted > 0) genres += genre
        }
        if (bySlug.isEmpty()) {
            doc.select("a[href*=/anime/stream/]").forEach { anchor ->
                seriesFromAnchor(anchor)?.let { bySlug.putIfAbsent(it.slug, it) }
            }
        }
        return CatalogData(
            items = bySlug.values.sortedBy { it.title.lowercase() },
            genres = genres.sortedBy { it.lowercase() },
            loadedAt = System.currentTimeMillis()
        )
    }

    private fun parseHomeFeed(markup: String): HomeFeed {
        val doc = Jsoup.parse(markup, "$baseUrl/")
        val popular = parseSeriesSection(doc, "Beliebt bei AniWorld").take(24)
        val latest = parseLatestEpisodes(doc).take(50)
        val newAnimes = parseSeriesSection(doc, "Neue Animes").take(30)
        val currentlyPopular = parseSeriesSection(doc, "Derzeit beliebt").take(24)
        val community = parseSeriesSection(doc, "Das sehen andere AniWorld Nutzer").take(24)
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
        val episodeRegex = Regex("^/anime/stream/([^/]+)/staffel-(\\d+)/episode-(\\d+)/?$")
        val movieRegex = Regex("^/anime/stream/([^/]+)/filme/film-(\\d+)/?$")
        val dateRegex = Regex("(?:Mo|Di|Mi|Do|Fr|Sa|So),?\\s*\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\s+\\d{1,2}:\\d{2}\\s+Uhr", RegexOption.IGNORE_CASE)
        return sectionAnchors(doc, "Die 50 neuesten Episoden").mapNotNull { anchor ->
            val path = pathOf(anchor) ?: return@mapNotNull null
            val episodeMatch = episodeRegex.matchEntire(path)
            val movieMatch = movieRegex.matchEntire(path)
            if (episodeMatch == null && movieMatch == null) return@mapNotNull null
            val slug = episodeMatch?.groupValues?.get(1) ?: movieMatch!!.groupValues[1]
            val season = episodeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val number = episodeMatch?.groupValues?.get(3)?.toIntOrNull()
                ?: movieMatch?.groupValues?.get(2)?.toIntOrNull()
                ?: return@mapNotNull null
            val container = closestContentContainer(anchor)
            val raw = clean(anchor.text())
            val titleFromText = raw
                .replace(dateRegex, "")
                .replace(Regex("\\bS\\d{1,2}\\s*E\\d{1,3}\\b", RegexOption.IGNORE_CASE), "")
                .trim(' ', '-', '–', '—', '·')
            val title = firstCleanText(
                anchor.selectFirst("h3, h4, [itemprop=name], .series-title, .title")?.text(),
                container?.selectFirst("h3, h4, [itemprop=name], .series-title")?.text(),
                anchor.selectFirst("img[alt]")?.attr("alt"),
                titleFromText
            ).removePrefix("Cover von ").trim()
            val cover = imageUrl(container ?: anchor, anchor.absUrl("href"))
            val description = firstCleanText(
                container?.selectFirst("p, .description, [itemprop=description]")?.text()
            )
            val genres = genreTexts(container ?: anchor, title)
            val series = Series(
                title = title.ifBlank { slug.replace('-', ' ').replaceFirstChar { it.titlecase() } },
                slug = slug,
                url = "$baseUrl/anime/stream/$slug",
                description = description,
                coverUrl = cover,
                genres = genres
            )
            val episodeTitle = firstCleanText(
                anchor.selectFirst(".episodeTitle, .episode-title, .episodeGermanTitle")?.text(),
                container?.selectFirst(".episodeTitle, .episode-title, .episodeGermanTitle")?.text()
            )
            val languages = (container ?: anchor).select("img[alt], img[title]").mapNotNull { img ->
                classifyLanguage(img.attr("src"), img.attr("alt"), img.attr("title")).takeIf { it != Language.UNKNOWN }
            }.distinct()
            HomeEpisode(
                series = series,
                episode = Episode(
                    season = season,
                    number = number,
                    title = normalizeEpisodeTitle(episodeTitle, season, number),
                    url = anchor.absUrl("href").ifBlank { "$baseUrl$path" },
                    seriesSlug = slug,
                    seriesTitle = series.title
                ),
                releasedAt = dateRegex.find((container ?: anchor).text())?.value.orEmpty(),
                languages = languages,
                isNew = (container ?: anchor).text().contains("Neu!", ignoreCase = true)
            )
        }
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
            if (element.tagName() == "a" && element.hasAttr("href")) output += element
        }
        return output.distinctBy { pathOf(it).orEmpty() + "|" + clean(it.text()) }
    }

    private fun seriesFromAnchor(anchor: Element): Series? {
        val path = pathOf(anchor) ?: return null
        val match = Regex("^/anime/stream/([^/]+)/?$").matchEntire(path) ?: return null
        val slug = match.groupValues[1]
        val container = closestContentContainer(anchor)
        val title = firstCleanText(
            anchor.selectFirst("h3, h4, [itemprop=name], .series-title, .title")?.text(),
            container?.selectFirst("h3, h4, [itemprop=name], .series-title")?.text(),
            anchor.selectFirst("img[alt]")?.attr("alt"),
            anchor.attr("title")
        ).removePrefix("Cover von ").trim().ifBlank {
            slug.replace('-', ' ').split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
        }
        val source = container ?: anchor
        return Series(
            title = title,
            slug = slug,
            url = anchor.absUrl("href").ifBlank { "$baseUrl$path" },
            description = firstCleanText(source.selectFirst("p, .description, [itemprop=description]")?.text()),
            coverUrl = imageUrl(source, anchor.absUrl("href").ifBlank { "$baseUrl$path" }),
            genres = genreTexts(source, title)
        )
    }

    private fun closestContentContainer(anchor: Element): Element? =
        anchor.closest("li, article, tr, .seriesListContainer, .seriesList, .latestEpisode, .episode, .col, .card")
            ?: anchor.parent()

    private fun imageUrl(source: Element, base: String): String {
        val img = source.selectFirst("img") ?: return backgroundImageUrl(source, base)
        val candidate = listOf(
            img.attr("data-src"),
            img.attr("data-lazy-src"),
            img.attr("data-original"),
            img.attr("src"),
            img.attr("srcset").substringBefore(' ')
        ).firstOrNull { it.isNotBlank() }
        return candidate?.let { normalizeUrl(it, base) }.orEmpty().ifBlank { backgroundImageUrl(source, base) }
    }

    private fun backgroundImageUrl(source: Element, base: String): String {
        val style = source.attr("style") + " " + source.selectFirst("[style*=background-image]")?.attr("style").orEmpty()
        val raw = Regex("url\\(['\"]?([^'\")]+)").find(style)?.groupValues?.get(1) ?: return ""
        return normalizeUrl(raw, base).orEmpty()
    }

    private fun genreTexts(source: Element, title: String): List<String> {
        val texts = source.select(".genre, .genres, [class*=genre], small, .label, .badge").eachText()
            .flatMap { it.split(',', '·', '|') }
            .map(::clean)
            .filter { value ->
                value.isNotBlank() && !value.equals(title, ignoreCase = true) &&
                    !value.equals("Neu!", ignoreCase = true) && value.length <= 32
            }
            .distinct()
        return texts.take(4)
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
        val doc = Jsoup.parse(markup)
        val out = linkedMapOf<Int, Episode>()
        val re = if (season == 0) {
            Regex("^/anime/stream/${Regex.escape(series.slug)}/filme/film-(\\d+)/?$")
        } else {
            Regex("^/anime/stream/${Regex.escape(series.slug)}/staffel-$season/episode-(\\d+)/?$")
        }
        doc.select("div#stream a[href]").forEach { a ->
            re.matchEntire(a.attr("href"))?.let { m ->
                val n = m.groupValues[1].toInt()
                val container = a.parent() ?: a
                val german = firstCleanText(
                    a.selectFirst(".episodeGermanTitle")?.text(),
                    container.selectFirst(".episodeGermanTitle")?.text(),
                    a.selectFirst(".seasonEpisodeTitle")?.text(),
                    container.selectFirst(".seasonEpisodeTitle")?.text(),
                    a.attr("title"),
                    a.selectFirst("strong")?.text(),
                    a.text()
                )
                val secondary = firstCleanText(
                    a.selectFirst(".episodeEnglishTitle")?.text(),
                    container.selectFirst(".episodeEnglishTitle")?.text(),
                    a.selectFirst("small")?.text(),
                    container.selectFirst("small")?.text()
                ).takeIf { it.isNotBlank() && !it.equals(german, ignoreCase = true) }.orEmpty()
                val fallback = if (season == 0) "Film $n" else "Folge $n"
                out[n] = Episode(
                    season = season,
                    number = n,
                    title = normalizeEpisodeTitle(german, season, n).ifBlank { fallback },
                    secondaryTitle = normalizeEpisodeTitle(secondary, season, n),
                    url = baseUrl + a.attr("href"),
                    seriesSlug = series.slug,
                    seriesTitle = series.title
                )
            }
        }
        return out.values.sortedBy { it.number }
    }

    private fun parseSeriesDetails(markup: String, series: Series): Series {
        val doc = Jsoup.parse(markup, series.url)
        val coverCandidates = listOf(
            doc.selectFirst("meta[property=og:image]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:image]")?.attr("content"),
            doc.selectFirst(".seriesCoverBox img")?.let { it.attr("data-src").ifBlank { it.attr("src") } },
            doc.selectFirst("img[itemprop=image]")?.let { it.attr("data-src").ifBlank { it.attr("src") } },
            doc.selectFirst(".seriesCover img, .cover img")?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        )
        val cover = coverCandidates.firstOrNull { !it.isNullOrBlank() }
            ?.let { normalizeUrl(it, series.url) }
            .orEmpty()
            .ifBlank { series.coverUrl }
        val description = listOf(
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst("[itemprop=description]")?.text(),
            doc.selectFirst(".seriesDescription, .seri_des, .description")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.let(::clean).orEmpty().ifBlank { series.description }
        val genres = doc.select(".genre, .genres a, [class*=genre] a, [itemprop=genre]")
            .eachText().map(::clean).filter { it.isNotBlank() }.distinct().take(8)
            .ifEmpty { series.genres }
        return series.copy(coverUrl = cover, description = description, genres = genres)
    }

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
            target.host.equals(base.host, ignoreCase = true)
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
                    language = hoster.lang
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
                    language = hoster.lang
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
                    language = hoster.lang
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
