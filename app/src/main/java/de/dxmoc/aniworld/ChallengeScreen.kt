package de.dxmoc.aniworld

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    request: ChallengeRequest,
    checking: Boolean,
    sessionStatus: String?,
    onVerify: (String) -> Unit,
    onClearSession: () -> Unit,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(request.url) { mutableStateOf(request.url) }
    var pageTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(request.title.ifBlank { stringResource(R.string.manual_verification) })
                        Text(
                            pageTitle.ifBlank { Uri.parse(currentUrl).host.orEmpty() },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val view = webView
                        if (view?.canGoBack() == true) view.goBack() else onClose()
                    }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.challenge_reload))
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, null, Modifier.size(20.dp))
                        Text(request.reason, style = MaterialTheme.typography.bodySmall)
                    }
                    sessionStatus?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium)
                    }
                    pageError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (loading || checking) LinearProgressIndicator(Modifier.fillMaxWidth())

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                            databaseEnabled = true
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
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                                !isAllowedWebUrl(url)

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = !isAllowedWebUrl(request.url.toString())

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                                pageError = null
                                currentUrl = url
                                AppLogger.info("WebView", context.getString(R.string.challenge_page_loading), Uri.parse(url).host.orEmpty())
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                currentUrl = url
                                pageTitle = view.title.orEmpty()
                                CookieManager.getInstance().flush()
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse
                            ) {
                                if (request.isForMainFrame) {
                                    pageError = context.getString(R.string.challenge_http_error, errorResponse.statusCode)
                                    AppLogger.warn("WebView", pageError.orEmpty(), request.url.toString())
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    pageError = context.getString(R.string.challenge_load_error, error.description)
                                    AppLogger.warn("WebView", pageError.orEmpty(), request.url.toString())
                                }
                            }

                        }
                        loadUrl(request.url)
                    }
                },
                update = { view ->
                    if (view.url.isNullOrBlank()) view.loadUrl(request.url)
                }
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !checking && currentUrl.isNotBlank(),
                    onClick = {
                        CookieManager.getInstance().flush()
                        onVerify(currentUrl)
                    }
                ) {
                    Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.challenge_check_session))
                }
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
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

private fun isAllowedWebUrl(value: String): Boolean {
    val scheme = runCatching { Uri.parse(value).scheme?.lowercase() }.getOrNull()
    return scheme == "https" || scheme == "http" || scheme == "about"
}
