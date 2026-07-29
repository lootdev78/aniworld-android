package io.github.lootdev78.aniworld

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Optional Samsung Smart View SDK adapter.
 *
 * The legacy SDK is loaded reflectively so the normal app remains buildable and usable when the
 * Samsung JAR/AAR is not present. Put the official SDK file in app/libs to activate this backend.
 * No browser receiver is involved: playback is sent to Samsung's Default Media Player (DMP).
 */
data class SamsungSmartViewDevice(
    val id: String,
    val displayName: String,
    val serviceUri: String,
    val version: String = "",
    val standby: Boolean = false,
    val dmpSupported: Boolean? = null
)

data class SamsungSmartViewState(
    val sdkAvailable: Boolean = false,
    val devices: List<SamsungSmartViewDevice> = emptyList(),
    val discovering: Boolean = false,
    val connectedDevice: SamsungSmartViewDevice? = null,
    val transportState: XboxTransportState = XboxTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int? = null,
    val muted: Boolean = false,
    val error: String? = null,
    val completionEvent: Long = 0L
)

class SamsungSmartViewController(
    context: Context,
    private val relay: LocalCastRelay? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceHandles = ConcurrentHashMap<String, Any>()
    private val mutableState = MutableStateFlow(
        SamsungSmartViewState(sdkAvailable = isSdkInstalled())
    )
    val state: StateFlow<SamsungSmartViewState> = mutableState.asStateFlow()

    private var searchHandle: Any? = null
    private var searchFoundListener: Any? = null
    private var searchLostListener: Any? = null
    private var discoveryTimeout: Job? = null
    private var playerHandle: Any? = null
    private var playerListener: Any? = null
    private var playResultListener: Any? = null

    fun discover() {
        if (!mutableState.value.sdkAvailable) {
            // The SDK is optional. Keep discovery quiet and let the picker/settings explain how
            // to activate the native Samsung backend instead of surfacing a playback error.
            mutableState.value = mutableState.value.copy(discovering = false, error = null)
            return
        }
        if (mutableState.value.discovering) return

        scope.launch {
            runCatching {
                stopDiscoveryInternal()
                val serviceClass = sdkClass(SERVICE_CLASS)
                val search = serviceClass.getMethod("search", Context::class.java)
                    .invoke(null, appContext)
                    ?: error("Smart View Search konnte nicht erstellt werden")
                searchHandle = search
                installSearchListeners(search)
                mutableState.value = mutableState.value.copy(discovering = true, error = null)

                // Samsung documents start() as the normal discovery entry point. The app-owned
                // timeout below keeps discovery bounded without invoking multiple SDK overloads.
                val started = invokeRequired(search, "start")
                if (started is Boolean && !started) {
                    error("Smart View Suche konnte nicht gestartet werden")
                }
                discoveryTimeout?.cancel()
                discoveryTimeout = scope.launch {
                    delay(DISCOVERY_WINDOW_MS)
                    stopDiscoveryInternal()
                    mutableState.value = mutableState.value.copy(discovering = false)
                }
            }.onFailure(::publishError)
        }
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun cast(device: SamsungSmartViewDevice, playback: ResolvedPlayback, startPositionMs: Long) {
        val service = serviceHandles[device.id]
        if (service == null) {
            mutableState.value = mutableState.value.copy(error = appContext.getString(R.string.smartview_device_lost))
            return
        }
        scope.launch {
            runCatching {
                stopDiscoveryInternal()
                disconnectPlayerInternal(stopRemote = true)
                mutableState.value = mutableState.value.copy(
                    connectedDevice = device,
                    transportState = XboxTransportState.CONNECTING,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = playback.knownDurationMs.coerceAtLeast(0L),
                    error = null
                )

                val receiverHost = runCatching { URI(device.serviceUri).host }.getOrNull()
                val prepared = relay?.preparePlayback(playback, receiverHost) ?: playback
                val player = invokeRequired(service, "createVideoPlayer", SMARTVIEW_APP_NAME)
                playerHandle = player
                installPlayerListeners(player)
                startRemotePlayback(player, prepared, startPositionMs)
            }.onFailure(::publishError)
        }
    }

    fun play() = playerCommand("play", XboxTransportState.PLAYING)
    fun pause() = playerCommand("pause", XboxTransportState.PAUSED)

    fun togglePlayPause() {
        if (mutableState.value.transportState == XboxTransportState.PLAYING) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val player = playerHandle ?: return
        scope.launch {
            runCatching {
                val safe = positionMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                invokeRequired(player, "seekTo", safe, TimeUnit.MILLISECONDS)
                mutableState.value = mutableState.value.copy(positionMs = safe.toLong(), error = null)
            }.onFailure(::publishError)
        }
    }

    fun setVolume(volume: Int) {
        val player = playerHandle ?: return
        scope.launch {
            runCatching {
                val safe = volume.coerceIn(0, 100)
                invokeRequired(player, "setVolume", safe)
                mutableState.value = mutableState.value.copy(volume = safe, muted = safe == 0, error = null)
            }.onFailure(::publishError)
        }
    }

    fun setMuted(muted: Boolean) {
        val player = playerHandle ?: return
        scope.launch {
            runCatching {
                invokeRequired(player, if (muted) "mute" else "unMute")
                mutableState.value = mutableState.value.copy(muted = muted, error = null)
            }.onFailure(::publishError)
        }
    }

    fun stop() {
        val player = playerHandle ?: return
        scope.launch {
            runCatching { invokeRequired(player, "stop") }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        transportState = XboxTransportState.STOPPED,
                        error = null
                    )
                }
                .onFailure(::publishError)
        }
    }

    fun disconnect(stopRemote: Boolean = true) {
        scope.launch {
            disconnectPlayerInternal(stopRemote)
            mutableState.value = mutableState.value.copy(
                connectedDevice = null,
                transportState = XboxTransportState.IDLE,
                error = null
            )
        }
    }

    fun close() {
        discoveryTimeout?.cancel()
        runCatching { stopDiscoveryInternal() }
        runCatching { disconnectPlayerInternal(stopRemote = false) }
        serviceHandles.clear()
        scope.cancel()
    }

    private fun installSearchListeners(search: Any) {
        val foundInterface = sdkClass("com.samsung.multiscreen.Search\$OnServiceFoundListener")
        val lostInterface = sdkClass("com.samsung.multiscreen.Search\$OnServiceLostListener")
        searchFoundListener = listenerProxy(foundInterface) { name, args ->
            if (name == "onFound") args.firstOrNull()?.let(::serviceFound)
        }
        searchLostListener = listenerProxy(lostInterface) { name, args ->
            if (name == "onLost") args.firstOrNull()?.let(::serviceLost)
        }
        invokeRequired(search, "setOnServiceFoundListener", searchFoundListener!!)
        invokeRequired(search, "setOnServiceLostListener", searchLostListener!!)
    }

    private fun serviceFound(service: Any) {
        val type = invokeCompatible(service, "getType")?.toString().orEmpty()
        if (type.isNotBlank() && !type.contains("tv", ignoreCase = true)) return
        val uri = invokeCompatible(service, "getUri")?.toString().orEmpty()
        val id = invokeCompatible(service, "getId")?.toString().orEmpty().ifBlank { uri }
        if (id.isBlank()) return
        val name = invokeCompatible(service, "getName")?.toString().orEmpty().ifBlank { "Samsung TV" }
        val version = invokeCompatible(service, "getVersion")?.toString().orEmpty()
        val standby = invokeCompatible(service, "getIsStandbyService") as? Boolean ?: false
        val support = invokeCompatible(service, "getIsSupport") as? Map<*, *>
        val dmp = support?.entries?.firstOrNull { (key, _) ->
            key?.toString()?.lowercase(Locale.ROOT)?.contains("dmp") == true
        }?.value as? Boolean
        val device = SamsungSmartViewDevice(id, name, uri, version, standby, dmp)
        serviceHandles[id] = service
        mutableState.value = mutableState.value.copy(
            devices = (mutableState.value.devices.filterNot { it.id == id } + device)
                .sortedWith(compareBy<SamsungSmartViewDevice> { it.standby }.thenBy { it.displayName.lowercase(Locale.ROOT) }),
            error = null
        )
        probeDmpSupport(service, id)
    }

    private fun serviceLost(service: Any) {
        val uri = invokeCompatible(service, "getUri")?.toString().orEmpty()
        val id = invokeCompatible(service, "getId")?.toString().orEmpty().ifBlank { uri }
        if (id.isBlank()) return
        serviceHandles.remove(id)
        mutableState.value = mutableState.value.copy(devices = mutableState.value.devices.filterNot { it.id == id })
    }

    private fun probeDmpSupport(service: Any, id: String) {
        val method = service.javaClass.methods.firstOrNull { it.name == "isDMPSupported" && it.parameterTypes.size == 1 } ?: return
        val resultInterface = method.parameterTypes[0]
        val callback = listenerProxy(resultInterface) { name, args ->
            if (name == "onSuccess") {
                val supported = args.firstOrNull() as? Boolean ?: return@listenerProxy
                mutableState.value = mutableState.value.copy(
                    devices = mutableState.value.devices.map { if (it.id == id) it.copy(dmpSupported = supported) else it }
                )
            }
        }
        runCatching { method.invoke(service, callback) }
    }

    private fun installPlayerListeners(player: Any) {
        val listenerInterface = sdkClass("com.samsung.multiscreen.VideoPlayer\$OnVideoPlayerListener")
        playerListener = listenerProxy(listenerInterface) { name, args ->
            when (name) {
                "onBufferingStart" -> mutableState.value = mutableState.value.copy(transportState = XboxTransportState.CONNECTING)
                "onBufferingComplete", "onPlay", "onPlayerInitialized" ->
                    mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PLAYING, error = null)
                "onPause" -> mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PAUSED)
                "onStop" -> mutableState.value = mutableState.value.copy(transportState = XboxTransportState.STOPPED)
                "onStreamingStarted" -> {
                    val duration = (args.firstOrNull() as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                    mutableState.value = mutableState.value.copy(
                        durationMs = duration,
                        transportState = XboxTransportState.PLAYING,
                        error = null
                    )
                }
                "onCurrentPlayTime" -> {
                    val position = (args.firstOrNull() as? Number)?.toLong()?.coerceAtLeast(0L) ?: return@listenerProxy
                    mutableState.value = mutableState.value.copy(positionMs = position)
                }
                "onStreamCompleted" -> mutableState.value = mutableState.value.copy(
                    transportState = XboxTransportState.STOPPED,
                    completionEvent = System.currentTimeMillis()
                )
                "onVolumeChange" -> {
                    val volume = (args.firstOrNull() as? Number)?.toInt()?.coerceIn(0, 100)
                    mutableState.value = mutableState.value.copy(volume = volume)
                }
                "onMute" -> mutableState.value = mutableState.value.copy(muted = true)
                "onUnMute" -> mutableState.value = mutableState.value.copy(muted = false)
                "onControlStatus" -> {
                    val volume = (args.getOrNull(0) as? Number)?.toInt()?.coerceIn(0, 100)
                    val muted = args.getOrNull(1) as? Boolean ?: mutableState.value.muted
                    mutableState.value = mutableState.value.copy(volume = volume, muted = muted)
                }
                "onError" -> publishError(IllegalStateException(samsungErrorText(args.firstOrNull())))
            }
        }
        invokeRequired(player, "addOnMessageListener", playerListener!!)
    }

    private suspend fun startRemotePlayback(player: Any, playback: ResolvedPlayback, startPositionMs: Long) {
        val streamUri = Uri.parse(playback.stream.url)
        val title = buildString {
            append(playback.seriesTitle)
            if (playback.episode.title.isNotBlank()) append(" · ").append(playback.episode.title)
        }
        val thumbnail = playback.series.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
        val playMethod = player.javaClass.methods
            .filter { it.name == "playContent" }
            .sortedByDescending { it.parameterCount }
            .firstOrNull { it.parameterTypes.size == 4 || it.parameterTypes.size == 2 }
            ?: error("Smart View VideoPlayer.playContent fehlt")
        val resultInterface = playMethod.parameterTypes.last()
        playResultListener = listenerProxy(resultInterface) { name, args ->
            when (name) {
                "onSuccess" -> mutableState.value = mutableState.value.copy(
                    transportState = XboxTransportState.PLAYING,
                    error = null
                )
                "onError" -> publishError(IllegalStateException(samsungErrorText(args.firstOrNull())))
            }
        }
        val callArgs = when (playMethod.parameterTypes.size) {
            4 -> arrayOf(streamUri, title, thumbnail, playResultListener)
            2 -> arrayOf(streamUri, playResultListener)
            else -> error("Nicht unterstützte Smart View playContent-Signatur")
        }
        playMethod.invoke(player, *callArgs)
        if (startPositionMs > 1_000L) {
            delay(900L)
            seekTo(startPositionMs)
        }
        runCatching { invokeCompatible(player, "getControlStatus") }
    }

    private fun playerCommand(name: String, resultingState: XboxTransportState) {
        val player = playerHandle ?: return
        scope.launch {
            runCatching {
                invokeRequired(player, name)
                mutableState.value = mutableState.value.copy(transportState = resultingState, error = null)
            }.onFailure(::publishError)
        }
    }

    private fun stopDiscoveryInternal() {
        discoveryTimeout?.cancel()
        discoveryTimeout = null
        searchHandle?.let { runCatching { invokeCompatible(it, "stop") } }
        searchHandle = null
        searchFoundListener = null
        searchLostListener = null
        if (mutableState.value.discovering) {
            mutableState.value = mutableState.value.copy(discovering = false)
        }
    }

    private fun disconnectPlayerInternal(stopRemote: Boolean) {
        val player = playerHandle ?: return
        if (stopRemote) runCatching { invokeCompatible(player, "stop") }
        val disconnectWithFlag = player.javaClass.methods.firstOrNull {
            it.name == "disconnect" && it.parameterTypes.size == 2 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        if (disconnectWithFlag != null) {
            val resultInterface = disconnectWithFlag.parameterTypes[1]
            val callback = listenerProxy(resultInterface) { _, _ -> }
            runCatching { disconnectWithFlag.invoke(player, stopRemote, callback) }
        } else {
            runCatching { invokeCompatible(player, "disconnect") }
        }
        playerHandle = null
        playerListener = null
        playResultListener = null
    }

    private fun publishError(error: Throwable) {
        val cause = error.cause ?: error
        val message = cause.message?.takeIf { it.isNotBlank() }
            ?: cause.javaClass.simpleName
        AppLogger.warn("SmartView", "Samsung Smart View Fehler", message)
        mutableState.value = mutableState.value.copy(
            discovering = false,
            transportState = XboxTransportState.ERROR,
            error = appContext.getString(R.string.smartview_cast_failed, message)
        )
    }

    private fun samsungErrorText(error: Any?): String {
        if (error == null) return "Unbekannter Smart View Fehler"
        val message = invokeCompatible(error, "getMessage")?.toString().orEmpty()
        val code = invokeCompatible(error, "getCode")?.toString().orEmpty()
        return listOf(code, message).filter(String::isNotBlank).joinToString(": ").ifBlank { error.toString() }
    }

    private fun listenerProxy(type: Class<*>, callback: (String, List<Any?>) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, rawArgs ->
            when (method.name) {
                "toString" -> "AniWorldSmartViewProxy(${type.simpleName})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === rawArgs?.firstOrNull()
                else -> {
                    callback(method.name, rawArgs?.toList().orEmpty())
                    defaultValue(method.returnType)
                }
            }
        }

    private fun invokeRequired(target: Any, name: String, vararg args: Any?): Any {
        val method = findCompatibleMethod(target, name, args)
            ?: error("${target.javaClass.simpleName}.$name fehlt")
        return method.invoke(target, *args) ?: Unit
    }

    private fun invokeCompatible(target: Any, name: String, vararg args: Any?): Any? =
        findCompatibleMethod(target, name, args)?.invoke(target, *args)

    private fun findCompatibleMethod(target: Any, name: String, args: Array<out Any?>): Method? =
        target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.zip(args).all { (parameter, argument) -> compatible(parameter, argument) }
        }

    private fun compatible(parameter: Class<*>, argument: Any?): Boolean {
        if (argument == null) return !parameter.isPrimitive
        val boxed = when (parameter) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            else -> parameter
        }
        return boxed.isInstance(argument)
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Void.TYPE -> null
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun sdkClass(name: String): Class<*> = Class.forName(name, true, appContext.classLoader)

    private fun isSdkInstalled(): Boolean = isSdkAvailable(appContext)

    companion object {
        fun isSdkAvailable(context: Context): Boolean = runCatching {
            Class.forName(SERVICE_CLASS, false, context.applicationContext.classLoader)
            Class.forName("com.samsung.multiscreen.VideoPlayer", false, context.applicationContext.classLoader)
        }.isSuccess

        private const val SERVICE_CLASS = "com.samsung.multiscreen.Service"
        private const val SMARTVIEW_APP_NAME = "AniWorld"
        private const val DISCOVERY_WINDOW_MS = 15_000L
    }
}
