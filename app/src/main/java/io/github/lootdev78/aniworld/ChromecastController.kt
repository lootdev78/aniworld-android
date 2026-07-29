package io.github.lootdev78.aniworld

import android.content.Context
import android.net.wifi.WifiManager
import com.google.android.gms.cast.MediaInfo
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
data class ChromecastState(
    val available: Boolean = true,
    val connectedDeviceName: String? = null,
    val transportState: XboxTransportState = XboxTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val completionEvent: Long = 0L
)

class ChromecastController(context: Context, private val relay: LocalCastRelay? = null) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(ChromecastState())
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
            castContext = contextInstance
            sessionManager = contextInstance.sessionManager.apply {
                addSessionManagerListener(sessionListener, CastSession::class.java)
            }
            mutableState.value = mutableState.value.copy(available = true, error = null)
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

    fun play() = runClientCommand { play() }
    fun pause() = runClientCommand { pause() }
    fun togglePlayPause() {
        if (mutableState.value.transportState == XboxTransportState.PLAYING) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        runClientCommand { seek(positionMs.coerceAtLeast(0L)) }
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
        mutableState.value = ChromecastState(available = mutableState.value.available)
    }

    private fun inferMimeType(url: String): String = when {
        ".m3u8" in url.lowercase() -> "application/x-mpegURL"
        ".mpd" in url.lowercase() -> "application/dash+xml"
        ".webm" in url.lowercase() -> "video/webm"
        else -> "video/mp4"
    }
}
