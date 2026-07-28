package io.github.lootdev78.aniworld

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallengeScreen(
    request: ChallengeRequest,
    checking: Boolean,
    sessionStatus: String?,
    adBlockEnabled: Boolean,
    enabledFilterLists: Set<String>,
    sessionPanelExpanded: Boolean,
    mediaPanelExpanded: Boolean,
    onSessionPanelExpanded: (Boolean) -> Unit,
    onMediaPanelExpanded: (Boolean) -> Unit,
    onVerify: (String) -> Unit,
    onClearSession: () -> Unit,
    onPlayDetectedMedia: (DetectedMediaCandidate) -> Unit,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(request.url) { mutableStateOf(request.url) }
    var pageTitle by remember(request.url) { mutableStateOf("") }
    var loading by remember(request.url) { mutableStateOf(true) }
    var pageError by remember(request.url) { mutableStateOf<String?>(null) }
    var detectedMedia by remember(request.url) { mutableStateOf<List<DetectedMediaCandidate>>(emptyList()) }
    var temporarilyAllowedHost by remember(request.url) { mutableStateOf<String?>(null) }
    val pageReady = remember(request.url) { AtomicBoolean(false) }
    val pageUrlRef = remember(request.url) { AtomicReference(request.url) }
    val allowedHostRef = remember(request.url) { AtomicReference<String?>(null) }
    val pendingDetection = remember(request.url) { ConcurrentHashMap<String, Map<String, String>>() }
    val isAniWorldChallenge = remember(request.url, request.mediaDetectionEnabled) {
        !request.mediaDetectionEnabled && isAniWorldHost(request.url)
    }

    fun detectAfterLoad(view: WebView, url: String, headers: Map<String, String>) {
        if (!request.mediaDetectionEnabled) return
        view.post {
            val cookie = CookieManager.getInstance().getCookie(url)
            val candidate = DirectMediaDetector.detect(
                rawUrl = url,
                requestHeaders = headers,
                pageUrl = pageUrlRef.get(),
                cookie = cookie,
                userAgent = view.settings.userAgentString ?: AniWorldRepository.UA
            ) ?: return@post
            val isNew = detectedMedia.none { it.key == candidate.key }
            detectedMedia = DirectMediaDetector.merge(detectedMedia, candidate)
            if (isNew) AppLogger.info("Media-Detector", "${candidate.formatLabel}-Quelle erkannt", candidate.host)
        }
    }

    fun queueDetection(view: WebView, url: String, headers: Map<String, String> = emptyMap()) {
        if (!request.mediaDetectionEnabled || !isAllowedWebUrl(url)) return
        if (pageReady.get()) detectAfterLoad(view, url, headers)
        else pendingDetection[url] = headers
    }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    val currentWebView = this
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(currentWebView, true)
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = AniWorldRepository.UA
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            AppLogger.info("Webfilter", "Popup blockiert", view?.url.orEmpty())
                            return false
                        }
                    }
                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(view: WebView, requestValue: WebResourceRequest): Boolean {
                            val url = requestValue.url.toString()
                            if (!isAllowedWebUrl(url)) return true
                            if (adBlockEnabled && WebAdBlocker.shouldBlockNavigation(url, pageUrlRef.get(), enabledFilterLists)) return true
                            queueDetection(view, url, requestValue.requestHeaders)
                            return false
                        }

                        override fun shouldInterceptRequest(view: WebView, requestValue: WebResourceRequest): WebResourceResponse? {
                            val url = requestValue.url.toString()
                            if (adBlockEnabled && WebAdBlocker.shouldBlock(requestValue, pageUrlRef.get(), enabledFilterLists, allowedHostRef.get())) {
                                return WebAdBlocker.emptyResponse()
                            }
                            queueDetection(view, url, requestValue.requestHeaders)
                            return null
                        }

                        override fun onLoadResource(view: WebView, url: String) {
                            queueDetection(view, url)
                            super.onLoadResource(view, url)
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            pageReady.set(false)
                            pendingDetection.clear()
                            loading = true
                            pageError = null
                            currentUrl = url
                            pageUrlRef.set(url)
                            AppLogger.info("WebView", context.getString(R.string.challenge_page_loading), Uri.parse(url).host.orEmpty())
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            currentUrl = url
                            pageUrlRef.set(url)
                            pageTitle = view.title.orEmpty()
                            CookieManager.getInstance().flush()
                            pageReady.set(true)
                            pendingDetection.entries.toList().forEach { (mediaUrl, headers) -> detectAfterLoad(view, mediaUrl, headers) }
                            pendingDetection.clear()
                        }

                        override fun onReceivedHttpError(view: WebView, requestValue: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (requestValue.isForMainFrame) {
                                pageError = context.getString(R.string.challenge_http_error, errorResponse.statusCode)
                                AppLogger.warn("WebView", pageError.orEmpty(), requestValue.url.toString())
                            }
                        }

                        override fun onReceivedError(view: WebView, requestValue: WebResourceRequest, error: WebResourceError) {
                            if (requestValue.isForMainFrame) {
                                pageError = context.getString(R.string.challenge_load_error, error.errorCode, error.description)
                                loading = false
                                AppLogger.warn("WebView", pageError.orEmpty(), requestValue.url.toString())
                            }
                        }
                    }
                    loadUrl(request.url)
                }
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 8.dp
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        val view = webView
                        if (view?.canGoBack() == true) view.goBack() else onClose()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(request.title.ifBlank { stringResource(R.string.manual_verification) }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pageTitle.ifBlank { Uri.parse(currentUrl).host.orEmpty() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalIconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, stringResource(R.string.challenge_reload)) }
                    FilledTonalIconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                if (loading || checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (request.mediaDetectionEnabled) {
                CollapsibleCard(
                    title = stringResource(R.string.media_detector_title),
                    count = detectedMedia.size,
                    expanded = mediaPanelExpanded,
                    onExpanded = onMediaPanelExpanded,
                    icon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                ) {
                    if (detectedMedia.isEmpty()) {
                        Text(stringResource(R.string.media_detector_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            detectedMedia.forEach { candidate ->
                                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(candidate.formatLabel, fontWeight = FontWeight.Bold)
                                            Text(candidate.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(candidate.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Button(onClick = { onPlayDetectedMedia(candidate) }) {
                                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.media_detector_play))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.media_detector_direct_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            CollapsibleCard(
                title = if (isAniWorldChallenge) stringResource(R.string.webview_session_title) else stringResource(R.string.webview_filter_session_title),
                expanded = sessionPanelExpanded,
                onExpanded = onSessionPanelExpanded,
                icon = { Icon(Icons.Default.Security, null, Modifier.size(20.dp)) }
            ) {
                Text(request.reason, style = MaterialTheme.typography.bodySmall)
                sessionStatus?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                pageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    if (adBlockEnabled) stringResource(R.string.webview_adblock_active, enabledFilterLists.size)
                    else stringResource(R.string.webview_adblock_inactive),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isAniWorldChallenge || request.retryAfterSuccess) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !checking && currentUrl.isNotBlank(),
                            onClick = { CookieManager.getInstance().flush(); onVerify(currentUrl) }
                        ) {
                            Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                            Text(
                                " " + stringResource(
                                    if (isAniWorldChallenge) R.string.challenge_check_session
                                    else R.string.challenge_retry_hoster
                                )
                            )
                        }
                    } else if (adBlockEnabled) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val host = Uri.parse(currentUrl).host
                                temporarilyAllowedHost = if (temporarilyAllowedHost == host) null else host
                                allowedHostRef.set(temporarilyAllowedHost)
                                webView?.reload()
                            }
                        ) {
                            Icon(Icons.Default.Block, null, Modifier.size(18.dp))
                            Text(" " + stringResource(if (temporarilyAllowedHost == Uri.parse(currentUrl).host) R.string.webview_enable_filter_domain else R.string.webview_disable_filter_domain))
                        }
                    }
                    OutlinedButton(
                        enabled = !checking,
                        onClick = {
                            detectedMedia = emptyList()
                            onClearSession()
                            webView?.clearCache(true)
                            webView?.reload()
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                        Text(" " + stringResource(R.string.challenge_cookies))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    count: Int? = null,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onExpanded(!expanded) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                count?.let { Text(stringResource(R.string.media_detector_count, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) content()
        }
    }
}

private fun isAllowedWebUrl(value: String): Boolean {
    val scheme = runCatching { Uri.parse(value).scheme?.lowercase() }.getOrNull()
    return scheme == "https" || scheme == "http" || scheme == "about"
}

private fun isAniWorldHost(value: String): Boolean = runCatching {
    val host = Uri.parse(value).host.orEmpty().lowercase()
    host == "aniworld.to" || host.endsWith(".aniworld.to")
}.getOrDefault(false)
