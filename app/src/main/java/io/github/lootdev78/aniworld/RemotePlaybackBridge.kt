package io.github.lootdev78.aniworld

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

/** Commands accepted from the local receiver website and its fallback transports. */
sealed interface RemotePlaybackCommand {
    data object Play : RemotePlaybackCommand
    data object Pause : RemotePlaybackCommand
    data object Toggle : RemotePlaybackCommand
    data object Previous : RemotePlaybackCommand
    data object Next : RemotePlaybackCommand
    data object Stop : RemotePlaybackCommand
    data class Seek(val positionMs: Long) : RemotePlaybackCommand
    data class Volume(val value: Float) : RemotePlaybackCommand
    data class Mute(val muted: Boolean) : RemotePlaybackCommand
}

data class RemotePlaybackSnapshot(
    val playback: ResolvedPlayback? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val playerState: Int = 0,
    val error: String? = null,
    val revision: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LocalPlaybackServerStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val preferredPort: Int = 8787,
    val actualPort: Int = 0,
    val pin: String = "",
    val pageUrl: String = "",
    val connectedClients: Int = 0,
    val lastError: String? = null
)

/**
 * Process-wide bridge shared by PlaybackService and the local HTTP receiver.
 * It deliberately does not own ExoPlayer; commands are always applied by PlaybackService.
 */
object RemotePlaybackRuntime {
    private val random = SecureRandom()
    private val mutableSnapshot = MutableStateFlow(RemotePlaybackSnapshot())
    val snapshot: StateFlow<RemotePlaybackSnapshot> = mutableSnapshot.asStateFlow()

    private val mutableCommands = MutableSharedFlow<RemotePlaybackCommand>(extraBufferCapacity = 64)
    val commands: SharedFlow<RemotePlaybackCommand> = mutableCommands.asSharedFlow()

    private val mutableStatus = MutableStateFlow(LocalPlaybackServerStatus())
    val status: StateFlow<LocalPlaybackServerStatus> = mutableStatus.asStateFlow()

    private var applicationContext: Context? = null
    private var relay: LocalCastRelay? = null
    private var server: LocalPlaybackWebServer? = null
    private var webRelayEnabled: Boolean = false
    private var preferredPort: Int = 8787

    @Synchronized
    fun configure(context: Context, preferences: AppPreferences) {
        applicationContext = context.applicationContext
        webRelayEnabled = preferences.localWebRelayEnabled
        preferredPort = preferences.localWebRelayPort.coerceIn(1024, 65535)
        mutableStatus.update { it.copy(enabled = webRelayEnabled, preferredPort = preferredPort) }
        if (!webRelayEnabled) {
            stopServer(clearPlayback = false)
        } else if (mutableSnapshot.value.playback != null) {
            ensureServer()
        }
    }

    @Synchronized
    fun onPlaybackPrepared(playback: ResolvedPlayback) {
        mutableSnapshot.update {
            it.copy(
                playback = playback,
                playing = true,
                positionMs = playback.startPositionMs.coerceAtLeast(0L),
                durationMs = playback.knownDurationMs.coerceAtLeast(0L),
                error = null,
                revision = it.revision + 1,
                updatedAt = System.currentTimeMillis()
            )
        }
        if (webRelayEnabled) ensureServer()
    }

    fun publishPlayerState(
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
        volume: Float,
        muted: Boolean,
        playerState: Int,
        error: String? = null
    ) {
        mutableSnapshot.update {
            it.copy(
                playing = playing,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                volume = volume.coerceIn(0f, 1f),
                muted = muted,
                playerState = playerState,
                error = error,
                revision = it.revision + 1,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun clearPlayback() {
        mutableSnapshot.update {
            RemotePlaybackSnapshot(revision = it.revision + 1, updatedAt = System.currentTimeMillis())
        }
        server?.scheduleIdleShutdown()
    }

    fun submit(command: RemotePlaybackCommand): Boolean = mutableCommands.tryEmit(command)

    internal fun updateServerStatus(transform: (LocalPlaybackServerStatus) -> LocalPlaybackServerStatus) {
        mutableStatus.update(transform)
    }

    internal fun currentPlayback(): ResolvedPlayback? = mutableSnapshot.value.playback

    @Synchronized
    private fun ensureServer() {
        if (server?.isRunning == true) return
        val context = applicationContext ?: return
        val activeRelay = LocalCastRelay(context)
        val created = LocalPlaybackWebServer(
            context = context,
            relay = activeRelay,
            preferredPort = preferredPort,
            pin = "%06d".format(random.nextInt(1_000_000))
        )
        val started = runCatching { created.start() }
        if (started.isSuccess) {
            relay = activeRelay
            server = created
        } else {
            activeRelay.close()
            mutableStatus.update {
                it.copy(running = false, actualPort = 0, pageUrl = "", pin = "", lastError = started.exceptionOrNull()?.message)
            }
        }
    }

    @Synchronized
    fun stopServer(clearPlayback: Boolean = true) {
        server?.close()
        relay?.close()
        server = null
        relay = null
        mutableStatus.update { it.copy(running = false, actualPort = 0, pageUrl = "", pin = "", connectedClients = 0) }
        if (clearPlayback) clearPlayback()
    }
}
