package io.github.lootdev78.aniworld

import java.net.URI
import java.util.Locale

/**
 * Read-only stream information for the player UI.
 *
 * The helper intentionally does not resolve or probe the URL. It only presents information
 * already available in [StreamSource] or safely inferable from the URL suffix.
 */
data class StreamPresentationInfo(
    val formatLabel: String,
    val mimeType: String,
    val host: String,
    val adaptive: Boolean
) {
    val compactLabel: String
        get() = buildString {
            append(formatLabel)
            if (mimeType.isNotBlank()) append(" · ").append(mimeType)
            if (host.isNotBlank()) append(" · ").append(host)
        }
}

object StreamPresentation {
    fun from(stream: StreamSource): StreamPresentationInfo {
        val normalizedMime = stream.mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DirectMediaDetector.mimeTypeFor(stream.url)
            ?: mimeFromUrl(stream.url)
            ?: "application/octet-stream"

        val label = labelFor(normalizedMime, stream.url)
        val host = runCatching { URI(stream.url).host.orEmpty() }.getOrDefault("")
        return StreamPresentationInfo(
            formatLabel = label,
            mimeType = normalizedMime,
            host = host,
            adaptive = normalizedMime.equals("application/x-mpegURL", true) ||
                normalizedMime.equals("application/vnd.apple.mpegurl", true) ||
                normalizedMime.equals("application/dash+xml", true) ||
                normalizedMime.equals("application/vnd.ms-sstr+xml", true)
        )
    }

    private fun mimeFromUrl(url: String): String? {
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".ism") || path.contains(".ism/manifest") -> "application/vnd.ms-sstr+xml"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".ogv") || path.endsWith(".ogg") -> "video/ogg"
            else -> null
        }
    }

    private fun labelFor(mimeType: String, url: String): String = when (mimeType.lowercase(Locale.ROOT)) {
        "application/x-mpegurl", "application/vnd.apple.mpegurl" -> "HLS"
        "application/dash+xml" -> "DASH"
        "application/vnd.ms-sstr+xml" -> "SmoothStreaming"
        "video/mp4" -> "MP4"
        "video/webm" -> "WebM"
        "video/x-matroska" -> "Matroska"
        "video/quicktime" -> "MOV"
        "video/ogg" -> "Ogg Video"
        else -> url.substringBefore('?').substringAfterLast('.', "Stream").uppercase(Locale.ROOT)
            .takeIf { it.length in 2..12 } ?: "Stream"
    }
}
