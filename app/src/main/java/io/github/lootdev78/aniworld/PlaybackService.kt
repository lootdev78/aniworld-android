package io.github.lootdev78.aniworld

import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.time.Duration

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var dataSourceFactory: OkHttpDataSource.Factory
    private val progressHandler = Handler(Looper.getMainLooper())
    private var lastEndedMediaId: String? = null
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.mediaItemCount > 0 && player.isPlaying) sendPlaybackProgress(ACTION_PROGRESS)
            progressHandler.postDelayed(this, 1_000L)
        }
    }

    private val previousCommand = SessionCommand(COMMAND_PREVIOUS, Bundle.EMPTY)
    private val nextCommand = SessionCommand(COMMAND_NEXT, Bundle.EMPTY)
    private val stopCommand = SessionCommand(COMMAND_STOP, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val httpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(30))
            .build()
        dataSourceFactory = OkHttpDataSource.Factory(httpClient).setUserAgent(AniWorldRepository.UA)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val mediaId = player.currentMediaItem?.mediaId.orEmpty()
                if (mediaId.isBlank() || mediaId == lastEndedMediaId) return
                lastEndedMediaId = mediaId
                sendPlaybackProgress(ACTION_ENDED)
            }
        })
        progressHandler.post(progressRunnable)

        val callback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(previousCommand)
                    .add(nextCommand)
                    .add(stopCommand)
                    .build()
                return MediaSession.ConnectionResult.accept(commands, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    COMMAND_PREVIOUS -> sendAppAction(ACTION_PREVIOUS)
                    COMMAND_NEXT -> sendAppAction(ACTION_NEXT)
                    COMMAND_STOP -> stopPlaybackAndService(notifyApp = true)
                    else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val layout = listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName(getString(R.string.previous_episode))
                .setSessionCommand(previousCommand)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName(getString(R.string.next_episode))
                .setSessionCommand(nextCommand)
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setDisplayName(getString(R.string.stop))
                .setSessionCommand(stopCommand)
                .build()
        )
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivity)
            .setCustomLayout(layout)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> prepare(intent)
            ACTION_SEEK -> player.seekTo(intent.getLongExtra(EXTRA_SEEK_POSITION, player.currentPosition).coerceAtLeast(0L))
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_STOP -> stopPlaybackAndService(notifyApp = false)
        }
        return START_NOT_STICKY
    }

    private fun prepare(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val headers = jsonToMap(intent.getStringExtra(EXTRA_HEADERS))
        val userAgentKey = headers.keys.firstOrNull { it.equals("User-Agent", true) }
        val userAgent = userAgentKey?.let(headers::remove) ?: AniWorldRepository.UA
        dataSourceFactory.setUserAgent(userAgent).setDefaultRequestProperties(headers)

        val metadata = MediaMetadata.Builder()
            .setTitle(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            .setSubtitle(intent.getStringExtra(EXTRA_SUBTITLE).orEmpty())
            .setArtist(intent.getStringExtra(EXTRA_SERIES_TITLE).orEmpty())
            .apply {
                intent.getStringExtra(EXTRA_ARTWORK)
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        lastEndedMediaId = null
        val item = MediaItem.Builder()
            .setMediaId(intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty())
            .setUri(url)
            .setMediaMetadata(metadata)
            .apply { intent.getStringExtra(EXTRA_MIME)?.takeIf(String::isNotBlank)?.let(::setMimeType) }
            .build()
        val start = intent.getLongExtra(EXTRA_START_POSITION, 0L).coerceAtLeast(0L)
        player.setMediaItem(item, start)
        player.prepare()
        player.playWhenReady = true
    }

    private fun stopPlaybackAndService(notifyApp: Boolean) {
        if (::player.isInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        if (notifyApp) sendAppAction(ACTION_STOPPED)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Workaround for vendor builds that otherwise keep a stale media notification after
        // the task is swiped away. A deliberate remote cast has its own controller/service.
        stopPlaybackAndService(notifyApp = false)
        super.onTaskRemoved(rootIntent)
    }

    private fun sendPlaybackProgress(action: String) {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val rawDuration = player.duration
        val duration = rawDuration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volumePercent = ((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maxVolume).toInt().coerceIn(0, 100)
        sendBroadcast(
            Intent(action)
                .setPackage(packageName)
                .putExtra(EXTRA_POSITION_MS, position)
                .putExtra(EXTRA_DURATION_MS, duration)
                .putExtra(EXTRA_VOLUME_PERCENT, volumePercent)
        )
    }

    private fun sendAppAction(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        if (::session.isInitialized) session.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "io.github.lootdev78.aniworld.action.OPEN_PLAYER"
        const val ACTION_PREVIOUS = "io.github.lootdev78.aniworld.action.PREVIOUS_EPISODE"
        const val ACTION_NEXT = "io.github.lootdev78.aniworld.action.NEXT_EPISODE"
        const val ACTION_STOPPED = "io.github.lootdev78.aniworld.action.PLAYBACK_STOPPED"
        const val ACTION_PROGRESS = "io.github.lootdev78.aniworld.action.PLAYBACK_PROGRESS"
        const val ACTION_ENDED = "io.github.lootdev78.aniworld.action.PLAYBACK_ENDED"
        private const val ACTION_PREPARE = "io.github.lootdev78.aniworld.action.PREPARE_PLAYBACK"
        private const val ACTION_SEEK = "io.github.lootdev78.aniworld.action.SEEK_PLAYBACK"
        private const val ACTION_PLAY = "io.github.lootdev78.aniworld.action.PLAY_PLAYBACK"
        private const val ACTION_PAUSE = "io.github.lootdev78.aniworld.action.PAUSE_PLAYBACK"
        private const val ACTION_STOP = "io.github.lootdev78.aniworld.action.STOP_PLAYBACK"
        private const val COMMAND_PREVIOUS = "io.github.lootdev78.aniworld.command.PREVIOUS_EPISODE"
        private const val COMMAND_NEXT = "io.github.lootdev78.aniworld.command.NEXT_EPISODE"
        private const val COMMAND_STOP = "io.github.lootdev78.aniworld.command.STOP_PLAYBACK"
        private const val EXTRA_URL = "url"
        private const val EXTRA_HEADERS = "headers"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_MEDIA_ID = "media_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_SERIES_TITLE = "series_title"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_START_POSITION = "start_position"
        private const val EXTRA_SEEK_POSITION = "seek_position"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_VOLUME_PERCENT = "volume_percent"

        fun prepare(context: Context, playback: ResolvedPlayback) {
            val episodeTitle = playback.episode.localizedDisplayTitle(context)
            val title = "${playback.seriesTitle} · ${playback.episode.localizedLabel(context)}"
            val headersJson = JSONObject().apply { playback.stream.headers.forEach { (key, value) -> put(key, value) } }.toString()
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PREPARE)
                .putExtra(EXTRA_URL, playback.stream.url)
                .putExtra(EXTRA_HEADERS, headersJson)
                .putExtra(EXTRA_MIME, playback.stream.mimeType)
                .putExtra(EXTRA_MEDIA_ID, playback.episode.key)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, episodeTitle)
                .putExtra(EXTRA_SERIES_TITLE, playback.seriesTitle)
                .putExtra(EXTRA_ARTWORK, playback.series.coverUrl)
                .putExtra(EXTRA_START_POSITION, playback.startPositionMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun seekTo(context: Context, positionMs: Long) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_SEEK)
                    .putExtra(EXTRA_SEEK_POSITION, positionMs.coerceAtLeast(0L))
            )
        }

        fun play(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY))
        }

        fun pause(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_PAUSE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }

        private fun jsonToMap(raw: String?): MutableMap<String, String> {
            if (raw.isNullOrBlank()) return linkedMapOf()
            return runCatching {
                val objectValue = JSONObject(raw)
                linkedMapOf<String, String>().apply {
                    objectValue.keys().forEach { key -> put(key, objectValue.optString(key)) }
                }
            }.getOrDefault(linkedMapOf())
        }
    }
}
