package io.github.lootdev78.aniworld

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Local HTTP relay for Cast receivers.
 *
 * Many hosters require Referer, Cookie or User-Agent headers which Chromecast and most DLNA
 * renderers cannot attach to their media request. The relay keeps those headers on the phone and
 * exposes a short-lived LAN URL that is reachable from normal Wi-Fi as well as from clients of the
 * phone's tethering hotspot. HLS playlists are rewritten so variants, keys and segments continue to
 * pass through the relay.
 */
class LocalCastRelay(context: Context) {
    private data class RelaySession(
        val headers: Map<String, String>,
        val createdAt: Long = System.currentTimeMillis()
    )

    @Suppress("unused") private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, RelaySession>()
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /** Returns a playback copy whose stream URL is reachable by the selected receiver. */
    fun preparePlayback(playback: ResolvedPlayback, receiverHost: String?): ResolvedPlayback {
        val server = runCatching { ensureStarted() }.getOrNull() ?: return playback
        val localAddress = CastNetworkAddress.localAddressFor(receiverHost) ?: return playback
        cleanupSessions()
        val token = UUID.randomUUID().toString().replace("-", "")
        sessions[token] = RelaySession(headers = playback.stream.headers)
        val authority = "${formatHost(localAddress.hostAddress.orEmpty())}:${server.localPort}"
        val relayUrl = buildRelayUrl(authority, token, playback.stream.url)
        return playback.copy(
            stream = playback.stream.copy(
                url = relayUrl,
                // Keep the original media type because the relay URL itself has no file suffix.
                mimeType = playback.stream.mimeType ?: inferMediaType(playback.stream.url),
                // The receiver talks to the phone; upstream-only headers must not be sent to it.
                headers = emptyMap()
            )
        )
    }

    fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        sessions.clear()
        scope.cancel()
    }

    @Synchronized
    private fun ensureStarted(): ServerSocket {
        serverSocket?.takeIf { !it.isClosed }?.let { return it }
        val created = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0), ACCEPT_BACKLOG)
        }
        serverSocket = created
        acceptJob = scope.launch {
            while (isActive && !created.isClosed) {
                val socket = runCatching { created.accept() }.getOrNull() ?: break
                launch { handleClient(socket, created.localPort) }
            }
        }
        return created
    }

    private fun handleClient(socket: Socket, relayPort: Int) {
        socket.use { connection ->
            connection.soTimeout = CLIENT_TIMEOUT_MS
            val remote = connection.inetAddress
            if (!isAllowedClient(remote)) {
                writeSimpleResponse(connection, 403, "Forbidden")
                return
            }
            val input = BufferedInputStream(connection.getInputStream())
            val requestLine = readLine(input)?.take(MAX_REQUEST_LINE) ?: return
            val requestParts = requestLine.split(' ')
            if (requestParts.size < 2) {
                writeSimpleResponse(connection, 400, "Bad Request")
                return
            }
            val method = requestParts[0].uppercase(Locale.ROOT)
            val requestTarget = requestParts[1]
            val incomingHeaders = readHeaders(input)
            if (method == "OPTIONS") {
                writeCorsPreflight(connection)
                return
            }
            if (method != "GET" && method != "HEAD") {
                writeSimpleResponse(connection, 405, "Method Not Allowed")
                return
            }
            val parsed = runCatching { URI(requestTarget) }.getOrNull()
            val token = parsed?.path?.removePrefix("/relay/")?.substringBefore('/')?.takeIf(String::isNotBlank)
            val encodedUrl = parsed?.rawQuery
                ?.split('&')
                ?.firstOrNull { it.startsWith("u=") }
                ?.substringAfter("u=")
            val targetUrl = encodedUrl?.let { runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull() }
            val relaySession = token?.let(sessions::get)
            if (relaySession == null || targetUrl.isNullOrBlank()) {
                writeSimpleResponse(connection, 404, "Not Found")
                return
            }

            val requestBuilder = Request.Builder().url(targetUrl)
            relaySession.headers.forEach { (name, value) ->
                if (name.isSafeForwardHeader()) requestBuilder.header(name, value)
            }
            FORWARDED_RECEIVER_HEADERS.forEach { name ->
                incomingHeaders[name.lowercase(Locale.ROOT)]?.let { requestBuilder.header(name, it) }
            }
            requestBuilder.header("Accept-Encoding", "identity")
            requestBuilder.header("Connection", "close")
            requestBuilder.method(method, null)

            val upstream = runCatching { client.newCall(requestBuilder.build()).execute() }.getOrElse { error ->
                writeSimpleResponse(connection, 502, error.message ?: "Bad Gateway")
                return
            }
            upstream.use { response ->
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty()
                val body = response.body
                val isHls = contentType.contains("mpegurl", ignoreCase = true) ||
                    finalUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                val isDash = contentType.contains("dash+xml", ignoreCase = true) ||
                    finalUrl.substringBefore('?').endsWith(".mpd", ignoreCase = true)
                val localAuthority = incomingHeaders["host"]
                    ?.takeIf(::isSafeAuthority)
                    ?: "${formatHost(connection.localAddress.hostAddress.orEmpty())}:$relayPort"

                if (method == "GET" && (isHls || isDash)) {
                    val original = body.bytes().toString(StandardCharsets.UTF_8)
                    val rewritten = if (isHls) {
                        rewriteHls(original, finalUrl, localAuthority, token)
                    } else {
                        rewriteDash(original, finalUrl, localAuthority, token)
                    }
                    val bytes = rewritten.toByteArray(StandardCharsets.UTF_8)
                    writeResponseHeaders(
                        connection = connection,
                        statusCode = response.code,
                        statusMessage = response.message,
                        upstreamHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                        contentLength = bytes.size.toLong(),
                        contentType = contentType.ifBlank {
                            if (isHls) "application/vnd.apple.mpegurl" else "application/dash+xml"
                        }
                    )
                    BufferedOutputStream(connection.getOutputStream()).use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                } else {
                    val length = body.contentLength().takeIf { it >= 0L }
                    writeResponseHeaders(
                        connection = connection,
                        statusCode = response.code,
                        statusMessage = response.message,
                        upstreamHeaders = response.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                        contentLength = length,
                        contentType = contentType
                    )
                    if (method == "GET") {
                        body.byteStream().use { upstreamInput ->
                            connection.getOutputStream().use { output ->
                                upstreamInput.copyTo(output, STREAM_BUFFER_SIZE)
                                output.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun rewriteHls(text: String, baseUrl: String, authority: String, token: String): String {
        return text.lineSequence().joinToString("\n") { originalLine ->
            var line = originalLine
            line = HLS_URI_ATTRIBUTE.replace(line) { match ->
                val resolved = resolveRemoteUrl(baseUrl, match.groupValues[1])
                "URI=\"${buildRelayUrl(authority, token, resolved)}\""
            }
            if (line.isNotBlank() && !line.startsWith('#')) {
                buildRelayUrl(authority, token, resolveRemoteUrl(baseUrl, line.trim()))
            } else line
        }
    }

    private fun rewriteDash(text: String, baseUrl: String, authority: String, token: String): String {
        var result = DASH_BASE_URL.replace(text) { match ->
            val resolved = resolveRemoteUrl(baseUrl, match.groupValues[2].trim())
            match.groupValues[1] + buildRelayUrl(authority, token, resolved) + match.groupValues[3]
        }
        result = DASH_URL_ATTRIBUTE.replace(result) { match ->
            val candidate = match.groupValues[2]
            if (candidate.startsWith("data:", true) || candidate.startsWith("urn:", true)) {
                match.value
            } else {
                val resolved = resolveRemoteUrl(baseUrl, candidate)
                match.groupValues[1] + buildRelayUrl(authority, token, resolved) + match.groupValues[3]
            }
        }
        return result
    }

    private fun resolveRemoteUrl(baseUrl: String, candidate: String): String = runCatching {
        URI(baseUrl).resolve(candidate).toString()
    }.getOrDefault(candidate)

    private fun inferMediaType(url: String): String = when {
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "application/dash+xml"
        url.substringBefore('?').endsWith(".webm", ignoreCase = true) -> "video/webm"
        url.substringBefore('?').endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun buildRelayUrl(authority: String, token: String, targetUrl: String): String {
        // Keep DASH template variables such as $Number$ visible so the receiver can substitute them.
        val encoded = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8.name())
            .replace("%24", "$")
        return "http://$authority/relay/$token?u=$encoded"
    }

    private fun writeResponseHeaders(
        connection: Socket,
        statusCode: Int,
        statusMessage: String,
        upstreamHeaders: Map<String, String>,
        contentLength: Long?,
        contentType: String
    ) {
        val output = BufferedOutputStream(connection.getOutputStream())
        val message = statusMessage.ifBlank { statusText(statusCode) }
        output.write("HTTP/1.1 $statusCode $message\r\n".toByteArray(StandardCharsets.US_ASCII))
        val passthrough = listOf("content-range", "accept-ranges", "cache-control", "etag", "last-modified")
        passthrough.forEach { name ->
            upstreamHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.let { value ->
                output.write("${canonicalHeader(name)}: $value\r\n".toByteArray(StandardCharsets.UTF_8))
            }
        }
        if (contentType.isNotBlank()) output.write("Content-Type: $contentType\r\n".toByteArray(StandardCharsets.UTF_8))
        contentLength?.let { output.write("Content-Length: $it\r\n".toByteArray(StandardCharsets.US_ASCII)) }
        output.write("Access-Control-Allow-Origin: *\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Access-Control-Allow-Headers: Range, Content-Type\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun writeCorsPreflight(connection: Socket) {
        val output = BufferedOutputStream(connection.getOutputStream())
        output.write(
            ("HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Range, Content-Type\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        )
        output.flush()
    }

    private fun writeSimpleResponse(connection: Socket, code: Int, text: String) {
        val body = text.toByteArray(StandardCharsets.UTF_8)
        val output = BufferedOutputStream(connection.getOutputStream())
        output.write(
            ("HTTP/1.1 $code ${statusText(code)}\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        )
        output.write(body)
        output.flush()
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        var total = 0
        while (true) {
            val line = readLine(input) ?: break
            total += line.length
            if (total > MAX_HEADER_BYTES || line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase(Locale.ROOT)] = line.substring(separator + 1).trim()
            }
        }
        return headers
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        var previous = -1
        while (bytes.size <= MAX_HEADER_BYTES) {
            val current = input.read()
            if (current < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.UTF_8)
            if (previous == '\r'.code && current == '\n'.code) {
                if (bytes.isNotEmpty()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().toString(StandardCharsets.UTF_8)
            }
            bytes += current.toByte()
            previous = current
        }
        return null
    }

    private fun cleanupSessions() {
        val cutoff = System.currentTimeMillis() - SESSION_TTL_MS
        sessions.entries.removeIf { it.value.createdAt < cutoff }
        if (sessions.size > MAX_SESSIONS) {
            sessions.entries.sortedBy { it.value.createdAt }.take(sessions.size - MAX_SESSIONS).forEach {
                sessions.remove(it.key)
            }
        }
    }

    private fun isAllowedClient(address: InetAddress?): Boolean {
        if (address == null) return false
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val bytes = address.address
        // Android also classifies CGNAT (100.64.0.0/10) and IPv6 ULA (fc00::/7) as LAN.
        if (address is Inet4Address && bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 100 && second in 64..127) return true
        }
        if (address is Inet6Address && bytes.isNotEmpty() && (bytes[0].toInt() and 0xFE) == 0xFC) return true
        return false
    }

    private fun String.isSafeForwardHeader(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return normalized !in HOP_BY_HOP_HEADERS && normalized !in setOf("host", "content-length", "range")
    }

    private fun isSafeAuthority(value: String): Boolean = value.length in 1..128 &&
        value.all { it.isLetterOrDigit() || it in ".:-[]" }

    private fun canonicalHeader(name: String): String = name.split('-').joinToString("-") {
        it.replaceFirstChar(Char::uppercaseChar)
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        304 -> "Not Modified"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        416 -> "Range Not Satisfiable"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        else -> "Response"
    }

    private fun formatHost(host: String): String = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host

    private companion object {
        const val ACCEPT_BACKLOG = 24
        const val CLIENT_TIMEOUT_MS = 30_000
        const val MAX_HEADER_BYTES = 64 * 1024
        const val MAX_REQUEST_LINE = 16 * 1024
        const val STREAM_BUFFER_SIZE = 64 * 1024
        const val MAX_SESSIONS = 32
        const val SESSION_TTL_MS = 2 * 60 * 60 * 1000L
        val HLS_URI_ATTRIBUTE = Regex("""URI=\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
        val DASH_BASE_URL = Regex("""(<BaseURL(?:\s[^>]*)?>)([^<]+)(</BaseURL>)""", RegexOption.IGNORE_CASE)
        val DASH_URL_ATTRIBUTE = Regex("""((?:media|initialization|sourceURL|href)=\")([^\"]+)(\")""", RegexOption.IGNORE_CASE)
        val FORWARDED_RECEIVER_HEADERS = listOf("Range", "If-Range", "If-None-Match", "If-Modified-Since", "Accept")
        val HOP_BY_HOP_HEADERS = setOf(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade", "accept-encoding"
        )
    }
}

/** Selects the address on the same interface/subnet as a Cast receiver, including tethering APs. */
private object CastNetworkAddress {
    fun localAddressFor(remoteHost: String?): InetAddress? {
        val remote = remoteHost?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
        if (remote != null) {
            runCatching {
                DatagramSocket().use { socket ->
                    socket.connect(InetSocketAddress(remote, 9))
                    socket.localAddress
                }
            }.getOrNull()?.takeIf { !it.isAnyLocalAddress && !it.isLoopbackAddress }?.let { return it }
        }
        val candidates = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { network ->
                    Collections.list(network.inetAddresses).mapNotNull { address ->
                        if (address.isLoopbackAddress || address.isLinkLocalAddress) null else network to address
                    }
                }
        }.getOrDefault(emptyList())
        return candidates
            .sortedByDescending { (network, address) -> score(network.name, address, remote) }
            .map { it.second }
            .firstOrNull { it is Inet4Address || it is Inet6Address }
    }

    private fun score(interfaceName: String, local: InetAddress, remote: InetAddress?): Int {
        var score = 0
        val name = interfaceName.lowercase(Locale.ROOT)
        if (local.isSiteLocalAddress) score += 20
        if (local is Inet4Address) score += 10
        if (listOf("ap", "softap", "swlan", "wlan1", "wlan2", "rndis", "bt-pan").any(name::contains)) score += 40
        val host = local.hostAddress.orEmpty()
        if (host.endsWith(".1") || host.startsWith("192.168.43.") || host.startsWith("192.168.232.")) score += 30
        if (remote is Inet4Address && local is Inet4Address) {
            val left = local.address
            val right = remote.address
            if (left[0] == right[0] && left[1] == right[1] && left[2] == right[2]) score += 100
        }
        return score
    }
}
