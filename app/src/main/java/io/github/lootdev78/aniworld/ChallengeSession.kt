package io.github.lootdev78.aniworld

import android.content.Context
import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

/**
 * Shares the cookie session created by the in-app WebView with OkHttp.
 * The user must complete any interactive verification manually in the WebView.
 */
class ChallengeSessionManager(private val context: Context) {
    private val cookieManager: CookieManager = CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    fun cookieHeader(url: String): String? =
        runCatching { cookieManager.getCookie(url) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun saveResponseCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        setCookieHeaders.forEach { value -> cookieManager.setCookie(url, value) }
        cookieManager.flush()
    }

    fun clear() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun cookieSummary(url: String): String {
        val names = cookieHeader(url)
            ?.split(';')
            ?.mapNotNull { pair -> pair.substringBefore('=').trim().takeIf { it.isNotBlank() } }
            ?.distinct()
            .orEmpty()
        return when (names.size) {
            0 -> context.getString(R.string.cookies_none)
            1 -> context.getString(R.string.cookies_one, names.first())
            else -> context.getString(
                R.string.cookies_many,
                names.size,
                names.take(4).joinToString(),
                if (names.size > 4) context.getString(R.string.cookies_more) else ""
            )
        }
    }
}

class WebViewCookieBridgeInterceptor(
    private val sessions: ChallengeSessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val webViewCookies = sessions.cookieHeader(original.url.toString())
        val requestCookies = original.header("Cookie")
        val mergedCookies = listOfNotNull(requestCookies, webViewCookies)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
        val request = original.newBuilder().apply {
            mergedCookies?.let { header("Cookie", it) }
        }.build()

        val response = chain.proceed(request)
        sessions.saveResponseCookies(
            response.request.url.toString(),
            response.headers("Set-Cookie")
        )
        return response
    }
}

class ChallengeRequiredException(
    val challengeUrl: String,
    val challengeReason: String
) : IllegalStateException(challengeReason)

object ChallengeDetector {
    private val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val alwaysStrongMarkers = listOf(
        "cf-chl-",
        "challenge-platform",
        "cf-turnstile-response"
    )
    private val titleMarkers = listOf(
        "just a moment",
        "verify you are human",
        "checking your browser",
        "attention required",
        "security verification",
        "captcha"
    )

    fun throwIfRequired(
        context: Context,
        url: String,
        statusCode: Int,
        contentType: String?,
        body: String
    ) {
        val htmlLike = contentType.orEmpty().contains("text/html", ignoreCase = true) ||
            body.trimStart().startsWith("<!doctype html", ignoreCase = true) ||
            body.trimStart().startsWith("<html", ignoreCase = true)
        if (!htmlLike) return

        val lower = body.take(512_000).lowercase()
        val title = titleRegex.find(lower)?.groupValues?.getOrNull(1).orEmpty()
        val suspiciousStatus = statusCode == 403 || statusCode == 429 || statusCode == 503
        val strongMarkup = alwaysStrongMarkers.any(lower::contains)
        val challengeTitle = titleMarkers.any(title::contains)
        if (!suspiciousStatus && !strongMarkup && !challengeTitle) return

        val host = runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { context.getString(R.string.website) }
        val captchaLike = "captcha" in lower || "turnstile" in lower || "verify you are human" in lower
        val reason = if (captchaLike) {
            context.getString(R.string.challenge_captcha_required, host)
        } else {
            context.getString(R.string.challenge_browser_required, host)
        }
        throw ChallengeRequiredException(url, reason)
    }
}
