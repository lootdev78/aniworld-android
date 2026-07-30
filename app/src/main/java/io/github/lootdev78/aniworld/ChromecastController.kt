package io.github.lootdev78.aniworld

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.CastMediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** State bridge between the Compose player and the official Google Cast framework. */
data class ChromecastRoute(val id: String, val name: String)

data class ChromecastState(
    val available: Boolean = false,
    val devices: List<ChromecastRoute> = emptyList(),
    val discovering: Boolean = false,
    val connectedDeviceName: String? = null,
    val transportState: XboxTransportState = XboxTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volumePercent: Int? = null,
    val error: String? = null,
    val completionEvent: Long = 0L
)

class ChromecastController(context: Context, private val relay: LocalCastRelay? = null) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(ChromecastState(available = isSupported(appContext)))
    val state: StateFlow<ChromecastState> = mutableState.asStateFlow()

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var attachedClient: RemoteMediaClient? = null
    private var attachedReceiverHost: String? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var pendingPlayback: ResolvedPlayback? = null
    private var pendingPositionMs: Long = 0L
    private var loadedPlaybackId: String? = null
    private var previousPlayerState: Int = MediaStatus.PLAYER_STATE_UNKNOWN
    private var initialized = false
    private var mediaRouter: MediaRouter? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishDiscovery = Runnable {
        mutableState.value = mutableState.value.copy(discovering = false)
    }
    private var routeSelector: MediaRouteSelector? = null
    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = updateRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = updateRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = updateRoutes()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = updateRoutes()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = updateRoutes()
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = updateFromClient()
        override fun onMetadataUpdated() = updateFromClient()
        override fun onQueueStatusUpdated() = updateFromClient()
        override fun onPreloadStatusUpdated() = updateFromClient()
        override fun onSendingRemoteMediaRequest() = Unit
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            mutableState.value = mutableState.value.copy(transportState = XboxTransportState.CONNECTING, error = null)
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) = attachSession(session, loadPending = true)
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attachSession(session, loadPending = loadedPlaybackId == null)
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            mutableState.value = mutableState.value.copy(transportState = XboxTransportState.PAUSED)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) = fail(appContext.getString(R.string.chromecast_session_failed, error))
        override fun onSessionResumeFailed(session: CastSession, error: Int) = fail(appContext.getString(R.string.chromecast_session_failed, error))
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = clearSession()
    }

    /**
     * Initializes Google Cast only after the user explicitly opens the Chromecast picker.
     * Local hoster playback must never depend on Play Services or MediaRouter setup.
     */
    fun initialize(): Boolean {
        if (initialized) return mutableState.value.available
        initialized = true
        return runCatching {
            @Suppress("DEPRECATION")
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("aniworld-google-cast")?.apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
            val contextInstance = CastContext.getSharedInstance(appContext)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()
            mediaRouter = MediaRouter.getInstance(appContext).also { router ->
                router.addCallback(routeSelector!!, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            }
            castContext = contextInstance
            sessionManager = contextInstance.sessionManager.apply {
                addSessionManagerListener(sessionListener, CastSession::class.java)
            }
            mutableState.value = mutableState.value.copy(available = true, discovering = true, error = null)
            updateRoutes()
            sessionManager?.currentCastSession?.let { attachSession(it, loadPending = false) }
            true
        }.getOrElse {
            mutableState.value = ChromecastState(
                available = false,
                error = appContext.getString(R.string.chromecast_unavailable)
            )
            false
        }
    }

    /** Keeps the latest local playback ready; selecting a Cast route starts it at this position. */
    fun prepare(playback: ResolvedPlayback, positionMs: Long) {
        pendingPlayback = playback
        pendingPositionMs = positionMs.coerceAtLeast(0L)
        if (attachedClient != null && loadedPlaybackId != playback.id) loadPending()
    }

    fun discover() {
        if (!initialize()) return
        mainHandler.removeCallbacks(finishDiscovery)
        mutableState.value = mutableState.value.copy(discovering = true, error = null)
        updateRoutes()
        mainHandler.postDelayed(finishDiscovery, 4_000L)
    }

    fun selectDevice(deviceId: String) {
        val router = mediaRouter ?: return
        val route = router.routes.firstOrNull { it.id == deviceId } ?: return
        runCatching { router.selectRoute(route) }
            .onFailure { fail(it.message ?: appContext.getString(R.string.chromecast_session_failed, -1)) }
    }

    fun syncVolumeFromAndroid(volumeFraction: Float) {
        val session = sessionManager?.currentCastSession ?: return
        val safe = volumeFraction.coerceIn(0f, 1f)
        runCatching { session.setVolume(safe.toDouble()) }
            .onSuccess { mutableState.value = mutableState.value.copy(volumePercent = (safe * 100f).toInt()) }
    }

    fun play() = runClientCommand { play() }
    fun pause() = runClientCommand { pause() }
    fun togglePlayPause() {
        if (mutableState.value.transportState == XboxTransportState.PLAYING) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val options = MediaSeekOptions.Builder()
            .setPosition(positionMs.coerceAtLeast(0L))
            .setResumeState(MediaSeekOptions.RESUME_STATE_UNCHANGED)
            .build()
        runClientCommand { seek(options) }
        mutableState.value = mutableState.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    /** Refreshes the approximate remote timeline; call from the main thread. */
    fun refreshProgress() {
        val client = attachedClient ?: return
        mutableState.value = mutableState.value.copy(
            positionMs = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = client.streamDuration.coerceAtLeast(0L)
                .takeIf { it > 0L } ?: mutableState.value.durationMs
        )
    }

    fun disconnect() {
        runCatching { attachedClient?.stop() }
        runCatching { sessionManager?.endCurrentSession(true) }
        clearSession()
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun close() {
        attachedClient?.unregisterCallback(mediaCallback)
        attachedClient = null
        attachedReceiverHost = null
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        routeSelector?.let { selector -> runCatching { mediaRouter?.removeCallback(routeCallback) } }
        mediaRouter = null
        routeSelector = null
        mainHandler.removeCallbacks(finishDiscovery)
        if (multicastLock?.isHeld == true) runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun attachSession(session: CastSession, loadPending: Boolean) {
        attachedClient?.unregisterCallback(mediaCallback)
        attachedClient = session.remoteMediaClient?.also { it.registerCallback(mediaCallback) }
        attachedReceiverHost = runCatching { session.castDevice?.inetAddress?.hostAddress }.getOrNull()
        mutableState.value = mutableState.value.copy(
            connectedDeviceName = session.castDevice?.friendlyName ?: appContext.getString(R.string.chromecast_device),
            transportState = XboxTransportState.CONNECTING,
            volumePercent = (session.volume * 100.0).toInt().coerceIn(0, 100),
            error = null
        )
        if (loadPending) loadPending() else updateFromClient()
    }

    private fun loadPending() {
        val playback = pendingPlayback ?: return
        val client = attachedClient ?: return
        runCatching {
            val castPlayback = relay?.preparePlayback(playback, attachedReceiverHost) ?: playback
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
                putString(MediaMetadata.KEY_TITLE, castPlayback.episode.localizedDisplayTitle(appContext))
                putString(MediaMetadata.KEY_SUBTITLE, castPlayback.seriesTitle)
                putString(MediaMetadata.KEY_SERIES_TITLE, castPlayback.seriesTitle)
            }
            val mediaInfo = MediaInfo.Builder(castPlayback.stream.url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(castPlayback.stream.mimeType ?: inferMimeType(castPlayback.stream.url))
                .setMetadata(metadata)
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(pendingPositionMs)
                .build()
            client.load(request)
            loadedPlaybackId = playback.id
            previousPlayerState = MediaStatus.PLAYER_STATE_UNKNOWN
            mutableState.value = mutableState.value.copy(
                transportState = XboxTransportState.CONNECTING,
                positionMs = pendingPositionMs,
                durationMs = playback.knownDurationMs.coerceAtLeast(0L),
                error = null
            )
        }.onFailure { error ->
            fail(error.message ?: appContext.getString(R.string.chromecast_load_failed))
        }
    }

    private fun updateFromClient() {
        val client = attachedClient ?: return
        val playerState = client.playerState
        val idleFinished = playerState == MediaStatus.PLAYER_STATE_IDLE &&
            client.mediaStatus?.idleReason == MediaStatus.IDLE_REASON_FINISHED
        val transport = when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_BUFFERING -> XboxTransportState.PLAYING
            MediaStatus.PLAYER_STATE_PAUSED -> XboxTransportState.PAUSED
            MediaStatus.PLAYER_STATE_IDLE -> XboxTransportState.STOPPED
            else -> XboxTransportState.CONNECTING
        }
        val completed = idleFinished && previousPlayerState != MediaStatus.PLAYER_STATE_IDLE
        previousPlayerState = playerState
        mutableState.value = mutableState.value.copy(
            transportState = transport,
            positionMs = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = client.streamDuration.coerceAtLeast(0L),
            volumePercent = sessionManager?.currentCastSession?.volume?.let { (it * 100.0).toInt().coerceIn(0, 100) },
            completionEvent = if (completed) System.nanoTime() else mutableState.value.completionEvent,
            error = null
        )
    }

    private fun runClientCommand(command: RemoteMediaClient.() -> Unit) {
        val client = attachedClient ?: return
        runCatching { client.command() }.onFailure { error ->
            fail(error.message ?: appContext.getString(R.string.chromecast_command_failed))
        }
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(transportState = XboxTransportState.ERROR, error = message)
    }

    private fun clearSession() {
        attachedClient?.unregisterCallback(mediaCallback)
        attachedClient = null
        attachedReceiverHost = null
        loadedPlaybackId = null
        previousPlayerState = MediaStatus.PLAYER_STATE_UNKNOWN
        mutableState.value = ChromecastState(available = mutableState.value.available, devices = mutableState.value.devices)
    }

    private fun updateRoutes() {
        val selector = routeSelector ?: return
        val routes = mediaRouter?.routes.orEmpty()
            .filter { route -> !route.isDefault && route.matchesSelector(selector) && route.isEnabled }
            .map { ChromecastRoute(it.id, it.name.toString()) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        mutableState.value = mutableState.value.copy(
            devices = routes,
            discovering = if (routes.isNotEmpty()) false else mutableState.value.discovering
        )
    }

    private fun inferMimeType(url: String): String = when {
        ".m3u8" in url.lowercase() -> "application/x-mpegURL"
        ".mpd" in url.lowercase() -> "application/dash+xml"
        ".webm" in url.lowercase() -> "video/webm"
        else -> "video/mp4"
    }
    companion object {
        /** Cheap capability check used before rendering the Cast button or initializing CastContext. */
        fun isSupported(context: Context): Boolean {
            val packageManager = context.applicationContext.packageManager
            val hasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
            val hasPlayServices = runCatching {
                packageManager.getApplicationInfo("com.google.android.gms", 0).enabled
            }.getOrDefault(false)
            return hasWifi && hasPlayServices
        }
    }

}
