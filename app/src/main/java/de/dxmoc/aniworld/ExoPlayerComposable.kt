package de.dxmoc.aniworld

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode { BRIGHTNESS, VOLUME }

@OptIn(UnstableApi::class)
@Composable
fun EmbeddedExoPlayer(
    playback: ResolvedPlayback,
    modifier: Modifier = Modifier,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onEnded: (positionMs: Long, durationMs: Long) -> Unit,
    onError: (String) -> Unit,
    seekPositionMs: Long = 0L,
    seekRequestId: Int = 0,
    onSeeked: (Long) -> Unit = {},
    onInteraction: () -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var player by remember { mutableStateOf<MediaController?>(null) }
    var buffering by remember(playback.id) { mutableStateOf(true) }
    var endedSent by remember(playback.id) { mutableStateOf(false) }
    var gestureMode by remember { mutableStateOf<GestureMode?>(null) }
    var displayedGestureMode by remember { mutableStateOf(GestureMode.BRIGHTNESS) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureVisible by remember { mutableStateOf(false) }
    var playStateVisible by remember { mutableStateOf(false) }
    var isPlaying by remember(playback.id) { mutableStateOf(true) }
    var seekFeedback by remember(playback.id) { mutableStateOf<Int?>(null) }

    val controllerFuture = remember {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync()
    }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { player = it }
                    .onFailure { onError(it.message ?: context.getString(R.string.player_init_error)) }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            player = null
            if (controllerFuture.isDone) runCatching { controllerFuture.get().release() }
            else controllerFuture.cancel(true)
        }
    }

    LaunchedEffect(playback.id) {
        if (!playback.stream.url.startsWith("https://") && !playback.stream.url.startsWith("http://")) {
            onError(context.getString(R.string.invalid_stream_url))
            return@LaunchedEffect
        }
        endedSent = false
        PlaybackService.prepare(context, playback)
    }

    DisposableEffect(player, playback.id) {
        val activePlayer = player
        if (activePlayer == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !endedSent) {
                    endedSent = true
                    val finalPosition = activePlayer.currentPosition.coerceAtLeast(0L)
                    val finalDuration = activePlayer.duration.coerceAtLeast(0L)
                    onProgress(finalPosition, finalDuration)
                    onEnded(finalPosition, finalDuration)
                }
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                onPlayingChanged(value)
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
        activePlayer.addListener(listener)
        buffering = activePlayer.playbackState == Player.STATE_BUFFERING || activePlayer.playbackState == Player.STATE_IDLE
        isPlaying = activePlayer.isPlaying
        onPlayingChanged(activePlayer.isPlaying)
        onDispose {
            runCatching { onProgress(activePlayer.currentPosition.coerceAtLeast(0L), activePlayer.duration.coerceAtLeast(0L)) }
            activePlayer.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(1_000L)
            player?.let { onProgress(it.currentPosition.coerceAtLeast(0L), it.duration.coerceAtLeast(0L)) }
        }
    }
    LaunchedEffect(player, seekRequestId) {
        val activePlayer = player ?: return@LaunchedEffect
        if (seekRequestId > 0) activePlayer.seekTo(seekPositionMs.coerceAtLeast(0L))
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
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(750L)
            seekFeedback = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(playback.id, player) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        onInteraction()
                        player?.let { activePlayer ->
                            val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                            val maxPosition = activePlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            val target = (activePlayer.currentPosition + delta).coerceIn(0L, maxPosition)
                            activePlayer.seekTo(target)
                            onSeeked(target)
                            seekFeedback = if (delta < 0L) -10 else 10
                        }
                    },
                    onTap = {
                        onInteraction()
                        player?.let {
                            if (it.isPlaying) it.pause() else it.play()
                            isPlaying = it.isPlaying
                            playStateVisible = true
                        }
                    }
                )
            }
            .pointerInput(playback.id) {
                var startBrightness = 0.5f
                var startVolumeFraction = 0f
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onInteraction()
                        totalDrag = 0f
                        val edgeFraction = offset.x / size.width.coerceAtLeast(1).toFloat()
                        gestureMode = when {
                            edgeFraction <= 0.38f -> GestureMode.BRIGHTNESS
                            edgeFraction >= 0.62f -> GestureMode.VOLUME
                            else -> null
                        }
                        val attrsValue = activity?.window?.attributes?.screenBrightness ?: -1f
                        startBrightness = if (attrsValue in 0f..1f) attrsValue else 0.5f
                        startVolumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        gestureMode?.let {
                            displayedGestureMode = it
                            gestureValue = if (it == GestureMode.BRIGHTNESS) startBrightness else startVolumeFraction
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (gestureMode == null) return@detectVerticalDragGestures
                        change.consume()
                        totalDrag += dragAmount
                        if (abs(totalDrag) < 18f) return@detectVerticalDragGestures
                        val delta = (-totalDrag / size.height.coerceAtLeast(1).toFloat()) * 1.15f
                        when (gestureMode) {
                            GestureMode.BRIGHTNESS -> {
                                val normalized = (startBrightness + delta).coerceIn(0.02f, 1f)
                                activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = normalized }
                                gestureValue = normalized
                            }
                            GestureMode.VOLUME -> {
                                val normalized = (startVolumeFraction + delta).coerceIn(0f, 1f)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (normalized * maxVolume).roundToInt(), 0)
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
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
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
                }
            },
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
        seekFeedback?.let { seconds ->
            Surface(color = Color.Black.copy(alpha = 0.72f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Text(
                    text = if (seconds < 0) "−10 s" else "+10 s",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                )
            }
        }
        if (gestureVisible) {
            Surface(color = Color.Black.copy(alpha = 0.72f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
