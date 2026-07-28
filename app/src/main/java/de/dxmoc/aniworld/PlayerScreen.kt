package de.dxmoc.aniworld

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    playback: ResolvedPlayback,
    hasPrevious: Boolean,
    hasNext: Boolean,
    autoNextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: (Long, Long) -> Unit,
    onProgress: (Long, Long, Boolean) -> Unit,
    onEnded: (Long, Long) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var position by remember(playback.id) { mutableLongStateOf(playback.startPositionMs) }
    var duration by remember(playback.id) { mutableLongStateOf(0L) }
    var playerError by remember(playback.id) { mutableStateOf<String?>(null) }
    var autoNextVisible by remember(playback.id) { mutableStateOf(false) }
    var autoNextSeconds by remember(playback.id) { mutableIntStateOf(8) }
    var controlsVisible by remember(playback.id) { mutableStateOf(true) }
    var controlsGeneration by remember(playback.id) { mutableIntStateOf(0) }
    var isPlaying by remember(playback.id) { mutableStateOf(true) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(playback.id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(controlsVisible, controlsGeneration, isPlaying, autoNextVisible, playerError) {
        if (controlsVisible && isPlaying && !autoNextVisible && playerError == null) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(autoNextVisible, autoNextSeconds) {
        if (!autoNextVisible) return@LaunchedEffect
        if (autoNextSeconds <= 0) {
            autoNextVisible = false
            onProgress(position, duration, true)
            onNext()
        } else {
            delay(1_000L)
            autoNextSeconds--
        }
    }

    DisposableEffect(activity) {
        activity?.let { host ->
            WindowCompat.setDecorFitsSystemWindows(host.window, false)
            WindowInsetsControllerCompat(host.window, host.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.let { host ->
                host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(host.window, true)
                WindowInsetsControllerCompat(host.window, host.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun closePlayer() {
        autoNextVisible = false
        PlaybackService.stop(context)
        onClose(position, duration)
    }

    BackHandler { closePlayer() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EmbeddedExoPlayer(
            playback = playback,
            modifier = Modifier.fillMaxSize(),
            onProgress = { pos, dur ->
                position = pos
                duration = dur
                onProgress(pos, dur, false)
            },
            onEnded = { finalPosition, finalDuration ->
                position = finalPosition
                duration = finalDuration
                onEnded(finalPosition, finalDuration)
                if (autoNextEnabled && hasNext) {
                    autoNextSeconds = 8
                    autoNextVisible = true
                }
            },
            onError = { message ->
                playerError = message
                controlsVisible = true
                onError(message)
            },
            onInteraction = {
                controlsVisible = true
                controlsGeneration++
            },
            onPlayingChanged = { playing ->
                isPlaying = playing
                if (!playing) controlsVisible = true
            }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::closePlayer) {
                Icon(Icons.Default.Close, stringResource(R.string.player_close), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    autoNextVisible = false
                    onProgress(position, duration, true)
                    onPrevious()
                },
                enabled = hasPrevious
            ) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.player_previous_episode), tint = if (hasPrevious) Color.White else Color.White.copy(alpha = .32f))
            }
            IconButton(
                onClick = {
                    autoNextVisible = false
                    onProgress(position, duration, true)
                    onNext()
                },
                enabled = hasNext
            ) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.player_next_episode), tint = if (hasNext) Color.White else Color.White.copy(alpha = .32f))
            }
            }
        }

        if (autoNextVisible) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
                color = Color.Black.copy(alpha = .84f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.auto_next_countdown, autoNextSeconds), color = Color.White, fontWeight = FontWeight.Bold)
                    Button(onClick = { autoNextVisible = false }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || playerError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = Color.Black.copy(alpha = .62f)
            ) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.player_title, playback.seriesTitle, playback.episode.localizedLabel()), color = Color.White, fontWeight = FontWeight.Bold)
                Text(playback.episode.localizedDisplayTitle(), color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                if (duration > 0L) {
                    LinearProgressIndicator(
                        progress = { (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Text(stringResource(R.string.player_gesture_hint), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                playerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L || ms == Long.MAX_VALUE) return "00:00"
    val totalSeconds = ms / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
