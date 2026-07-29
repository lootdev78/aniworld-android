package io.github.lootdev78.aniworld

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local receiver website with WebSocket, SSE and polling fallbacks.
 * The website and control API use a stable preferred port; protected media is served by LocalCastRelay.
 */
class LocalPlaybackWebServer(
    context: Context,
    private val relay: LocalCastRelay,
    private val preferredPort: Int,
    private val pin: String
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = AtomicInteger(0)
    private val relayUrls = ConcurrentHashMap<String, Pair<String, String>>()
    private var acceptJob: Job? = null
    private var idleShutdownJob: Job? = null
    @Volatile private var serverSocket: ServerSocket? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    fun start() {
        if (isRunning) return
        val socket = bindPreferredPort()
        serverSocket = socket
        val pageUrl = localPageUrl(socket.localPort)
        RemotePlaybackRuntime.updateServerStatus {
            it.copy(
                enabled = true,
                running = true,
                preferredPort = preferredPort,
                actualPort = socket.localPort,
                pin = pin,
                pageUrl = pageUrl,
                connectedClients = 0,
                lastError = null
            )
        }
        AppLogger.info("Web-Relay", "Lokaler Player gestartet", pageUrl)
        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handle(client) }
            }
        }
    }

    fun scheduleIdleShutdown() {
        idleShutdownJob?.cancel()
        idleShutdownJob = scope.launch {
            delay(IDLE_SHUTDOWN_MS)
            if (RemotePlaybackRuntime.currentPlayback() == null && clients.get() == 0) {
                RemotePlaybackRuntime.stopServer(clearPlayback = false)
            }
        }
    }

    fun close() {
        idleShutdownJob?.cancel()
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        relayUrls.clear()
        scope.cancel()
    }

    private fun bindPreferredPort(): ServerSocket {
        val start = preferredPort.coerceIn(1024, 65535)
        var lastError: Throwable? = null
        for (port in start..minOf(65535, start + PORT_FALLBACK_COUNT)) {
            val candidate = ServerSocket()
            val result = runCatching {
                candidate.reuseAddress = true
                candidate.bind(InetSocketAddress(port), 32)
            }
            if (result.isSuccess) return candidate
            lastError = result.exceptionOrNull()
            runCatching { candidate.close() }
        }
        throw IllegalStateException("Kein freier lokaler Port ab $start", lastError)
    }

    private fun handle(socket: Socket) {
        socket.use { connection ->
            connection.soTimeout = CLIENT_TIMEOUT_MS
            if (!allowed(connection.inetAddress)) {
                writeText(connection, 403, "Forbidden")
                return
            }
            val input = BufferedInputStream(connection.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.ROOT)
            val target = parts[1]
            val headers = readHeaders(input)
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
            val body = if (contentLength > 0) readExactly(input, contentLength) else ByteArray(0)
            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))
            val suppliedPin = query["pin"] ?: path.removePrefix("/watch/").substringBefore('/').takeIf { path.startsWith("/watch/") }

            if (method == "OPTIONS") {
                writeNoContent(connection)
                return
            }
            if (path == "/health") {
                writeJson(connection, JSONObject().put("ok", true).put("running", true))
                return
            }
            if (path == "/" || path.startsWith("/watch/")) {
                writeHtml(connection, receiverHtml(suppliedPin.orEmpty()))
                return
            }
            if (suppliedPin != pin) {
                writeText(connection, 401, "Invalid PIN")
                return
            }
            when {
                path == "/api/state" && method == "GET" -> writeJson(connection, stateJson(connection.inetAddress))
                path == "/api/control" && method == "POST" -> {
                    val accepted = applyCommand(body.toString(StandardCharsets.UTF_8))
                    writeJson(connection, JSONObject().put("accepted", accepted))
                }
                path == "/api/events" && method == "GET" -> serveEvents(connection)
                path == "/ws" && headers["upgrade"]?.equals("websocket", true) == true -> serveWebSocket(connection, input, headers)
                else -> writeText(connection, 404, "Not Found")
            }
        }
    }

    private fun serveEvents(connection: Socket) {
        clientConnected()
        try {
            val output = BufferedOutputStream(connection.getOutputStream())
            output.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
            )
            output.flush()
            var lastRevision = -1L
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < SSE_CONNECTION_MS) {
                val state = RemotePlaybackRuntime.snapshot.value
                if (state.revision != lastRevision) {
                    lastRevision = state.revision
                    val payload = stateJson(connection.inetAddress).toString()
                    output.write("event: state\ndata: $payload\n\n".toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                } else {
                    output.write(": keep-alive\n\n".toByteArray(StandardCharsets.US_ASCII))
                    output.flush()
                }
                Thread.sleep(750L)
            }
        } finally {
            clientDisconnected()
        }
    }

    private fun serveWebSocket(connection: Socket, input: BufferedInputStream, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return writeText(connection, 400, "Missing WebSocket key")
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII))
        )
        val output = BufferedOutputStream(connection.getOutputStream())
        output.write(
            ("HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        )
        output.flush()
        connection.soTimeout = 900
        clientConnected()
        try {
            var lastRevision = -1L
            while (!connection.isClosed) {
                val state = RemotePlaybackRuntime.snapshot.value
                if (state.revision != lastRevision) {
                    lastRevision = state.revision
                    writeWebSocketText(output, stateJson(connection.inetAddress).toString())
                }
                runCatching { readWebSocketText(input) }.getOrNull()?.let(::applyCommand)
            }
        } finally {
            clientDisconnected()
        }
    }

    private fun stateJson(remoteAddress: InetAddress): JSONObject {
        val snapshot = RemotePlaybackRuntime.snapshot.value
        val playback = snapshot.playback
        val streamUrl = if (playback == null) "" else relayUrl(playback, remoteAddress)
        return JSONObject()
            .put("revision", snapshot.revision)
            .put("playing", snapshot.playing)
            .put("positionMs", snapshot.positionMs)
            .put("durationMs", snapshot.durationMs)
            .put("volume", snapshot.volume.toDouble())
            .put("muted", snapshot.muted)
            .put("playerState", snapshot.playerState)
            .put("error", snapshot.error ?: JSONObject.NULL)
            .put("streamUrl", streamUrl)
            .put("mimeType", playback?.stream?.mimeType.orEmpty())
            .put("playbackId", playback?.id.orEmpty())
            .put("title", playback?.seriesTitle.orEmpty())
            .put("episode", playback?.episode?.title.orEmpty())
            .put("season", playback?.episode?.season ?: 0)
            .put("episodeNumber", playback?.episode?.number ?: 0)
            .put("artwork", playback?.series?.coverUrl.orEmpty())
            .put("pin", pin)
    }

    private fun relayUrl(playback: ResolvedPlayback, remoteAddress: InetAddress): String {
        val key = remoteAddress.hostAddress.orEmpty()
        val cached = relayUrls[key]
        if (cached?.first == playback.id) return cached.second
        val prepared = relay.preparePlayback(playback, key)
        relayUrls[key] = playback.id to prepared.stream.url
        return prepared.stream.url
    }

    private fun applyCommand(raw: String): Boolean {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val command = when (json.optString("command").lowercase(Locale.ROOT)) {
            "play" -> RemotePlaybackCommand.Play
            "pause" -> RemotePlaybackCommand.Pause
            "toggle" -> RemotePlaybackCommand.Toggle
            "previous" -> RemotePlaybackCommand.Previous
            "next" -> RemotePlaybackCommand.Next
            "stop" -> RemotePlaybackCommand.Stop
            "seek" -> RemotePlaybackCommand.Seek(json.optLong("positionMs").coerceAtLeast(0L))
            "volume" -> RemotePlaybackCommand.Volume(json.optDouble("value", 1.0).toFloat().coerceIn(0f, 1f))
            "mute" -> RemotePlaybackCommand.Mute(json.optBoolean("muted"))
            else -> return false
        }
        return RemotePlaybackRuntime.submit(command)
    }

    private fun receiverHtml(prefilledPin: String): String {
        val initialPin = prefilledPin.filter(Char::isDigit).take(6)
        return """<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>AniWorld Local Player</title><style>
:root{color-scheme:dark;font-family:system-ui,sans-serif}body{margin:0;background:#07080b;color:#f4f4f6}.wrap{max-width:1100px;margin:auto;padding:16px}.card{background:#111319;border-radius:20px;padding:16px;margin:12px 0;box-shadow:0 8px 30px #0008}video{width:100%;max-height:68vh;background:#000;border-radius:14px}.row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}button,input{font:inherit;border-radius:999px;border:1px solid #555;background:#1b1e25;color:white;padding:11px 16px}button{cursor:pointer}button.primary{background:#7c4dff;border-color:#7c4dff}.grow{flex:1}.muted{opacity:.72;font-size:.9rem}.hidden{display:none}input[type=range]{padding:0;min-width:160px}.status{white-space:pre-wrap}</style></head>
<body><main class="wrap"><h1>AniWorld Local Player</h1><section id="pair" class="card"><div class="row"><input id="pin" inputmode="numeric" maxlength="6" value="$initialPin" placeholder="6-stellige PIN"><button class="primary" id="connect">Verbinden</button></div><p class="muted">Die PIN steht in den App-Einstellungen. WebSocket wird zuerst versucht; danach folgen SSE und Polling.</p></section>
<section id="playerCard" class="card hidden"><video id="video" playsinline controls></video><h2 id="title"></h2><p id="subtitle" class="muted"></p><div class="row"><button data-cmd="previous">Zurück</button><button data-cmd="play" class="primary">Play</button><button data-cmd="pause">Pause</button><button data-cmd="next">Weiter</button><button data-cmd="stop">Stop</button></div><div class="row"><label>Lautstärke <input id="volume" type="range" min="0" max="1" step="0.01" value="1"></label><button id="mute">Stumm</button><span id="transport" class="muted"></span></div><p id="status" class="status muted"></p></section></main>
<script>
const q=s=>document.querySelector(s), video=q('#video'), status=q('#status'), transport=q('#transport');let pin=q('#pin').value, ws=null, es=null, timer=null,lastId='',lastLocalVolume=0;
function api(path){return path+(path.includes('?')?'&':'?')+'pin='+encodeURIComponent(pin)}
async function command(command,extra={}){const body=JSON.stringify({command,...extra});if(ws&&ws.readyState===1){ws.send(body);return}await fetch(api('/api/control'),{method:'POST',headers:{'Content-Type':'application/json'},body}).catch(()=>{})}
function apply(s){q('#playerCard').classList.remove('hidden');q('#pair').classList.add('hidden');q('#title').textContent=s.title||'Keine Wiedergabe';q('#subtitle').textContent=s.episode||'';transport.textContent=(s.playing?'Wiedergabe':'Pausiert')+' · '+Math.floor((s.positionMs||0)/1000)+'s';status.textContent=s.error||'';
 if(s.playbackId&&s.streamUrl&&(lastId!==s.playbackId||video.src!==s.streamUrl)){lastId=s.playbackId;video.src=s.streamUrl;video.currentTime=(s.positionMs||0)/1000;video.play().catch(()=>{});navigator.mediaSession&&(navigator.mediaSession.metadata=new MediaMetadata({title:s.episode||s.title,artist:s.title,artwork:s.artwork?[{src:s.artwork}]:[]}));}
 if(Math.abs(video.currentTime-(s.positionMs||0)/1000)>3&&!video.seeking)video.currentTime=(s.positionMs||0)/1000;if(s.playing&&video.paused)video.play().catch(()=>{});if(!s.playing&&!video.paused)video.pause();if(Date.now()-lastLocalVolume>1200){video.volume=Math.max(0,Math.min(1,s.volume??1));video.muted=!!s.muted;q('#volume').value=video.volume}}
async function poll(){try{const r=await fetch(api('/api/state'),{cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);apply(await r.json())}catch(e){status.textContent=e.message}}
function startPolling(){clearInterval(timer);timer=setInterval(poll,1000);poll()}
function startSse(){try{es=new EventSource(api('/api/events'));es.addEventListener('state',e=>apply(JSON.parse(e.data)));es.onerror=()=>{es.close();startPolling()}}catch(e){startPolling()}}
function connect(){pin=q('#pin').value.trim();localStorage.setItem('aniworld-pin',pin);clearInterval(timer);es&&es.close();try{ws=new WebSocket('ws://'+location.host+api('/ws'));ws.onmessage=e=>apply(JSON.parse(e.data));ws.onerror=()=>{};ws.onclose=()=>startSse();setTimeout(()=>{if(!ws||ws.readyState!==1){try{ws.close()}catch(e){}startSse()}},1800)}catch(e){startSse()}}
q('#pin').value=q('#pin').value||localStorage.getItem('aniworld-pin')||'';q('#connect').onclick=connect;document.querySelectorAll('[data-cmd]').forEach(b=>b.onclick=()=>command(b.dataset.cmd));q('#volume').oninput=e=>{lastLocalVolume=Date.now();video.volume=+e.target.value;command('volume',{value:video.volume})};q('#mute').onclick=()=>{video.muted=!video.muted;command('mute',{muted:video.muted})};video.onplay=()=>command('play');video.onpause=()=>command('pause');video.onseeked=()=>command('seek',{positionMs:Math.floor(video.currentTime*1000)});
if('mediaSession'in navigator){for(const [a,c] of [['play','play'],['pause','pause'],['previoustrack','previous'],['nexttrack','next']])try{navigator.mediaSession.setActionHandler(a,()=>command(c))}catch(e){}try{navigator.mediaSession.setActionHandler('seekto',d=>command('seek',{positionMs:Math.floor(d.seekTime*1000)}))}catch(e){}}
if(q('#pin').value.length===6)connect();
</script></body></html>"""
    }

    private fun clientConnected() {
        val count = clients.incrementAndGet()
        idleShutdownJob?.cancel()
        RemotePlaybackRuntime.updateServerStatus { it.copy(connectedClients = count) }
    }

    private fun clientDisconnected() {
        val count = clients.decrementAndGet().coerceAtLeast(0)
        RemotePlaybackRuntime.updateServerStatus { it.copy(connectedClients = count) }
        if (count == 0 && RemotePlaybackRuntime.currentPlayback() == null) scheduleIdleShutdown()
    }

    private fun writeWebSocketText(output: BufferedOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        output.write(0x81)
        when {
            bytes.size < 126 -> output.write(bytes.size)
            bytes.size <= 65_535 -> {
                output.write(126)
                output.write((bytes.size shr 8) and 0xFF)
                output.write(bytes.size and 0xFF)
            }
            else -> {
                output.write(127)
                output.write(ByteBuffer.allocate(8).putLong(bytes.size.toLong()).array())
            }
        }
        output.write(bytes)
        output.flush()
    }

    private fun readWebSocketText(input: BufferedInputStream): String? {
        val first = input.read()
        if (first < 0) return null
        val second = input.read()
        if (second < 0) return null
        val opcode = first and 0x0F
        if (opcode == 8) return null
        var length = second and 0x7F
        if (length == 126) length = (input.read() shl 8) or input.read()
        if (length == 127) {
            val longLength = ByteBuffer.wrap(readExactly(input, 8)).long
            if (longLength > MAX_BODY_BYTES) return null
            length = longLength.toInt()
        }
        val masked = second and 0x80 != 0
        val mask = if (masked) readExactly(input, 4) else ByteArray(0)
        val payload = readExactly(input, length.coerceIn(0, MAX_BODY_BYTES))
        if (masked) payload.indices.forEach { index -> payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte() }
        return if (opcode == 1) payload.toString(StandardCharsets.UTF_8) else null
    }

    private fun writeHtml(socket: Socket, html: String) = writeBytes(socket, 200, "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
    private fun writeJson(socket: Socket, json: JSONObject) = writeBytes(socket, 200, "application/json; charset=utf-8", json.toString().toByteArray(StandardCharsets.UTF_8))
    private fun writeText(socket: Socket, code: Int, text: String) = writeBytes(socket, code, "text/plain; charset=utf-8", text.toByteArray(StandardCharsets.UTF_8))

    private fun writeBytes(socket: Socket, code: Int, contentType: String, body: ByteArray) {
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(
            ("HTTP/1.1 $code ${statusText(code)}\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        )
        output.write(body)
        output.flush()
    }

    private fun writeNoContent(socket: Socket) {
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(
            ("HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)
        )
        output.flush()
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        var total = 0
        while (true) {
            val line = readLine(input) ?: break
            total += line.length
            if (line.isEmpty() || total > MAX_HEADER_BYTES) break
            val split = line.indexOf(':')
            if (split > 0) headers[line.substring(0, split).trim().lowercase(Locale.ROOT)] = line.substring(split + 1).trim()
        }
        return headers
    }

    private fun readLine(input: BufferedInputStream): String? {
        val output = ByteArrayOutputStream()
        var previous = -1
        while (output.size() <= MAX_HEADER_BYTES) {
            val current = input.read()
            if (current < 0) return if (output.size() == 0) null else output.toString(StandardCharsets.UTF_8.name())
            if (previous == '\r'.code && current == '\n'.code) {
                val bytes = output.toByteArray()
                return bytes.copyOf((bytes.size - 1).coerceAtLeast(0)).toString(StandardCharsets.UTF_8)
            }
            output.write(current)
            previous = current
        }
        return null
    }

    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        val output = ByteArray(size.coerceAtLeast(0))
        var offset = 0
        while (offset < output.size) {
            val count = input.read(output, offset, output.size - offset)
            if (count <= 0) break
            offset += count
        }
        return if (offset == output.size) output else output.copyOf(offset)
    }

    private fun parseQuery(value: String): Map<String, String> = value.split('&').mapNotNull { item ->
        val key = item.substringBefore('=', "").trim()
        if (key.isBlank()) null else key to java.net.URLDecoder.decode(item.substringAfter('=', ""), StandardCharsets.UTF_8.name())
    }.toMap()

    private fun allowed(address: InetAddress?): Boolean {
        if (address == null) return false
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val raw = address.address
        return address is Inet4Address && raw.size == 4 && (raw[0].toInt() and 0xFF) == 100 && (raw[1].toInt() and 0xFF) in 64..127
    }

    private fun localPageUrl(port: Int): String {
        val address = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .sortedByDescending { candidate ->
                    val host = candidate.hostAddress.orEmpty()
                    (if (candidate.isSiteLocalAddress) 10 else 0) +
                        (if (host.endsWith(".1") || host.startsWith("192.168.43.") || host.startsWith("192.168.232.")) 30 else 0)
                }
                .firstOrNull()
        }.getOrNull()
        val host = address?.hostAddress ?: "127.0.0.1"
        return "http://$host:$port/watch/$pin"
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        else -> "Response"
    }

    private companion object {
        const val PORT_FALLBACK_COUNT = 20
        const val CLIENT_TIMEOUT_MS = 30_000
        const val MAX_HEADER_BYTES = 64 * 1024
        const val MAX_BODY_BYTES = 256 * 1024
        const val SSE_CONNECTION_MS = 25_000L
        const val IDLE_SHUTDOWN_MS = 60_000L
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
