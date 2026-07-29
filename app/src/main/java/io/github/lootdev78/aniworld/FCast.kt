package io.github.lootdev78.aniworld

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale

/** Open FCast receiver found on the local Wi-Fi or on an Android phone hotspot. */
data class FCastDevice(
    val host: String,
    val port: Int = DEFAULT_FCAST_PORT,
    val name: String = "FCast"
) {
    val id: String get() = "$host:$port"
    val displayName: String get() = if (name.isBlank() || name == "FCast") "FCast · $host" else name
}

data class FCastState(
    val devices: List<FCastDevice> = emptyList(),
    val discovering: Boolean = false,
    val connectedDevice: FCastDevice? = null,
    val transportState: XboxTransportState = XboxTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val completionEvent: Long = 0L
)

class FCastController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(FCastState())
    val state: StateFlow<FCastState> = mutableState.asStateFlow()

    private var socket: Socket? = null
    private var output: BufferedOutputStream? = null
    private var readerJob: Job? = null
    private val outputLock = Any()
    private var previousState = XboxTransportState.IDLE

    fun discover() {
        if (mutableState.value.discovering) return
        scope.launch {
            mutableState.value = mutableState.value.copy(discovering = true, error = null)
            runCatching { discoverReceivers() }
                .onSuccess { devices ->
                    mutableState.value = mutableState.value.copy(
                        devices = devices.sortedBy { it.displayName.lowercase(Locale.ROOT) },
                        discovering = false,
                        error = null
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        discovering = false,
                        error = error.message ?: appContext.getString(R.string.fcast_discovery_failed)
                    )
                }
        }
    }

    fun discoverAt(address: String) {
        val host = address.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        if (host.isBlank()) return
        scope.launch {
            mutableState.value = mutableState.value.copy(discovering = true, error = null)
            val device = probe(host)
            val existing = mutableState.value.devices
            mutableState.value = if (device != null) {
                mutableState.value.copy(
                    devices = (existing + device).distinctBy(FCastDevice::id),
                    discovering = false,
                    error = null
                )
            } else {
                mutableState.value.copy(
                    discovering = false,
                    error = appContext.getString(R.string.fcast_manual_not_found, host)
                )
            }
        }
    }

    fun cast(device: FCastDevice, playback: ResolvedPlayback, startPositionMs: Long) {
        scope.launch {
            closeSocket(sendStop = false)
            mutableState.value = mutableState.value.copy(
                connectedDevice = device,
                transportState = XboxTransportState.CONNECTING,
                positionMs = startPositionMs.coerceAtLeast(0L),
                durationMs = playback.knownDurationMs.coerceAtLeast(0L),
                error = null
            )
            runCatching {
                val connection = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
                    soTimeout = 0
                }
                socket = connection
                output = BufferedOutputStream(connection.getOutputStream())
                startReader(connection)
                val playMessage = JSONObject()
                    .put("container", playback.stream.mimeType ?: inferMimeType(playback.stream.url))
                    .put("url", playback.stream.url)
                    .put("time", startPositionMs.coerceAtLeast(0L) / 1000.0)
                    .put(
                        "metadata",
                        JSONObject()
                            .put("title", playback.episode.localizedDisplayTitle(appContext))
                            .put("seriesName", playback.seriesTitle)
                    )
                if (playback.stream.headers.isNotEmpty()) {
                    playMessage.put("headers", JSONObject(playback.stream.headers))
                }
                sendPacket(OPCODE_PLAY, playMessage)
                previousState = XboxTransportState.PLAYING
                mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PLAYING, error = null)
            }.onFailure { error ->
                closeSocket(sendStop = false)
                mutableState.value = mutableState.value.copy(
                    connectedDevice = null,
                    transportState = XboxTransportState.ERROR,
                    error = error.message ?: appContext.getString(R.string.fcast_connect_failed)
                )
            }
        }
    }

    fun play() = command(OPCODE_RESUME, null, XboxTransportState.PLAYING)
    fun pause() = command(OPCODE_PAUSE, null, XboxTransportState.PAUSED)
    fun togglePlayPause() {
        if (mutableState.value.transportState == XboxTransportState.PLAYING) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        command(
            OPCODE_SEEK,
            JSONObject().put("time", positionMs.coerceAtLeast(0L) / 1000.0),
            mutableState.value.transportState
        )
        mutableState.value = mutableState.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun disconnect() {
        scope.launch {
            closeSocket(sendStop = true)
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
        runCatching { socket?.close() }
        readerJob?.cancel()
        scope.cancel()
    }

    private fun command(opcode: Int, body: JSONObject?, resultingState: XboxTransportState) {
        scope.launch {
            runCatching { sendPacket(opcode, body) }
                .onSuccess {
                    previousState = resultingState
                    mutableState.value = mutableState.value.copy(transportState = resultingState, error = null)
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        transportState = XboxTransportState.ERROR,
                        error = error.message ?: appContext.getString(R.string.fcast_command_failed)
                    )
                }
        }
    }

    private fun startReader(connection: Socket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            val input = BufferedInputStream(connection.getInputStream())
            try {
                while (!connection.isClosed) {
                    val lengthBytes = readExactly(input, 4)
                    val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    if (length !in 1..MAX_PACKET_SIZE) throw IllegalStateException("Invalid FCast packet size: $length")
                    val packet = readExactly(input, length)
                    val opcode = packet[0].toInt() and 0xFF
                    val body = if (length > 1) String(packet, 1, length - 1, StandardCharsets.UTF_8) else ""
                    when (opcode) {
                        OPCODE_PLAYBACK_UPDATE -> handlePlaybackUpdate(body)
                        OPCODE_PLAYBACK_ERROR -> {
                            val message = runCatching { JSONObject(body).optString("message") }.getOrDefault(body)
                            mutableState.value = mutableState.value.copy(
                                transportState = XboxTransportState.ERROR,
                                error = message.ifBlank { appContext.getString(R.string.fcast_playback_failed) }
                            )
                        }
                        OPCODE_PING -> sendPacket(OPCODE_PONG, null)
                    }
                }
            } catch (error: Exception) {
                if (mutableState.value.connectedDevice != null && !connection.isClosed) {
                    mutableState.value = mutableState.value.copy(
                        transportState = XboxTransportState.ERROR,
                        error = error.message ?: appContext.getString(R.string.fcast_connection_lost)
                    )
                }
            }
        }
    }

    private fun handlePlaybackUpdate(body: String) {
        val json = JSONObject(body)
        val newState = when (json.optInt("state", -1)) {
            0 -> XboxTransportState.STOPPED
            1 -> XboxTransportState.PLAYING
            2 -> XboxTransportState.PAUSED
            else -> mutableState.value.transportState
        }
        val positionMs = (json.optDouble("time", mutableState.value.positionMs / 1000.0) * 1000.0).toLong().coerceAtLeast(0L)
        val durationSeconds = json.optDouble("duration", Double.NaN)
        val durationMs = if (durationSeconds.isFinite() && durationSeconds > 0.0) {
            (durationSeconds * 1000.0).toLong()
        } else mutableState.value.durationMs
        val completed = previousState == XboxTransportState.PLAYING && newState == XboxTransportState.STOPPED &&
            durationMs > 0L && positionMs >= durationMs - 4_000L
        previousState = newState
        mutableState.value = mutableState.value.copy(
            transportState = newState,
            positionMs = positionMs,
            durationMs = durationMs,
            completionEvent = if (completed) System.nanoTime() else mutableState.value.completionEvent,
            error = null
        )
    }

    private fun sendPacket(opcode: Int, body: JSONObject?) {
        val stream = output ?: throw IllegalStateException(appContext.getString(R.string.fcast_not_connected))
        val bodyBytes = body?.toString()?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val length = 1 + bodyBytes.size
        val header = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(length)
            .put(opcode.toByte())
            .array()
        synchronized(outputLock) {
            stream.write(header)
            if (bodyBytes.isNotEmpty()) stream.write(bodyBytes)
            stream.flush()
        }
    }

    private suspend fun closeSocket(sendStop: Boolean) = withContext(Dispatchers.IO) {
        if (sendStop && output != null) runCatching { sendPacket(OPCODE_STOP, null) }
        readerJob?.cancel()
        readerJob = null
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
        previousState = XboxTransportState.IDLE
    }

    /**
     * FCast normally advertises with mDNS. The direct TCP scan is intentional: Android hotspot
     * implementations often isolate or drop multicast, while unicast to connected clients still
     * works. Only private active /24 networks and already known neighbor addresses are probed.
     */
    private suspend fun discoverReceivers(): List<FCastDevice> = coroutineScope {
        val localAddresses = activeLocalIpv4Addresses()
        val candidates = linkedSetOf<String>()
        localAddresses.forEach { address ->
            val bytes = address.address
            if (bytes.size == 4) {
                val prefix = "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}."
                for (last in 1..254) {
                    val host = prefix + last
                    if (host != address.hostAddress) candidates += host
                }
            }
        }
        readNeighborHosts().forEach { address ->
            address.hostAddress?.let(candidates::add)
        }
        val gate = Semaphore(MAX_PARALLEL_PROBES)
        candidates.map { host ->
            async(Dispatchers.IO) { gate.withPermit { probe(host) } }
        }.awaitAll().filterNotNull().distinctBy(FCastDevice::id)
    }

    private fun probe(host: String): FCastDevice? = runCatching {
        Socket().use { test ->
            test.connect(InetSocketAddress(host, DEFAULT_FCAST_PORT), PROBE_TIMEOUT_MS)
        }
        FCastDevice(host = host)
    }.getOrNull()

    private fun activeLocalIpv4Addresses(): List<Inet4Address> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network -> Collections.list(network.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            .distinctBy(InetAddress::getHostAddress)
    }.getOrDefault(emptyList())

    private fun readNeighborHosts(): List<InetAddress> = runCatching {
        java.io.File("/proc/net/arp").takeIf { it.canRead() }?.readLines().orEmpty()
            .drop(1)
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).firstOrNull() }
            .filter { host -> host.count { it == '.' } == 3 }
            .mapNotNull { host -> runCatching { InetAddress.getByName(host) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun readExactly(input: BufferedInputStream, size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(result, offset, size - offset)
            if (read < 0) throw EOFException("FCast connection closed")
            offset += read
        }
        return result
    }

    private fun inferMimeType(url: String): String = when {
        ".m3u8" in url.lowercase() -> "application/x-mpegURL"
        ".mpd" in url.lowercase() -> "application/dash+xml"
        ".webm" in url.lowercase() -> "video/webm"
        else -> "video/mp4"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_500
        const val PROBE_TIMEOUT_MS = 150
        const val MAX_PARALLEL_PROBES = 32
        const val MAX_PACKET_SIZE = 2 * 1024 * 1024
        const val OPCODE_PLAY = 1
        const val OPCODE_PAUSE = 2
        const val OPCODE_RESUME = 3
        const val OPCODE_STOP = 4
        const val OPCODE_SEEK = 5
        const val OPCODE_PLAYBACK_UPDATE = 6
        const val OPCODE_PLAYBACK_ERROR = 9
        const val OPCODE_PING = 12
        const val OPCODE_PONG = 13
    }
}

const val DEFAULT_FCAST_PORT = 46899
