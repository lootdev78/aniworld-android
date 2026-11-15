package io.github.lootdev78.aniworld

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/** Anime title resolver used by the native Android port of synacktraa/ani-skip. */
data class AniSkipAnime(
    val malId: Int,
    val title: String,
    val englishTitle: String = "",
    val japaneseTitle: String = "",
    val episodes: Int? = null,
    val imageUrl: String = "",
    val url: String = ""
) {
    val displayTitle: String get() = englishTitle.takeIf(String::isNotBlank) ?: title
}

enum class AniSkipTitleSource(val displayName: String) {
    MYANIMELIST("MyAnimeList"),
    ALLANIME("AllAnime")
}

enum class AniSkipType(val apiValue: String, val label: String) {
    OPENING("op", "Opening"),
    ENDING("ed", "Ending"),
    MIXED_OPENING("mixed-op", "Mixed Opening"),
    MIXED_ENDING("mixed-ed", "Mixed Ending"),
    RECAP("recap", "Recap");

    companion object {
        fun fromApi(value: String): AniSkipType? = entries.firstOrNull { it.apiValue == value }
        fun isOpening(type: AniSkipType) = type == OPENING || type == MIXED_OPENING
        fun isEnding(type: AniSkipType) = type == ENDING || type == MIXED_ENDING
    }
}

data class AniSkipSegment(
    val id: String,
    val type: AniSkipType,
    val startSeconds: Double,
    val endSeconds: Double,
    val episodeLengthSeconds: Double = 0.0
) {
    val valid: Boolean get() = id.isNotBlank() && startSeconds.isFinite() && endSeconds.isFinite() && startSeconds >= 0.0 && endSeconds > startSeconds
}

data class AniSkipAnimeMapping(val malId: Int, val title: String, val url: String = "")
data class AniSkipLoadResult(val mapping: AniSkipAnimeMapping?, val segments: List<AniSkipSegment>, val message: String? = null)

/**
 * Kotlin/Android port of the upstream ani-skip CLI's core flow:
 *  - resolve a title to a MAL id (MAL prefix search or AllAnime -> MAL id),
 *  - fetch community opening/ending ranges from AniSkip,
 *  - apply the same one-time, seek-to-end-minus-offset semantics in the Android player.
 *
 * Android's Media3 player replaces the upstream mpv Lua integration. AniSkip v2 is also
 * queried as a compatibility/extension path for mixed openings/endings and recaps.
 */
class AniSkipAndroidCore(context: Context) {
    private val appContext = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8))
        .readTimeout(Duration.ofSeconds(12))
        .callTimeout(Duration.ofSeconds(18))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val skipCache = ConcurrentHashMap<String, List<AniSkipSegment>>()
    private val animeCache = ConcurrentHashMap<String, List<AniSkipAnime>>()

    suspend fun searchAnime(query: String, source: AniSkipTitleSource = AniSkipTitleSource.MYANIMELIST): List<AniSkipAnime> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            require(clean.length >= 2) { "Bitte mindestens zwei Zeichen eingeben." }
            val cacheKey = "${source.name}:${clean.lowercase(Locale.ROOT)}"
            animeCache[cacheKey]?.let { return@withContext it }
            val resolverQuery = clean.replace(Regex("\\s+\\(\\d+ episodes\\)\\s*$", RegexOption.IGNORE_CASE), "").trim()

            val resolverAttempt = runCatching {
                when {
                    source == AniSkipTitleSource.MYANIMELIST && resolverQuery.matches(Regex("\\d+")) -> {
                        val directId = resolverQuery.toIntOrNull() ?: 0
                        if (directId <= 0) emptyList()
                        else listOf(AniSkipAnime(directId, "MAL ID $directId", url = "https://myanimelist.net/anime/$directId"))
                    }
                    else -> when (source) {
                        AniSkipTitleSource.MYANIMELIST -> searchMyAnimeListPrefix(resolverQuery)
                        AniSkipTitleSource.ALLANIME -> searchAllAnime(resolverQuery)
                    }
                }
            }
            val resolved = resolverAttempt.getOrDefault(emptyList())
            // Jikan-compatible metadata is supplemental only. MAL/AllAnime remains the
            // title resolver, matching the original ani-skip flow and never changing the MAL id.
            val metadataAttempt = runCatching { searchAnimeMetadata(resolverQuery) }
            val metadata = metadataAttempt.getOrDefault(emptyList())
            if (resolved.isEmpty() && metadata.isEmpty() && (!resolverAttempt.isSuccess || !metadataAttempt.isSuccess)) {
                throw IOException(
                    "Anime-Lookup ist nicht erreichbar. Bitte später erneut versuchen oder einen anderen Suchanbieter wählen.",
                    metadataAttempt.exceptionOrNull() ?: resolverAttempt.exceptionOrNull()
                )
            }
            val metadataById = metadata.associateBy(AniSkipAnime::malId)
            val merged = resolved.map { found ->
                val details = metadataById[found.malId] ?: return@map found
                found.copy(
                    englishTitle = found.englishTitle.ifBlank { details.englishTitle },
                    japaneseTitle = found.japaneseTitle.ifBlank { details.japaneseTitle },
                    episodes = found.episodes ?: details.episodes,
                    imageUrl = found.imageUrl.ifBlank { details.imageUrl },
                    url = found.url.ifBlank { details.url }
                )
            }.distinctBy(AniSkipAnime::malId)
            val result = if (merged.isNotEmpty()) merged else metadata.distinctBy(AniSkipAnime::malId)
            animeCache[cacheKey] = result
            result
        }

    suspend fun findExactTitleMatch(title: String): AniSkipAnime? {
        val clean = title.trim().takeIf(String::isNotBlank) ?: return null
        val failures = mutableListOf<Throwable>()
        var anyProviderResponded = false
        val candidates = AniSkipTitleSource.entries.flatMap { source ->
            try {
                searchAnime(clean, source).also { anyProviderResponded = true }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
                emptyList()
            }
        }
        if (!anyProviderResponded) {
            throw IOException("Anime-Lookup ist momentan nicht erreichbar.", failures.firstOrNull())
        }
        val exactMatches = candidates.filter { anime ->
            sequenceOf(anime.title, anime.englishTitle, anime.japaneseTitle)
                .filter(String::isNotBlank)
                .any { aniWorldTitleEquals(clean, it) }
        }.distinctBy(AniSkipAnime::malId)
        return exactMatches.singleOrNull()
    }

    suspend fun getSkipTimes(malId: Int, episodeNumber: Int, episodeLengthSeconds: Double): List<AniSkipSegment> =
        withContext(Dispatchers.IO) {
            require(malId > 0) { "Ungültige MyAnimeList-ID." }
            require(episodeNumber > 0) { "Ungültige Episodennummer." }
            val length = episodeLengthSeconds.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            val cacheKey = "$malId:$episodeNumber:${length.toInt()}"
            skipCache[cacheKey]?.let { return@withContext it }

            var v1Succeeded = false
            var v1Failure: Exception? = null
            val v1Segments = try {
                val url = "$ANISKIP_V1/skip-times/$malId/$episodeNumber".toHttpUrl().newBuilder()
                    .addQueryParameter("types", "op")
                    .addQueryParameter("types", "ed")
                    .build()
                val root = getJson(url.toString())
                v1Succeeded = true
                parseV1SkipTimes(root, length)
            } catch (error: Exception) {
                if (error.message?.contains("HTTP 404") == true) v1Succeeded = true
                else v1Failure = error
                emptyList()
            }

            // v2 is the supported modern API and supplies mixed/recap ranges; use it to
            // supplement the original ani-skip v1 OP/ED response and as a fallback.
            var v2Succeeded = false
            var v2Failure: Exception? = null
            val v2Segments = try {
                val builder = "$ANISKIP_V2/skip-times/$malId/$episodeNumber".toHttpUrl().newBuilder()
                listOf("op", "ed", "mixed-op", "mixed-ed", "recap").forEach { builder.addQueryParameter("types", it) }
                if (length > 0.0) builder.addQueryParameter("episodeLength", String.format(Locale.US, "%.3f", length))
                val root = getJson(builder.build().toString())
                v2Succeeded = true
                parseV2SkipTimes(root, length)
            } catch (error: Exception) {
                if (error.message?.contains("HTTP 404") == true) v2Succeeded = true
                else v2Failure = error
                emptyList()
            }

            // Prefer v2 IDs (needed for community voting) when both APIs report the same range.
            val combined = (v2Segments + v1Segments)
                .distinctBy { "${it.type.apiValue}:${(it.startSeconds * 1000).toLong()}:${(it.endSeconds * 1000).toLong()}" }
                .sortedBy(AniSkipSegment::startSeconds)
            if (!v1Succeeded && !v2Succeeded) {
                throw IOException(
                    "AniSkip ist momentan nicht erreichbar. Bitte Verbindung prüfen und erneut versuchen.",
                    v2Failure ?: v1Failure
                )
            }
            skipCache[cacheKey] = combined
            combined
        }

    fun rememberSubmittedSkipTime(malId: Int, episodeNumber: Int, episodeLengthSeconds: Double, segment: AniSkipSegment) {
        val prefix = "$malId:$episodeNumber:"
        val keys = skipCache.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) {
            skipCache["$prefix${episodeLengthSeconds.toInt()}"] = listOf(segment)
        } else {
            keys.forEach { key ->
                skipCache.computeIfPresent(key) { _, existing ->
                    (existing + segment).distinctBy { "${it.type.apiValue}:${(it.startSeconds * 1000).toLong()}:${(it.endSeconds * 1000).toLong()}" }
                        .sortedBy(AniSkipSegment::startSeconds)
                }
            }
        }
    }

    suspend fun submitSkipTime(
        malId: Int,
        episodeNumber: Int,
        type: AniSkipType,
        startSeconds: Double,
        endSeconds: Double,
        episodeLengthSeconds: Double,
        providerName: String
    ): JSONObject = withContext(Dispatchers.IO) {
        require(malId > 0 && episodeNumber > 0) { "Anime-Zuordnung fehlt." }
        require(type == AniSkipType.OPENING || type == AniSkipType.ENDING) { "AniSkip-Community-Einträge unterstützen nur Opening und Ending." }
        require(startSeconds.isFinite() && endSeconds.isFinite() && startSeconds >= 0.0 && endSeconds > startSeconds) {
            "Endzeit muss nach der Startzeit liegen."
        }
        require(episodeLengthSeconds.isFinite() && episodeLengthSeconds > endSeconds) { "Die Endzeit muss innerhalb der Folgenlänge liegen." }
        val preferences = appContext.getSharedPreferences("aniskip_identity", Context.MODE_PRIVATE)
        val submitterId = preferences.getString("submitter_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("submitter_id", it).apply()
        }
        val body = JSONObject()
            .put("skipType", type.apiValue)
            .put("providerName", providerName.ifBlank { "aniworld-android" })
            .put("startTime", startSeconds)
            .put("endTime", endSeconds)
            .put("episodeLength", episodeLengthSeconds)
            .put("submitterId", submitterId)
        val request = Request.Builder().url("$ANISKIP_V2/skip-times/$malId/$episodeNumber")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()
        executeJson(request)
    }

    suspend fun vote(skipId: String, upvote: Boolean): JSONObject = withContext(Dispatchers.IO) {
        require(skipId.isNotBlank()) { "Ungültige Skip-ID." }
        val body = JSONObject().put("voteType", if (upvote) "upvote" else "downvote")
        val request = Request.Builder().url("$ANISKIP_V2/skip-times/vote/$skipId")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()
        executeJson(request)
    }

    private fun searchMyAnimeListPrefix(query: String): List<AniSkipAnime> {
        val url = "https://myanimelist.net/search/prefix.json".toHttpUrl().newBuilder()
            .addQueryParameter("type", "anime")
            .addQueryParameter("keyword", query)
            .build()
        val request = Request.Builder().url(url).get().header("User-Agent", MAL_USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("MyAnimeList HTTP ${response.code}: ${extractMessage(raw)}")
            val array = JSONArray(raw)
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (item.optString("type").isNotBlank() && !item.optString("type").equals("anime", true)) continue
                    val id = item.optInt("id", 0)
                    val title = item.optString("name").trim()
                    if (id > 0 && title.isNotBlank()) {
                        add(AniSkipAnime(malId = id, title = title, url = item.optString("url").ifBlank { "https://myanimelist.net/anime/$id" }))
                    }
                }
            }.distinctBy(AniSkipAnime::malId)
        }
    }

    private fun searchAllAnime(query: String): List<AniSkipAnime> {
        val graphQuery = "query(\$search:SearchInput \$limit:Int \$page:Int \$translationType:VaildTranslationTypeEnumType \$countryOrigin:VaildCountryOriginEnumType){shows(search:\$search limit:\$limit page:\$page translationType:\$translationType countryOrigin:\$countryOrigin){edges{_id name}}}"
        val variables = JSONObject()
            .put("search", JSONObject().put("query", query))
            .put("limit", 10)
            .put("page", 1)
            .put("translationType", "sub")
            .put("countryOrigin", "ALL")
        val root = postGraphQl(graphQuery, variables)
        val edges = root.optJSONObject("data")?.optJSONObject("shows")?.optJSONArray("edges") ?: JSONArray()
        val output = mutableListOf<AniSkipAnime>()
        for (index in 0 until edges.length()) {
            val edge = edges.optJSONObject(index) ?: continue
            val id = edge.optString("_id").trim()
            val title = edge.optString("name").trim()
            if (id.isBlank() || title.isBlank()) continue
            val malId = runCatching { resolveAllAnimeMalId(id) }.getOrNull() ?: continue
            if (malId > 0) output += AniSkipAnime(malId, title, url = "https://myanimelist.net/anime/$malId")
        }
        return output.distinctBy(AniSkipAnime::malId)
    }

    private fun resolveAllAnimeMalId(allAnimeId: String): Int {
        val escapedId = JSONObject.quote(allAnimeId)
        val root = postGraphQl("{ show(_id: $escapedId) { malId } }")
        val show = root.optJSONObject("data")?.optJSONObject("show") ?: return 0
        return show.optInt("malId", show.optString("malId").toIntOrNull() ?: 0)
    }

    private fun postGraphQl(query: String, variables: JSONObject? = null): JSONObject {
        val body = JSONObject().put("query", query).apply { if (variables != null) put("variables", variables) }
        val request = Request.Builder().url(ALLANIME_API)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", MAL_USER_AGENT)
            .build()
        return executeJson(request)
    }

    private fun searchAnimeMetadata(query: String): List<AniSkipAnime> {
        var lastError: Exception? = null
        var successfulResponse = false
        for (base in ANIME_METADATA_BASES) {
            try {
                val url = "$base/anime".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("limit", "10")
                    .addQueryParameter("sfw", "true")
                    .build()
                val root = getJson(url.toString())
                successfulResponse = true
                val data = root.optJSONArray("data") ?: JSONArray()
                val result = buildList {
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        parseAnimeMetadata(item)?.let(::add)
                    }
                }.distinctBy(AniSkipAnime::malId)
                if (result.isNotEmpty()) return result
            } catch (error: Exception) {
                lastError = error
            }
        }
        if (!successfulResponse && lastError != null) throw IOException("Anime-Metadaten sind momentan nicht erreichbar.", lastError)
        return emptyList()
    }

    private fun parseAnimeMetadata(item: JSONObject): AniSkipAnime? {
        val id = item.optInt("mal_id", 0)
        val title = item.optString("title").trim()
        if (id <= 0 || title.isBlank()) return null
        val titles = item.optJSONArray("titles") ?: JSONArray()
        var english = item.optString("title_english").takeUnless { it == "null" }.orEmpty()
        var japanese = item.optString("title_japanese").takeUnless { it == "null" }.orEmpty()
        for (index in 0 until titles.length()) {
            val entry = titles.optJSONObject(index) ?: continue
            when (entry.optString("type").lowercase(Locale.ROOT)) {
                "english" -> if (english.isBlank()) english = entry.optString("title")
                "japanese" -> if (japanese.isBlank()) japanese = entry.optString("title")
            }
        }
        val image = item.optJSONObject("images")?.optJSONObject("jpg")?.optString("image_url").orEmpty()
        return AniSkipAnime(
            malId = id,
            title = title,
            englishTitle = english.takeUnless { it == "null" }.orEmpty(),
            japaneseTitle = japanese.takeUnless { it == "null" }.orEmpty(),
            episodes = item.optInt("episodes", 0).takeIf { it > 0 },
            imageUrl = image,
            url = item.optString("url").orEmpty().ifBlank { "https://myanimelist.net/anime/$id" }
        )
    }

    private fun parseV1SkipTimes(root: JSONObject, duration: Double): List<AniSkipSegment> {
        if (!root.optBoolean("found", false)) return emptyList()
        val results = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val type = AniSkipType.fromApi(item.optString("skip_type", item.optString("skipType"))) ?: continue
                // Upstream ani-skip v1 uses snake_case; tolerate the current camelCase schema too.
                val interval = item.optJSONObject("interval") ?: item
                val start = interval.optDouble("start_time", interval.optDouble("startTime", -1.0))
                val end = interval.optDouble("end_time", interval.optDouble("endTime", -1.0))
                val id = item.optString("skip_id").ifBlank { item.optString("skipId") }
                    .ifBlank { "v1:${type.apiValue}:$start:$end" }
                val segment = AniSkipSegment(id, type, start, end, duration)
                if (segment.valid && (duration <= 0.0 || segment.startSeconds < duration + 2.0)) add(segment)
            }
        }.sortedBy(AniSkipSegment::startSeconds)
    }

    private fun parseV2SkipTimes(root: JSONObject, duration: Double): List<AniSkipSegment> {
        val results = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val interval = item.optJSONObject("interval") ?: continue
                val type = AniSkipType.fromApi(item.optString("skipType")) ?: continue
                val reportedLength = item.optDouble("episodeLength", duration).takeIf { it.isFinite() && it > 0.0 } ?: duration
                val scale = if (duration > 0.0 && reportedLength > 0.0 && abs(duration - reportedLength) > maxOf(5.0, duration * 0.005)) {
                    duration / reportedLength
                } else 1.0
                val start = interval.optDouble("startTime", -1.0) * scale
                val end = interval.optDouble("endTime", -1.0) * scale
                val segment = AniSkipSegment(
                    id = item.optString("skipId").ifBlank { "v2:${type.apiValue}:$start:$end" },
                    type = type,
                    startSeconds = start,
                    endSeconds = end,
                    episodeLengthSeconds = duration.takeIf { it > 0.0 } ?: reportedLength
                )
                if (segment.valid && (duration <= 0.0 || segment.startSeconds < duration + 2.0)) add(segment)
            }
        }.sortedBy(AniSkipSegment::startSeconds)
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .header("User-Agent", APP_USER_AGENT)
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${extractMessage(raw)}")
            if (raw.isBlank()) return JSONObject()
            return try { JSONObject(raw) } catch (error: Exception) {
                throw IOException("Ungültige JSON-Antwort von ${request.url.host}.", error)
            }
        }
    }

    private fun extractMessage(raw: String): String = runCatching { JSONObject(raw).optString("message") }
        .getOrNull().orEmpty().take(180)

    companion object {
        private const val ANISKIP_V1 = "https://api.aniskip.com/v1"
        private const val ANISKIP_V2 = "https://api.aniskip.com/v2"
        private const val ALLANIME_API = "https://api.allanime.day/api"
        private const val APP_USER_AGENT = "LootDev78-AniWorld-Android/${BuildConfig.VERSION_NAME} (Android; AniSkip Kotlin port)"
        private const val MAL_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private val ANIME_METADATA_BASES = listOf("https://api.tenrai.org/v1", "https://api.jikan.moe/v4")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun aniWorldTitleEquals(aniWorldTitle: String, aniSkipTitle: String): Boolean =
    aniWorldTitle.trim().equals(aniSkipTitle.trim(), ignoreCase = true)

internal fun shouldAutoResolveAniSkipMapping(mode: AniSkipMappingMode, hasStoredMapping: Boolean): Boolean =
    mode == AniSkipMappingMode.AUTOMATIC && !hasStoredMapping

/** Shared seek calculation matching upstream ani-skip's endTime - offset behavior. */
object AniSkipPlaybackLogic {
    fun targetPositionMs(segment: AniSkipSegment, durationMs: Long, offsetSeconds: Float): Long {
        val maximumSeconds = durationMs.takeIf { it > 0L && it != Long.MAX_VALUE }?.div(1000.0) ?: segment.endSeconds
        val earlyBySeconds = offsetSeconds.toDouble().coerceIn(0.0, 5.0)
        val targetSeconds = (segment.endSeconds - earlyBySeconds).coerceIn(segment.startSeconds, maximumSeconds.coerceAtLeast(segment.startSeconds))
        return (targetSeconds * 1000.0).toLong().coerceAtLeast(0L)
    }
}

/** Stable app-facing facade; existing favorites, history, player and settings keep using this one repository. */
class AniSkipRepository(context: Context) {
    private val core = AniSkipAndroidCore(context)

    suspend fun searchAnime(query: String): List<AniSkipAnime> = core.searchAnime(query)
    suspend fun searchAnime(query: String, source: AniSkipTitleSource): List<AniSkipAnime> = core.searchAnime(query, source)
    suspend fun findExactTitleMatch(title: String): AniSkipAnime? = core.findExactTitleMatch(title)
    suspend fun getSkipTimes(malId: Int, episodeNumber: Int, episodeLengthSeconds: Double): List<AniSkipSegment> =
        core.getSkipTimes(malId, episodeNumber, episodeLengthSeconds)
    fun rememberSubmittedSkipTime(malId: Int, episodeNumber: Int, episodeLengthSeconds: Double, segment: AniSkipSegment) =
        core.rememberSubmittedSkipTime(malId, episodeNumber, episodeLengthSeconds, segment)
    suspend fun submitSkipTime(
        malId: Int,
        episodeNumber: Int,
        type: AniSkipType,
        startSeconds: Double,
        endSeconds: Double,
        episodeLengthSeconds: Double,
        providerName: String
    ): JSONObject = core.submitSkipTime(malId, episodeNumber, type, startSeconds, endSeconds, episodeLengthSeconds, providerName)
    suspend fun vote(skipId: String, upvote: Boolean): JSONObject = core.vote(skipId, upvote)
}
