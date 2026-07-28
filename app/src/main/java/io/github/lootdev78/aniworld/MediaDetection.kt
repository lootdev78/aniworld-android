package io.github.lootdev78.aniworld

import android.net.Uri
import java.net.URI
import java.util.Locale

/**
 * A direct-media request observed in the in-app WebView.
 *
 * Only ordinary HTTP(S) requests whose URL already identifies a Media3-compatible
 * manifest or progressive media file are accepted. This class does not execute or
 * decode scripts, reconstruct tokens, inspect encrypted payloads, or bypass access
 * controls.
 */
data class DetectedMediaCandidate(
    val url: String,
    val mimeType: String,
    val formatLabel: String,
    val host: String,
    val displayName: String,
    val headers: Map<String, String>,
    val detectedAt: Long = System.currentTimeMillis()
) {
    val key: String get() = url
}

object DirectMediaDetector {
    private data class Format(
        val mimeType: String,
        val label: String,
        val priority: Int
    )

    private val extensionPattern = Regex(
        pattern = """\.(m3u8|mpd|mp4|m4v|mov|webm|mkv|ogv|ogg|mp3|m4a|aac)(?:[?#]|$)""",
        option = RegexOption.IGNORE_CASE
    )

    private val smoothStreamingPattern = Regex(
        pattern = """\.ism(?:/manifest|[vc])?(?:[?#]|$)""",
        option = RegexOption.IGNORE_CASE
    )

    private val likelyManifestPattern = Regex(
        pattern = """(?:^|[/_.-])(master|playlist|manifest)(?:[/_.?&=-]|$)""",
        option = RegexOption.IGNORE_CASE
    )

    fun detect(
        rawUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
        pageUrl: String? = null,
        cookie: String? = null,
        userAgent: String = AniWorldRepository.UA
    ): DetectedMediaCandidate? {
        val url = rawUrl.trim()
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null

        val format = classify(url) ?: return null
        if (looksLikeSegment(url)) return null

        val host = uri.host.orEmpty()
        val headers = linkedMapOf<String, String>()

        requestHeaders.entries.forEach { (name, value) ->
            when (name.lowercase(Locale.ROOT)) {
                "referer", "origin", "authorization", "accept", "accept-language" -> {
                    if (value.isNotBlank()) headers[name] = value
                }
            }
        }

        val referer = requestHeaders.entries
            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.value
            ?.takeIf(String::isNotBlank)
            ?: pageUrl?.takeIf(String::isNotBlank)

        if (!referer.isNullOrBlank()) headers["Referer"] = referer
        headers["User-Agent"] = userAgent
        if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie

        val fileName = uri.lastPathSegment
            ?.substringBefore('?')
            ?.takeIf(String::isNotBlank)
            ?: host.ifBlank { format.label }

        return DetectedMediaCandidate(
            url = url,
            mimeType = format.mimeType,
            formatLabel = format.label,
            host = host,
            displayName = fileName,
            headers = headers
        )
    }

    fun mimeTypeFor(url: String): String? = classify(url)?.mimeType

    fun merge(
        existing: List<DetectedMediaCandidate>,
        candidate: DetectedMediaCandidate,
        limit: Int = 12
    ): List<DetectedMediaCandidate> {
        val merged = buildList {
            add(candidate)
            existing.filterNot { it.key == candidate.key }.forEach { add(it) }
        }

        return merged
            .sortedWith(
                compareByDescending<DetectedMediaCandidate> { priority(it.mimeType) }
                    .thenByDescending { it.detectedAt }
            )
            .take(limit)
    }

    private fun classify(url: String): Format? {
        val lower = url.lowercase(Locale.ROOT)

        if (smoothStreamingPattern.containsMatchIn(lower)) {
            return Format("application/vnd.ms-sstr+xml", "SmoothStreaming", 80)
        }

        val extension = extensionPattern.find(lower)?.groupValues?.getOrNull(1)
        return when (extension) {
            "m3u8" -> Format("application/x-mpegURL", "HLS", 100)
            "mpd" -> Format("application/dash+xml", "DASH", 95)
            "mp4", "m4v" -> Format("video/mp4", "MP4", 70)
            "mov" -> Format("video/quicktime", "MOV", 65)
            "webm" -> Format("video/webm", "WebM", 65)
            "mkv" -> Format("video/x-matroska", "Matroska", 65)
            "ogv", "ogg" -> Format("video/ogg", "Ogg", 55)
            "mp3" -> Format("audio/mpeg", "MP3", 45)
            "m4a" -> Format("audio/mp4", "M4A", 45)
            "aac" -> Format("audio/aac", "AAC", 40)
            null -> if (likelyManifestPattern.containsMatchIn(lower)) {
                // A URL that merely says “manifest” is too ambiguous without a known
                // extension. Do not guess a MIME type and accidentally open an API call.
                null
            } else {
                null
            }
            else -> null
        }
    }

    private fun looksLikeSegment(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault("")

        return path.endsWith(".m4s") ||
            path.endsWith(".cmfv") ||
            path.endsWith(".cmfa") ||
            path.endsWith(".ts") ||
            path.contains("/segment/") ||
            path.contains("/segments/") ||
            Regex("""(?:^|[/_-])seg(?:ment)?[-_]?\d+""").containsMatchIn(path)
    }

    private fun priority(mimeType: String): Int = when (mimeType) {
        "application/x-mpegURL" -> 100
        "application/dash+xml" -> 95
        "application/vnd.ms-sstr+xml" -> 80
        "video/mp4" -> 70
        "video/quicktime", "video/webm", "video/x-matroska" -> 65
        "video/ogg" -> 55
        else -> 40
    }
}
