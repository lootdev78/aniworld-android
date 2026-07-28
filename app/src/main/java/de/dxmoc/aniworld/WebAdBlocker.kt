package de.dxmoc.aniworld

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

object WebFilterList {
    const val ADVERTISING = "advertising"
    const val TRACKING = "tracking"
    const val POPUPS = "popups"
    const val REDIRECTS = "redirects"
    val ALL_IDS: Set<String> = linkedSetOf(ADVERTISING, TRACKING, POPUPS, REDIRECTS)
}

/**
 * A deliberately conservative first-party WebView filter. It is not a claim of 1:1 uBlock Origin
 * compatibility, but it blocks the common ad, tracker, popup and redirect endpoints encountered in
 * hoster pages without inspecting or modifying media responses.
 */
object WebAdBlocker {
    private val advertisingHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adnxs.com",
        "popads.net", "propellerads.com", "adsterra.com", "exoclick.com", "trafficjunky.net",
        "onclicka.com", "hilltopads.net", "juicyads.com", "revcontent.com", "taboola.com",
        "outbrain.com", "mgid.com"
    )
    private val trackingHosts = setOf(
        "google-analytics.com", "googletagmanager.com", "facebook.net", "hotjar.com",
        "clarity.ms", "scorecardresearch.com", "quantserve.com", "segment.io", "mixpanel.com",
        "amplitude.com", "matomo.cloud"
    )
    private val adPathTokens = setOf(
        "/ads/", "/adserver", "/adserve", "/advert", "/banner", "/popunder", "/popup",
        "clickunder", "interstitial", "vast.xml", "prebid", "adtag", "adsystem"
    )
    private val redirectTokens = setOf(
        "/redirect?", "/out?", "/go?", "redirect_url=", "target_url=", "clickid=", "subid="
    )
    private val mediaExtensions = setOf(
        ".m3u8", ".mpd", ".ism", ".mp4", ".m4v", ".webm", ".mkv", ".ts", ".m4s",
        ".aac", ".mp3", ".vtt", ".srt"
    )

    fun shouldBlock(
        request: WebResourceRequest,
        pageUrl: String,
        enabledLists: Set<String>,
        temporarilyAllowedHost: String?
    ): Boolean = shouldBlock(request.url.toString(), pageUrl, enabledLists, temporarilyAllowedHost, request.isForMainFrame)

    fun shouldBlock(
        requestUrl: String,
        pageUrl: String,
        enabledLists: Set<String>,
        temporarilyAllowedHost: String?,
        isMainFrame: Boolean = false
    ): Boolean {
        if (enabledLists.isEmpty()) return false
        val uri = runCatching { Uri.parse(requestUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme !in setOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (host.isBlank() || hostMatches(host, temporarilyAllowedHost.orEmpty())) return false
        if (looksLikeMedia(uri)) return false

        val pageHost = runCatching { Uri.parse(pageUrl).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val sameOrigin = pageHost.isNotBlank() && hostMatches(host, pageHost)
        val lower = requestUrl.lowercase(Locale.ROOT)

        if (WebFilterList.ADVERTISING in enabledLists &&
            (advertisingHosts.any { hostMatches(host, it) } || adPathTokens.any(lower::contains))) return true
        if (WebFilterList.TRACKING in enabledLists &&
            (trackingHosts.any { hostMatches(host, it) } || lower.contains("/track") || lower.contains("/pixel") || lower.contains("analytics"))) return true
        if (WebFilterList.REDIRECTS in enabledLists && !isMainFrame && !sameOrigin && redirectTokens.any(lower::contains)) return true
        return false
    }

    fun shouldBlockNavigation(url: String, pageUrl: String, enabledLists: Set<String>): Boolean {
        if (WebFilterList.REDIRECTS !in enabledLists && WebFilterList.POPUPS !in enabledLists) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        if (uri.scheme !in setOf("http", "https")) return true
        val lower = url.lowercase(Locale.ROOT)
        if (looksLikeMedia(uri)) return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val pageHost = runCatching { Uri.parse(pageUrl).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        if (hostMatches(host, pageHost)) return false
        return advertisingHosts.any { hostMatches(host, it) } ||
            trackingHosts.any { hostMatches(host, it) } ||
            (WebFilterList.REDIRECTS in enabledLists && redirectTokens.any(lower::contains))
    }

    fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "Blocked",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0))
    )

    private fun looksLikeMedia(uri: Uri): Boolean {
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        return mediaExtensions.any { path.endsWith(it) } ||
            uri.getQueryParameter("mime")?.startsWith("video/") == true ||
            uri.getQueryParameter("type")?.contains("mpegurl", true) == true
    }

    private fun hostMatches(host: String, rule: String): Boolean {
        if (rule.isBlank()) return false
        val clean = rule.lowercase(Locale.ROOT).removePrefix("www.")
        val value = host.removePrefix("www.")
        return value == clean || value.endsWith(".$clean")
    }
}
