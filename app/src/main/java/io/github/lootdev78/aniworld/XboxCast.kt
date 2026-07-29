package io.github.lootdev78.aniworld

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/**
 * Lightweight DLNA/UPnP controller used to send the currently resolved stream to an Xbox One or
 * Xbox Series S/X that exposes an AVTransport renderer on the same local network.
 *
 * Xbox consoles do not expose a Google Cast receiver. The interoperable path is DLNA/UPnP
 * "Play To". Discovery therefore uses SSDP and playback uses the AVTransport SOAP service.
 */
data class XboxCastDevice(
    val id: String,
    val friendlyName: String,
    val modelName: String,
    val manufacturer: String,
    val locationUrl: String,
    val avTransportControlUrl: String,
    val avTransportServiceType: String
) {
    val isXbox: Boolean
        get() {
            val haystack = "$friendlyName $modelName $manufacturer".lowercase(Locale.ROOT)
            return "xbox" in haystack || ("microsoft" in haystack && "media" in haystack)
        }

    val displayName: String
        get() = friendlyName.ifBlank { modelName.ifBlank { "Xbox / DLNA" } }
}

enum class XboxTransportState {
    IDLE,
    CONNECTING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR
}

data class XboxCastState(
    val devices: List<XboxCastDevice> = emptyList(),
    val discovering: Boolean = false,
    val connectedDevice: XboxCastDevice? = null,
    val transportState: XboxTransportState = XboxTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val completionEvent: Long = 0L
)

class XboxCastController(context: Context, private val relay: LocalCastRelay? = null) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(XboxCastState())
    val state: StateFlow<XboxCastState> = mutableState.asStateFlow()
    private var pollingJob: Job? = null
    private var lastObservedTransportState = XboxTransportState.IDLE

    fun discover() {
        if (mutableState.value.discovering) return
        scope.launch {
            mutableState.value = mutableState.value.copy(discovering = true, error = null)
            val result = runCatching { discoverRenderers() }
            mutableState.value = result.fold(
                onSuccess = { devices ->
                    mutableState.value.copy(
                        devices = devices.sortedWith(
                            compareByDescending<XboxCastDevice> { it.isXbox }
                                .thenBy { it.displayName.lowercase(Locale.ROOT) }
                        ),
                        discovering = false,
                        error = null
                    )
                },
                onFailure = { error ->
                    mutableState.value.copy(
                        discovering = false,
                        error = error.message ?: appContext.getString(R.string.xbox_cast_discovery_failed)
                    )
                }
            )
        }
    }

    fun discoverAt(address: String) {
        val normalized = address.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        if (normalized.isBlank()) return
        scope.launch {
            mutableState.value = mutableState.value.copy(discovering = true, error = null)
            val result = runCatching { discoverRendererAt(normalized) }
            mutableState.value = result.fold(
                onSuccess = { devices ->
                    val merged = (mutableState.value.devices + devices)
                        .distinctBy(XboxCastDevice::id)
                        .sortedWith(compareByDescending<XboxCastDevice> { it.isXbox }.thenBy { it.displayName.lowercase(Locale.ROOT) })
                    mutableState.value.copy(
                        devices = merged,
                        discovering = false,
                        error = if (devices.isEmpty()) appContext.getString(R.string.xbox_cast_manual_not_found, normalized) else null
                    )
                },
                onFailure = { error ->
                    mutableState.value.copy(
                        discovering = false,
                        error = error.message ?: appContext.getString(R.string.xbox_cast_discovery_failed)
                    )
                }
            )
        }
    }

    fun cast(device: XboxCastDevice, playback: ResolvedPlayback, startPositionMs: Long) {
        scope.launch {
            pollingJob?.cancel()
            mutableState.value = mutableState.value.copy(
                connectedDevice = device,
                transportState = XboxTransportState.CONNECTING,
                positionMs = startPositionMs.coerceAtLeast(0L),
                durationMs = playback.knownDurationMs.coerceAtLeast(0L),
                error = null
            )
            val result = runCatching {
                val receiverHost = runCatching { URI(device.locationUrl).host }.getOrNull()
                val castPlayback = relay?.preparePlayback(playback, receiverHost) ?: playback
                setTransportUri(device, castPlayback)
                sendAction(device, "Play", "<Speed>1</Speed>")
                if (startPositionMs > 1_000L) {
                    delay(350L)
                    seekInternal(device, startPositionMs)
                }
            }
            result.onSuccess {
                lastObservedTransportState = XboxTransportState.PLAYING
                mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PLAYING)
                startPolling(device)
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    connectedDevice = null,
                    transportState = XboxTransportState.ERROR,
                    error = readableError(error)
                )
            }
        }
    }

    fun play() = withConnectedDevice { device ->
        sendAction(device, "Play", "<Speed>1</Speed>")
        mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PLAYING, error = null)
    }

    fun pause() = withConnectedDevice { device ->
        sendAction(device, "Pause")
        mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PAUSED, error = null)
    }

    fun togglePlayPause() {
        if (mutableState.value.transportState == XboxTransportState.PLAYING) pause() else play()
    }

    fun seekTo(positionMs: Long) = withConnectedDevice { device ->
        seekInternal(device, positionMs)
        mutableState.value = mutableState.value.copy(positionMs = positionMs.coerceAtLeast(0L), error = null)
    }

    fun disconnect(stopRemote: Boolean = true) {
        val device = mutableState.value.connectedDevice
        scope.launch {
            pollingJob?.cancel()
            pollingJob = null
            if (stopRemote && device != null) runCatching { sendAction(device, "Stop") }
            lastObservedTransportState = XboxTransportState.IDLE
            mutableState.value = mutableState.value.copy(
                connectedDevice = null,
                transportState = XboxTransportState.IDLE,
                error = null
            )
        }
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun close() {
        pollingJob?.cancel()
        pollingJob = null
        val device = mutableState.value.connectedDevice
        if (device == null) {
            scope.cancel()
        } else {
            scope.launch {
                runCatching { sendAction(device, "Stop") }
                scope.cancel()
            }
        }
    }

    private fun withConnectedDevice(block: suspend (XboxCastDevice) -> Unit) {
        val device = mutableState.value.connectedDevice ?: return
        scope.launch {
            runCatching { block(device) }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        transportState = XboxTransportState.ERROR,
                        error = readableError(error)
                    )
                }
        }
    }

    private fun startPolling(device: XboxCastDevice) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && mutableState.value.connectedDevice?.id == device.id) {
                runCatching { queryPosition(device) }
                    .onSuccess { snapshot ->
                        val previous = lastObservedTransportState
                        lastObservedTransportState = snapshot.transportState
                        val completed = previous == XboxTransportState.PLAYING &&
                            snapshot.transportState == XboxTransportState.STOPPED &&
                            snapshot.durationMs > 0L &&
                            snapshot.positionMs >= snapshot.durationMs - 4_000L
                        mutableState.value = mutableState.value.copy(
                            positionMs = snapshot.positionMs,
                            durationMs = snapshot.durationMs.takeIf { it > 0L } ?: mutableState.value.durationMs,
                            transportState = snapshot.transportState,
                            completionEvent = if (completed) System.nanoTime() else mutableState.value.completionEvent,
                            error = null
                        )
                    }
                delay(1_000L)
            }
        }
    }

    private suspend fun discoverRenderers(): List<XboxCastDevice> = withContext(Dispatchers.IO) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("aniworld-xbox-cast")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val locations = linkedSetOf<String>()
            val endpoints = activeIpv4Endpoints()

            // Standard SSDP discovery still covers normal Wi-Fi networks.
            collectStandardSsdp(locations)

            // Android hotspot interfaces often do not forward multicast reliably. Send the same
            // M-SEARCH from every active IPv4 interface, to multicast, subnet broadcast and each
            // client address in the local /24. Responses remain unicast to our bound socket.
            endpoints.forEach { endpoint -> collectInterfaceSsdp(endpoint, locations) }

            // Some Android versions expose hotspot clients only through the ARP/neighbor table.
            // Probe those addresses directly as a final local-network fallback.
            readNeighborHosts().forEach { host -> collectUnicastSsdp(host, locations) }

            locations.mapNotNull { location -> runCatching { loadDevice(location) }.getOrNull() }
                .distinctBy(XboxCastDevice::id)
        } finally {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    private fun discoverRendererAt(host: String): List<XboxCastDevice> {
        val locations = linkedSetOf<String>()
        collectUnicastSsdp(InetAddress.getByName(host), locations, receiveWindowMs = 3_800L)
        return locations.mapNotNull { location -> runCatching { loadDevice(location) }.getOrNull() }
            .distinctBy(XboxCastDevice::id)
    }

    private data class DiscoveryEndpoint(
        val networkInterface: NetworkInterface,
        val localAddress: Inet4Address,
        val broadcastAddress: InetAddress?,
        val scanSubnet: Boolean,
        val likelyHotspot: Boolean
    )

    private fun activeIpv4Endpoints(): List<DiscoveryEndpoint> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@mapNotNull null
                    if (address.isLoopbackAddress || address.isLinkLocalAddress || !address.isSiteLocalAddress) return@mapNotNull null
                    val name = networkInterface.name.lowercase(Locale.ROOT)
                    val host = address.hostAddress.orEmpty()
                    val hotspotName = listOf("ap", "softap", "swlan", "wlan1", "wlan2", "rndis", "bt-pan").any(name::contains)
                    val hotspotRange = host.startsWith("192.168.43.") || host.startsWith("192.168.232.") ||
                        host.startsWith("192.168.137.") || host.endsWith(".1")
                    DiscoveryEndpoint(
                        networkInterface = networkInterface,
                        localAddress = address,
                        broadcastAddress = interfaceAddress.broadcast ?: calculateBroadcast(address, interfaceAddress.networkPrefixLength.toInt()),
                        scanSubnet = interfaceAddress.broadcast != null || hotspotName || name.startsWith("wlan"),
                        likelyHotspot = hotspotName || hotspotRange
                    )
                }
            }
            .distinctBy { "${it.networkInterface.name}:${it.localAddress.hostAddress}" }
    }.getOrDefault(emptyList())

    private fun collectStandardSsdp(locations: MutableSet<String>) {
        val group = InetAddress.getByName(SSDP_ADDRESS)
        MulticastSocket().use { socket ->
            socket.reuseAddress = true
            socket.soTimeout = 220
            socket.timeToLive = 4
            repeat(3) {
                SEARCH_TARGETS.forEach { target -> sendSearch(socket, group, target) }
                receiveLocations(socket, locations, 1_100L)
            }
        }
    }

    private fun collectInterfaceSsdp(endpoint: DiscoveryEndpoint, locations: MutableSet<String>) {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(endpoint.localAddress, 0))
            socket.soTimeout = 120
            val multicast = InetAddress.getByName(SSDP_ADDRESS)
            SEARCH_TARGETS.forEach { target ->
                sendSearch(socket, multicast, target)
                endpoint.broadcastAddress?.let { sendSearch(socket, it, target) }
            }

            // A unicast sweep is intentionally limited to the phone's /24. It is especially useful
            // when the phone itself is the hotspot gateway and multicast delivery is suppressed.
            if (endpoint.scanSubnet) {
                subnetHosts(endpoint.localAddress).forEach { host ->
                    SEARCH_TARGETS.forEach { target -> sendSearch(socket, host, target) }
                }
            }
            receiveLocations(socket, locations, if (endpoint.likelyHotspot) 3_400L else 2_000L)
        }
    }

    private fun collectUnicastSsdp(
        host: InetAddress,
        locations: MutableSet<String>,
        receiveWindowMs: Long = 1_500L
    ) {
        DatagramSocket().use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = 140
            SEARCH_TARGETS.forEach { target -> sendSearch(socket, host, target) }
            receiveLocations(socket, locations, receiveWindowMs)
        }
    }

    private fun sendSearch(socket: DatagramSocket, address: InetAddress, target: String) {
        runCatching {
            val request = buildSearchRequest(target).toByteArray(StandardCharsets.UTF_8)
            socket.send(DatagramPacket(request, request.size, address, SSDP_PORT))
        }
    }

    private fun receiveLocations(socket: DatagramSocket, locations: MutableSet<String>, windowMs: Long) {
        val deadline = System.currentTimeMillis() + windowMs
        while (System.currentTimeMillis() < deadline) {
            val buffer = ByteArray(16 * 1024)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                parseSsdpLocation(String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8))
                    ?.let(locations::add)
            } catch (_: java.net.SocketTimeoutException) {
                // Continue until the complete discovery window has elapsed.
            } catch (_: java.io.IOException) {
                break
            }
        }
    }

    private fun subnetHosts(localAddress: Inet4Address): Sequence<InetAddress> {
        val bytes = localAddress.address
        return (1..254).asSequence().mapNotNull { last ->
            if ((bytes[3].toInt() and 0xFF) == last) return@mapNotNull null
            runCatching { InetAddress.getByAddress(byteArrayOf(bytes[0], bytes[1], bytes[2], last.toByte())) }.getOrNull()
        }
    }

    private fun calculateBroadcast(address: Inet4Address, prefixLength: Int): InetAddress? = runCatching {
        val prefix = prefixLength.coerceIn(0, 32)
        val raw = address.address.fold(0) { accumulator, byte -> (accumulator shl 8) or (byte.toInt() and 0xFF) }
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val broadcast = raw or mask.inv()
        InetAddress.getByAddress(byteArrayOf(
            (broadcast ushr 24).toByte(),
            (broadcast ushr 16).toByte(),
            (broadcast ushr 8).toByte(),
            broadcast.toByte()
        ))
    }.getOrNull()

    private fun readNeighborHosts(): List<InetAddress> = runCatching {
        java.io.File("/proc/net/arp").takeIf { it.canRead() }?.readLines().orEmpty()
            .drop(1)
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull() }
            .mapNotNull { value -> runCatching { InetAddress.getByName(value) }.getOrNull() }
            .filter { it.isSiteLocalAddress }
            .distinctBy(InetAddress::getHostAddress)
    }.getOrDefault(emptyList())

    private fun loadDevice(location: String): XboxCastDevice? {
        val response = LocalHttp.execute(location, "GET", mapOf("Accept" to "text/xml, application/xml"))
        if (response.statusCode !in 200..299) return null
        val document = Jsoup.parse(response.bodyText(), location, Parser.xmlParser())
        val device = document.getElementsByTag("device").firstOrNull() ?: return null
        val friendlyName = device.getElementsByTag("friendlyName").firstOrNull()?.text().orEmpty()
        val modelName = device.getElementsByTag("modelName").firstOrNull()?.text().orEmpty()
        val manufacturer = device.getElementsByTag("manufacturer").firstOrNull()?.text().orEmpty()
        val udn = device.getElementsByTag("UDN").firstOrNull()?.text().orEmpty()
        val service = device.getElementsByTag("service").firstOrNull { element ->
            element.getElementsByTag("serviceType").firstOrNull()?.text()?.contains(":service:AVTransport:", true) == true
        } ?: return null
        val serviceType = service.getElementsByTag("serviceType").firstOrNull()?.text().orEmpty()
        val controlPath = service.getElementsByTag("controlURL").firstOrNull()?.text().orEmpty()
        if (serviceType.isBlank() || controlPath.isBlank()) return null
        val controlUrl = URI(location).resolve(controlPath).toString()
        val id = udn.ifBlank { "$friendlyName|$modelName|$location" }
        return XboxCastDevice(id, friendlyName, modelName, manufacturer, location, controlUrl, serviceType)
    }

    private fun setTransportUri(device: XboxCastDevice, playback: ResolvedPlayback) {
        val title = "${playback.seriesTitle} · ${playback.episode.localizedLabel(appContext)}"
        val mimeType = playback.stream.mimeType?.takeIf(String::isNotBlank) ?: inferMimeType(playback.stream.url)
        val metadata = buildDidlMetadata(title, playback.stream.url, mimeType)
        val args = "<CurrentURI>${xmlEscape(playback.stream.url)}</CurrentURI>" +
            "<CurrentURIMetaData>${xmlEscape(metadata)}</CurrentURIMetaData>"
        runCatching { sendAction(device, "SetAVTransportURI", args) }
            .recoverCatching {
                // Some Xbox/DLNA renderers reject metadata they do not understand but accept an empty field.
                sendAction(
                    device,
                    "SetAVTransportURI",
                    "<CurrentURI>${xmlEscape(playback.stream.url)}</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>"
                )
            }.getOrThrow()
    }

    private fun seekInternal(device: XboxCastDevice, positionMs: Long) {
        sendAction(
            device,
            "Seek",
            "<Unit>REL_TIME</Unit><Target>${formatDlnaTime(positionMs)}</Target>"
        )
    }

    private fun queryPosition(device: XboxCastDevice): RemoteSnapshot {
        val positionResponse = sendAction(device, "GetPositionInfo")
        val positionDocument = Jsoup.parse(positionResponse, "", Parser.xmlParser())
        val relative = positionDocument.getElementsByTag("RelTime").firstOrNull()?.text().orEmpty()
        val duration = positionDocument.getElementsByTag("TrackDuration").firstOrNull()?.text().orEmpty()
        val transportResponse = sendAction(device, "GetTransportInfo")
        val transportDocument = Jsoup.parse(transportResponse, "", Parser.xmlParser())
        val rawState = transportDocument.getElementsByTag("CurrentTransportState").firstOrNull()?.text().orEmpty()
        return RemoteSnapshot(
            positionMs = parseDlnaTime(relative),
            durationMs = parseDlnaTime(duration),
            transportState = when (rawState.uppercase(Locale.ROOT)) {
                "PLAYING" -> XboxTransportState.PLAYING
                "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> XboxTransportState.PAUSED
                "STOPPED", "NO_MEDIA_PRESENT" -> XboxTransportState.STOPPED
                "TRANSITIONING" -> XboxTransportState.CONNECTING
                else -> mutableState.value.transportState
            }
        )
    }

    private fun sendAction(device: XboxCastDevice, action: String, arguments: String = ""): String {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="${xmlEscape(device.avTransportServiceType)}">
                  <InstanceID>0</InstanceID>$arguments
                </u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val response = LocalHttp.execute(
            device.avTransportControlUrl,
            "POST",
            mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPACTION" to "\"${device.avTransportServiceType}#$action\""
            ),
            body.toByteArray(StandardCharsets.UTF_8)
        )
        if (response.statusCode !in 200..299) {
            val fault = Jsoup.parse(response.bodyText(), "", Parser.xmlParser())
                .getElementsByTag("errorDescription").firstOrNull()?.text()
                .orEmpty()
            error(fault.ifBlank { "DLNA $action: HTTP ${response.statusCode}" })
        }
        return response.bodyText()
    }

    private fun readableError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            "cleartext" in message.lowercase(Locale.ROOT) -> appContext.getString(R.string.xbox_cast_local_network_failed)
            message.isNotBlank() -> message
            else -> appContext.getString(R.string.xbox_cast_failed)
        }
    }

    private data class RemoteSnapshot(
        val positionMs: Long,
        val durationMs: Long,
        val transportState: XboxTransportState
    )

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "upnp:rootdevice",
            "ssdp:all"
        )

        fun buildSearchRequest(target: String): String = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $target\r\n")
            append("USER-AGENT: Android UPnP/1.0 AniWorld/1.0\r\n\r\n")
        }

        fun parseSsdpLocation(response: String): String? = response.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("location:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

        fun buildDidlMetadata(title: String, url: String, mimeType: String): String =
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="0" restricted="1"><dc:title>${xmlEscape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:${xmlEscape(mimeType)}:*">${xmlEscape(url)}</res></item></DIDL-Lite>"""

        fun inferMimeType(url: String): String = when {
            ".m3u8" in url.lowercase(Locale.ROOT) -> "application/vnd.apple.mpegurl"
            ".mpd" in url.lowercase(Locale.ROOT) -> "application/dash+xml"
            ".mkv" in url.lowercase(Locale.ROOT) -> "video/x-matroska"
            ".webm" in url.lowercase(Locale.ROOT) -> "video/webm"
            else -> "video/mp4"
        }

        fun xmlEscape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun formatDlnaTime(milliseconds: Long): String {
            val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        }

        fun parseDlnaTime(value: String): Long {
            val clean = value.substringBefore('.').trim()
            val parts = clean.split(':')
            if (parts.size != 3) return 0L
            val hours = parts[0].toLongOrNull() ?: return 0L
            val minutes = parts[1].toLongOrNull() ?: return 0L
            val seconds = parts[2].toLongOrNull() ?: return 0L
            return ((hours * 3_600L) + (minutes * 60L) + seconds) * 1_000L
        }
    }
}

/**
 * Tiny local-network HTTP client. UPnP renderers commonly expose plain HTTP endpoints on private
 * IP addresses. Using a raw socket here keeps the app-wide HTTPS-only policy intact instead of
 * enabling cleartext traffic for every remote host.
 */
private object LocalHttp {
    data class Response(val statusCode: Int, val headers: Map<String, String>, val body: ByteArray) {
        fun bodyText(): String = body.toString(StandardCharsets.UTF_8)
    }

    fun execute(
        url: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
        redirectCount: Int = 0
    ): Response {
        require(redirectCount <= 3) { "Zu viele lokale Weiterleitungen" }
        val uri = URI(url)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "Ungültige DLNA-Adresse" }
        val secure = uri.scheme.equals("https", true)
        val host = uri.host ?: error("Ungültiger lokaler Host")
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val socket = if (secure) {
            (SSLSocketFactory.getDefault().createSocket() as Socket)
        } else {
            Socket()
        }
        socket.use { connection ->
            connection.connect(InetSocketAddress(host, port), 6_000)
            connection.soTimeout = 8_000
            val output = BufferedOutputStream(connection.getOutputStream())
            val path = buildString {
                append(uri.rawPath?.takeIf(String::isNotBlank) ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }
            val requestHeaders = linkedMapOf(
                "Host" to if (uri.port > 0) "$host:$port" else host,
                "Connection" to "close",
                "Accept-Encoding" to "identity",
                "User-Agent" to "AniWorld Android DLNA/1.0"
            ).apply {
                putAll(headers)
                if (body.isNotEmpty()) put("Content-Length", body.size.toString())
            }
            output.write("$method $path HTTP/1.1\r\n".toByteArray(StandardCharsets.US_ASCII))
            requestHeaders.forEach { (name, value) ->
                output.write("$name: $value\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
            if (body.isNotEmpty()) output.write(body)
            output.flush()

            val input = BufferedInputStream(connection.getInputStream())
            val statusLine = readLine(input) ?: error("Leere DLNA-Antwort")
            val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: error("Ungültige DLNA-Antwort")
            val responseHeaders = linkedMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) responseHeaders[line.substring(0, separator).trim().lowercase(Locale.ROOT)] = line.substring(separator + 1).trim()
            }
            val responseBody = when {
                responseHeaders["transfer-encoding"]?.contains("chunked", true) == true -> readChunked(input)
                responseHeaders["content-length"]?.toIntOrNull() != null -> readExactly(input, responseHeaders.getValue("content-length").toInt())
                else -> input.readBytes()
            }
            if (statusCode in 300..399) {
                val target = responseHeaders["location"]?.let(uri::resolve)?.toString()
                if (!target.isNullOrBlank()) return execute(target, method, headers, body, redirectCount + 1)
            }
            return Response(statusCode, responseHeaders, responseBody)
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current == -1) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.UTF_8)
            if (previous == '\r'.code && current == '\n'.code) {
                if (bytes.isNotEmpty()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().toString(StandardCharsets.UTF_8)
            }
            bytes += current.toByte()
            previous = current
        }
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val result = ByteArray(length.coerceAtLeast(0))
        var offset = 0
        while (offset < result.size) {
            val count = input.read(result, offset, result.size - offset)
            if (count <= 0) break
            offset += count
        }
        return if (offset == result.size) result else result.copyOf(offset)
    }

    private fun readChunked(input: BufferedInputStream): ByteArray {
        val chunks = ArrayList<Byte>()
        while (true) {
            val sizeLine = readLine(input)?.substringBefore(';')?.trim() ?: break
            val size = sizeLine.toIntOrNull(16) ?: break
            if (size == 0) {
                while (!readLine(input).isNullOrEmpty()) Unit
                break
            }
            readExactly(input, size).forEach(chunks::add)
            readLine(input)
        }
        return chunks.toByteArray()
    }
}
