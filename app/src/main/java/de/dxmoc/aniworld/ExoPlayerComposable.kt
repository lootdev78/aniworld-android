package de.dxmoc.aniworld

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import java.time.Duration
import kotlin.math.roundToInt

private enum class GestureMode { BRIGHTNESS, VOLUME }

@OptIn(UnstableApi::class)
@Composable
fun EmbeddedExoPlayer(
    playback: ResolvedPlayback,
    modifier: Modifier = Modifier,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onEnded: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var buffering by remember(playback.id) { mutableStateOf(true) }
    var endedSent by remember(playback.id) { mutableStateOf(false) }
    var gestureMode by remember { mutableStateOf<GestureMode?>(null) }
    var displayedGestureMode by remember { mutableStateOf(GestureMode.BRIGHTNESS) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureVisible by remember { mutableStateOf(false) }
    var playStateVisible by remember { mutableStateOf(false) }
    var isPlaying by remember(playback.id) { mutableStateOf(true) }

    val playerResult = remember(playback.id) {
        runCatching {
            require(playback.stream.url.startsWith("https://") || playback.stream.url.startsWith("http://")) {
                context.getString(R.string.invalid_stream_url)
            }
            val httpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(30))
                .build()
            val requestHeaders = playback.stream.headers.toMutableMap()
            val userAgentKey = requestHeaders.keys.firstOrNull { it.equals("User-Agent", true) }
            val userAgent = userAgentKey?.let { requestHeaders.remove(it) } ?: AniWorldRepository.UA
            val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(requestHeaders)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    setMediaItem(MediaItem.Builder().setUri(playback.stream.url).setMediaId(playback.episode.key).build())
                    if (playback.startPositionMs > 0L) seekTo(playback.startPositionMs)
                    playWhenReady = true
                    prepare()
                }
        }
    }
    val player = playerResult.getOrNull()
    if (player == null) {
        val message = playerResult.exceptionOrNull()?.message ?: context.getString(R.string.player_init_error)
        LaunchedEffect(message) { onError(message) }
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text(message, color = Color.White, modifier = Modifier.padding(24.dp))
        }
        return
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !endedSent) {
                    endedSent = true
                    onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
                    onEnded()
                }
            }
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                val detail = buildString {
                    append(error.errorCodeName).append(": ").append(error.message ?: context.getString(R.string.playback_error))
                    error.cause?.message?.let { append("\n").append(it) }
                }
                onError(detail)
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching { onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L)) }
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (player.playbackState != Player.STATE_ENDED) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(1_000L)
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
        }
    }
    LaunchedEffect(gestureVisible, gestureValue) {
        if (gestureVisible) {
            delay(900L)
            gestureVisible = false
        }
    }
    LaunchedEffect(playStateVisible, isPlaying) {
        if (playStateVisible) {
            delay(650L)
            playStateVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(playback.id) {
                detectTapGestures(onTap = {
                    if (player.isPlaying) player.pause() else player.play()
                    isPlaying = player.isPlaying
                    playStateVisible = true
                })
            }
            .pointerInput(playback.id) {
                var startX = 0f
                var startBrightness = 0.5f
                var startVolume = 0
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        val attrsValue = activity?.window?.attributes?.screenBrightness ?: -1f
                        startBrightness = if (attrsValue in 0f..1f) attrsValue else 0.5f
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        gestureMode = if (startX < size.width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                        displayedGestureMode = gestureMode ?: GestureMode.BRIGHTNESS
                        gestureValue = if (displayedGestureMode == GestureMode.BRIGHTNESS) startBrightness else startVolume.toFloat() / maxVolume
                        gestureVisible = true
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height.coerceAtLeast(1).toFloat()
                        when (gestureMode) {
                            GestureMode.BRIGHTNESS -> {
                                startBrightness = (startBrightness + delta).coerceIn(0.02f, 1f)
                                activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = startBrightness }
                                gestureValue = startBrightness
                            }
                            GestureMode.VOLUME -> {
                                val normalized = (startVolume.toFloat() / maxVolume + delta).coerceIn(0f, 1f)
                                startVolume = (normalized * maxVolume).roundToInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, startVolume, 0)
                                gestureValue = normalized
                            }
                            null -> Unit
                        }
                        gestureVisible = true
                    },
                    onDragEnd = { gestureMode = null },
                    onDragCancel = { gestureMode = null }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply {
                this.player = player
                useController = false
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(false)
                setShowRewindButton(false)
                setShowShuffleButton(false)
                setShowSubtitleButton(false)
                setShowVrButton(false)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } },
            update = { it.player = player }
        )
        if (buffering) CircularProgressIndicator()
        if (playStateVisible) {
            Surface(color = Color.Black.copy(alpha = 0.68f), shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (isPlaying) stringResource(R.string.playback_running) else stringResource(R.string.playback_paused),
                    tint = Color.White,
                    modifier = Modifier.padding(22.dp)
                )
            }
        }
        if (gestureVisible) {
            Surface(color = Color.Black.copy(alpha = 0.72f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (displayedGestureMode == GestureMode.BRIGHTNESS) Icons.Default.Brightness6 else Icons.Default.VolumeUp, null, tint = Color.White)
                        Text(
                            if (displayedGestureMode == GestureMode.BRIGHTNESS) {
                                stringResource(R.string.brightness_percent, (gestureValue * 100).roundToInt())
                            } else {
                                stringResource(R.string.volume_percent, (gestureValue * 100).roundToInt())
                            },
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
