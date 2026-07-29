package io.github.lootdev78.aniworld

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    playback: ResolvedPlayback,
    hasPrevious: Boolean,
    hasNext: Boolean,
    autoNextEnabled: Boolean,
    availableHosters: List<Hoster>,
    allowExternalPlayer: Boolean,
    castEnabled: Boolean,
    onLanguageChange: (Language) -> Unit,
    onHosterChange: (Hoster) -> Unit,
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
    var duration by remember(playback.id) { mutableLongStateOf(playback.knownDurationMs.coerceAtLeast(0L)) }
    var scrubPosition by remember(playback.id) { mutableFloatStateOf(playback.startPositionMs.toFloat()) }
    var scrubbing by remember(playback.id) { mutableStateOf(false) }
    var playerError by remember(playback.id) { mutableStateOf<String?>(null) }
    var autoNextVisible by remember(playback.id) { mutableStateOf(false) }
    var autoNextSeconds by remember(playback.id) { mutableIntStateOf(8) }
    var controlsVisible by remember(playback.id) { mutableStateOf(true) }
    var controlsGeneration by remember(playback.id) { mutableIntStateOf(0) }
    var isPlaying by remember(playback.id) { mutableStateOf(true) }
    var languageMenuOpen by remember(playback.id) { mutableStateOf(false) }
    var hosterMenuOpen by remember(playback.id) { mutableStateOf(false) }
    var castMenuOpen by remember(playback.id) { mutableStateOf(false) }
    var manualCastDialogOpen by remember(playback.id) { mutableStateOf(false) }
    var chromecastDialogOpen by remember(playback.id) { mutableStateOf(false) }
    var manualCastAddress by remember(playback.id) { mutableStateOf("") }
    var streamInfoOpen by remember(playback.id) { mutableStateOf(false) }
    var internalPausedForCast by remember(playback.id) { mutableStateOf(false) }
    val streamInfo = remember(playback.stream.url, playback.stream.mimeType) {
        StreamPresentation.from(playback.stream)
    }
    val castRelay = remember(context.applicationContext) { LocalCastRelay(context.applicationContext) }
    val dlnaController = remember(context.applicationContext, castRelay) { XboxCastController(context.applicationContext, castRelay) }
    val dlnaState by dlnaController.state.collectAsState()
    val fcastController = remember(context.applicationContext, castRelay) { FCastController(context.applicationContext, castRelay) }
    val fcastState by fcastController.state.collectAsState()
    val chromecastController = remember(context.applicationContext, castRelay) { ChromecastController(context.applicationContext, castRelay) }
    val chromecastState by chromecastController.state.collectAsState()
    val smartViewController = remember(context.applicationContext, castRelay) { SamsungSmartViewController(context.applicationContext, castRelay) }
    val smartViewState by smartViewController.state.collectAsState()
    val dlnaActive = dlnaState.connectedDevice != null
    val fcastActive = fcastState.connectedDevice != null
    val chromecastActive = chromecastState.connectedDeviceName != null
    val smartViewActive = smartViewState.connectedDevice != null
    val castActive = castEnabled && (smartViewActive || dlnaActive || fcastActive || chromecastActive)
    val remoteTransportState = when {
        smartViewActive -> smartViewState.transportState
        chromecastActive -> chromecastState.transportState
        fcastActive -> fcastState.transportState
        else -> dlnaState.transportState
    }
    val remotePositionMs = when {
        smartViewActive -> smartViewState.positionMs
        chromecastActive -> chromecastState.positionMs
        fcastActive -> fcastState.positionMs
        else -> dlnaState.positionMs
    }
    val remoteDurationMs = when {
        smartViewActive -> smartViewState.durationMs
        chromecastActive -> chromecastState.durationMs
        fcastActive -> fcastState.durationMs
        else -> dlnaState.durationMs
    }
    val remoteDeviceName = when {
        smartViewActive -> smartViewState.connectedDevice?.displayName.orEmpty()
        chromecastActive -> chromecastState.connectedDeviceName.orEmpty()
        fcastActive -> fcastState.connectedDevice?.displayName.orEmpty()
        else -> dlnaState.connectedDevice?.displayName.orEmpty()
    }
    val playerLanguages = remember(availableHosters, playback.stream.language) {
        (availableHosters.map { it.lang } + playback.stream.language).filter { it != Language.UNKNOWN }.distinct()
    }
    val orderedHosters = remember(availableHosters) {
        availableHosters.distinctBy { "${HosterCatalog.normalize(it.name)}:${it.lang.token}" }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val nearbyDevicesPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            castMenuOpen = true
            dlnaController.discover()
            fcastController.discover()
            smartViewController.discover()
        } else {
            playerError = context.getString(R.string.xbox_cast_permission_denied)
            controlsVisible = true
        }
    }
    val chromecastPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            chromecastController.prepare(playback, position)
            if (chromecastController.initialize()) {
                chromecastDialogOpen = true
            } else {
                playerError = context.getString(R.string.chromecast_unavailable)
            }
        } else {
            playerError = context.getString(R.string.xbox_cast_permission_denied)
            controlsVisible = true
        }
    }

    LaunchedEffect(castEnabled) {
        if (!castEnabled) {
            castMenuOpen = false
            chromecastDialogOpen = false
            manualCastDialogOpen = false
            if (smartViewActive) smartViewController.disconnect()
            if (chromecastActive) chromecastController.disconnect()
            if (fcastActive) fcastController.disconnect()
            if (dlnaActive) dlnaController.disconnect()
        }
    }

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
            if (smartViewActive) smartViewController.disconnect()
            if (dlnaActive) dlnaController.disconnect()
            if (fcastActive) fcastController.disconnect()
            if (chromecastActive) chromecastController.disconnect()
            onNext()
        } else {
            delay(1_000L)
            autoNextSeconds--
        }
    }

    LaunchedEffect(castActive, remoteTransportState) {
        if (castActive) {
            PlaybackService.pause(context)
            internalPausedForCast = true
            isPlaying = remoteTransportState == XboxTransportState.PLAYING
        } else if (internalPausedForCast) {
            // A remote session disappeared or was ended outside the app. Never resume
            // silently on the phone; stop Android playback to avoid double playback.
            PlaybackService.stop(context)
            isPlaying = false
            internalPausedForCast = false
            controlsVisible = true
        }
    }

    LaunchedEffect(chromecastActive) {
        if (chromecastActive) {
            if (smartViewActive) smartViewController.disconnect()
            if (fcastActive) fcastController.disconnect()
            if (dlnaActive) dlnaController.disconnect()
        }
        while (chromecastActive) {
            chromecastController.refreshProgress()
            delay(1_000L)
        }
    }

    LaunchedEffect(castActive, remotePositionMs, remoteDurationMs) {
        if (castActive) {
            position = remotePositionMs.coerceAtLeast(0L)
            if (remoteDurationMs > 0L) duration = remoteDurationMs
            if (!scrubbing) scrubPosition = position.toFloat()
            onProgress(position, duration, false)
        }
    }

    LaunchedEffect(dlnaState.completionEvent, fcastState.completionEvent, chromecastState.completionEvent, smartViewState.completionEvent) {
        val completion = maxOf(dlnaState.completionEvent, fcastState.completionEvent, chromecastState.completionEvent, smartViewState.completionEvent)
        if (completion != 0L) {
            onEnded(remotePositionMs, remoteDurationMs)
            if (autoNextEnabled && hasNext) {
                autoNextSeconds = 8
                autoNextVisible = true
            }
        }
    }

    LaunchedEffect(dlnaState.error, fcastState.error, chromecastState.error, smartViewState.error) {
        val message = smartViewState.error ?: chromecastState.error ?: fcastState.error ?: dlnaState.error
        message?.let {
            playerError = it
            controlsVisible = true
            onError(it)
        }
    }

    DisposableEffect(dlnaController, fcastController, chromecastController, smartViewController, castRelay) {
        onDispose {
            runCatching { smartViewController.disconnect() }
            runCatching { chromecastController.disconnect() }
            runCatching { fcastController.disconnect() }
            runCatching { dlnaController.disconnect() }
            PlaybackService.stop(context)
            smartViewController.close()
            dlnaController.close()
            fcastController.close()
            chromecastController.close()
            castRelay.close()
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

    fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val host = activity ?: return
        controlsVisible = false
        languageMenuOpen = false
        hosterMenuOpen = false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { host.enterPictureInPictureMode(params) }
            .onFailure { error ->
                playerError = error.message ?: context.getString(R.string.pip_failed)
                controlsVisible = true
            }
    }

    fun openCastPicker() {
        controlsVisible = true
        dlnaState.error?.let { dlnaController.clearError() }
        fcastState.error?.let { fcastController.clearError() }
        chromecastState.error?.let { chromecastController.clearError() }
        smartViewState.error?.let { smartViewController.clearError() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            nearbyDevicesPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            castMenuOpen = true
            dlnaController.discover()
            fcastController.discover()
            smartViewController.discover()
        }
    }

    fun seekPlayback(targetMs: Long) {
        val target = targetMs.coerceAtLeast(0L)
        when {
            smartViewActive -> smartViewController.seekTo(target)
            chromecastActive -> chromecastController.seekTo(target)
            fcastActive -> fcastController.seekTo(target)
            dlnaActive -> dlnaController.seekTo(target)
            else -> PlaybackService.seekTo(context, target)
        }
        position = target
        scrubPosition = target.toFloat()
        onProgress(target, duration, true)
        controlsGeneration++
    }

    fun disconnectCastAndResume() {
        val resumePosition = remotePositionMs.takeIf { it > 0L } ?: position
        if (smartViewActive) smartViewController.disconnect()
        if (chromecastActive) chromecastController.disconnect()
        if (fcastActive) fcastController.disconnect()
        if (dlnaActive) dlnaController.disconnect()
        PlaybackService.seekTo(context, resumePosition)
        PlaybackService.play(context)
        position = resumePosition
        scrubPosition = resumePosition.toFloat()
        internalPausedForCast = false
        isPlaying = true
    }

    fun stopCastForNavigation() {
        if (smartViewActive) smartViewController.disconnect()
        if (chromecastActive) chromecastController.disconnect()
        if (fcastActive) fcastController.disconnect()
        if (dlnaActive) dlnaController.disconnect()
        internalPausedForCast = false
    }

    fun closePlayer() {
        autoNextVisible = false
        if (smartViewActive) smartViewController.disconnect()
        if (chromecastActive) chromecastController.disconnect()
        if (fcastActive) fcastController.disconnect()
        if (dlnaActive) dlnaController.disconnect()
        PlaybackService.stop(context)
        onClose(position, duration)
    }

    fun copyStreamLink() {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.player_stream_link_clip_label), playback.stream.url)
            )
            Toast.makeText(context, context.getString(R.string.player_stream_link_copied), Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            playerError = error.message ?: context.getString(R.string.player_stream_link_copy_failed)
            controlsVisible = true
        }
    }

    BackHandler { closePlayer() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EmbeddedExoPlayer(
            playback = playback,
            modifier = Modifier.fillMaxSize(),
            onProgress = { pos, dur ->
                if (!castActive) {
                    position = pos
                    duration = dur
                    if (!scrubbing) scrubPosition = pos.toFloat()
                    onProgress(pos, dur, false)
                }
            },
            onEnded = { finalPosition, finalDuration ->
                if (!castActive) {
                    position = finalPosition
                    duration = finalDuration
                    onEnded(finalPosition, finalDuration)
                    if (autoNextEnabled && hasNext) {
                        autoNextSeconds = 8
                        autoNextVisible = true
                    }
                }
            },
            onError = { message ->
                playerError = message
                controlsVisible = true
                onError(message)
            },
            onInteraction = {
                controlsGeneration++
            },
            onSingleTap = {
                if (!controlsVisible) {
                    controlsVisible = true
                    controlsGeneration++
                    true
                } else {
                    false
                }
            },
            onPlayingChanged = { playing ->
                if (!castActive) {
                    isPlaying = playing
                    if (!playing) controlsVisible = true
                }
            }
        )

        if (castActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .88f))
                    .clickable {
                        if (!controlsVisible) {
                            controlsVisible = true
                        } else {
                            when {
                                smartViewActive -> smartViewController.togglePlayPause()
                                chromecastActive -> chromecastController.togglePlayPause()
                                fcastActive -> fcastController.togglePlayPause()
                                else -> dlnaController.togglePlayPause()
                            }
                        }
                        controlsGeneration++
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CastConnected,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Text(
                        stringResource(R.string.cast_playing_on, remoteDeviceName),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (remoteTransportState == XboxTransportState.PAUSED) stringResource(R.string.playback_paused)
                        else stringResource(R.string.playback_running),
                        color = Color.White.copy(alpha = .76f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .42f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = .38f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    IconButton(onClick = ::closePlayer) {
                        Icon(Icons.Default.Close, stringResource(R.string.player_close), tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
            if (castEnabled) {
            Box {
                IconButton(onClick = ::openCastPicker) {
                    Icon(
                        if (castActive) Icons.Default.CastConnected else Icons.Default.Cast,
                        stringResource(R.string.local_cast),
                        tint = if (castActive) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                DropdownMenu(expanded = castMenuOpen, onDismissRequest = { castMenuOpen = false }) {
                    if (chromecastState.available) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.chromecast_button), fontWeight = FontWeight.Bold)
                                    Text(
                                        if (chromecastActive) remoteDeviceName else stringResource(R.string.chromecast_device),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            leadingIcon = { Icon(if (chromecastActive) Icons.Default.CastConnected else Icons.Default.Cast, null) },
                            onClick = {
                                castMenuOpen = false
                                if (chromecastActive) {
                                    disconnectCastAndResume()
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    chromecastPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                                } else {
                                    chromecastController.prepare(playback, position)
                                    if (chromecastController.initialize()) chromecastDialogOpen = true
                                    else playerError = context.getString(R.string.chromecast_unavailable)
                                }
                            }
                        )
                    }
                    if (castActive) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(remoteDeviceName, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.cast_disconnect_and_resume), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.CastConnected, null) },
                            onClick = {
                                castMenuOpen = false
                                disconnectCastAndResume()
                            }
                        )
                    }
                    if (smartViewActive) {
                        DropdownMenuItem(
                            text = {
                                Column(Modifier.width(260.dp)) {
                                    Text(stringResource(R.string.cast_volume), fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = (smartViewState.volume ?: 50).toFloat(),
                                        onValueChange = { smartViewController.setVolume(it.toInt()) },
                                        valueRange = 0f..100f
                                    )
                                }
                            },
                            onClick = { controlsVisible = true }
                        )
                    }
                    if (dlnaActive && dlnaState.connectedDevice?.renderingControlUrl?.isNotBlank() == true) {
                        DropdownMenuItem(
                            text = {
                                Column(Modifier.width(260.dp)) {
                                    Text(stringResource(R.string.cast_volume), fontWeight = FontWeight.Bold)
                                    Slider(
                                        value = (dlnaState.volume ?: 50).toFloat(),
                                        onValueChange = { dlnaController.setVolume(it.toInt()) },
                                        valueRange = 0f..100f
                                    )
                                }
                            },
                            onClick = { controlsVisible = true }
                        )
                    }
                    if (playback.stream.headers.keys.any { !it.equals("User-Agent", true) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cast_header_warning), style = MaterialTheme.typography.labelSmall) },
                            enabled = false,
                            onClick = { castMenuOpen = false }
                        )
                    }
                    if (smartViewState.discovering || dlnaState.discovering || fcastState.discovering) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cast_searching)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = false,
                            onClick = { castMenuOpen = false }
                        )
                    }
                    if (!smartViewState.sdkAvailable) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.smartview_sdk_title))
                                    Text(stringResource(R.string.smartview_sdk_missing_short), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            enabled = false,
                            onClick = { castMenuOpen = false }
                        )
                    }
                    smartViewState.devices.filter { it.id != smartViewState.connectedDevice?.id && it.dmpSupported != false }.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(device.displayName)
                                    Text(
                                        if (device.standby) stringResource(R.string.smartview_device_standby)
                                        else stringResource(R.string.smartview_device_native),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Cast, null) },
                            onClick = {
                                castMenuOpen = false
                                playerError = null
                                if (chromecastActive) chromecastController.disconnect()
                                if (fcastActive) fcastController.disconnect()
                                if (dlnaActive) dlnaController.disconnect()
                                PlaybackService.pause(context)
                                internalPausedForCast = true
                                smartViewController.cast(device, playback, position)
                            }
                        )
                    }
                    dlnaState.devices.filter { it.id != dlnaState.connectedDevice?.id }.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(device.displayName)
                                    Text(
                                        stringResource(
                                            when (device.profile) {
                                                XboxDeviceProfile.XBOX_ONE -> R.string.xbox_profile_one
                                                XboxDeviceProfile.XBOX_SERIES -> R.string.xbox_profile_series
                                                XboxDeviceProfile.SAMSUNG_TIZEN -> R.string.xbox_profile_tizen
                                                XboxDeviceProfile.GENERIC_DLNA -> R.string.xbox_profile_generic
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            leadingIcon = { Icon(if (device.isXbox) Icons.Default.CastConnected else Icons.Default.Cast, null) },
                            onClick = {
                                castMenuOpen = false
                                playerError = null
                                if (smartViewActive) smartViewController.disconnect()
                                if (chromecastActive) chromecastController.disconnect()
                                if (fcastActive) fcastController.disconnect()
                                PlaybackService.pause(context)
                                internalPausedForCast = true
                                dlnaController.cast(device, playback, position)
                            }
                        )
                    }
                    fcastState.devices.filter { it.id != fcastState.connectedDevice?.id }.forEach { device ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(device.displayName)
                                    Text(stringResource(R.string.cast_fcast_device), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Cast, null) },
                            onClick = {
                                castMenuOpen = false
                                playerError = null
                                if (smartViewActive) smartViewController.disconnect()
                                if (chromecastActive) chromecastController.disconnect()
                                if (dlnaActive) dlnaController.disconnect()
                                PlaybackService.pause(context)
                                internalPausedForCast = true
                                fcastController.cast(device, playback, position)
                            }
                        )
                    }
                    if (!smartViewState.discovering && !dlnaState.discovering && !fcastState.discovering && smartViewState.devices.isEmpty() && dlnaState.devices.isEmpty() && fcastState.devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cast_no_local_devices)) },
                            onClick = {
                                dlnaController.discover()
                                fcastController.discover()
                                smartViewController.discover()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cast_refresh_local)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = {
                            dlnaController.discover()
                            fcastController.discover()
                            smartViewController.discover()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cast_manual_address)) },
                        leadingIcon = { Icon(Icons.Default.Tune, null) },
                        onClick = {
                            castMenuOpen = false
                            manualCastDialogOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.miracast_system_settings))
                                Text(stringResource(R.string.miracast_system_settings_desc), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Cast, null) },
                        onClick = {
                            castMenuOpen = false
                            launchMiracastPicker(context)
                                .onFailure { error ->
                                    playerError = error.message ?: context.getString(R.string.miracast_settings_failed)
                                    controlsVisible = true
                                }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cast_hotspot_hint), style = MaterialTheme.typography.labelSmall) },
                        enabled = false,
                        onClick = { castMenuOpen = false }
                    )
                }
            }
            }
                Surface(
                    color = Color.Black.copy(alpha = .38f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    IconButton(onClick = { streamInfoOpen = true; controlsVisible = true }) {
                        Icon(Icons.Default.Info, stringResource(R.string.player_stream_info), tint = Color.White)
                    }
                }
            }
        }

        if (languageMenuOpen) {
            AlertDialog(
                onDismissRequest = { languageMenuOpen = false },
                title = { Text(stringResource(R.string.change_language)) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(playerLanguages, key = Language::token) { language ->
                            TextButton(
                                onClick = {
                                    languageMenuOpen = false
                                    onProgress(position, duration, true)
                                    stopCastForNavigation()
                                    onLanguageChange(language)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (playback.stream.language == language) Icon(Icons.Default.Check, null)
                                Text(language.localizedLabel(), modifier = Modifier.padding(start = 8.dp).weight(1f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { languageMenuOpen = false }) { Text(stringResource(R.string.close)) } }
            )
        }

        if (hosterMenuOpen) {
            AlertDialog(
                onDismissRequest = { hosterMenuOpen = false },
                title = { Text(stringResource(R.string.change_hoster)) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(orderedHosters, key = { "${it.name}:${it.lang.token}" }) { hoster ->
                            TextButton(
                                onClick = {
                                    hosterMenuOpen = false
                                    onProgress(position, duration, true)
                                    stopCastForNavigation()
                                    onHosterChange(hoster)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (HosterCatalog.normalize(playback.stream.hoster) == HosterCatalog.normalize(hoster.name) && playback.stream.language == hoster.lang) {
                                    Icon(Icons.Default.Check, null)
                                }
                                Text(
                                    "${localizedHosterName(hoster.name)} · ${hoster.lang.localizedLabel()}",
                                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { hosterMenuOpen = false }) { Text(stringResource(R.string.close)) } }
            )
        }

        if (streamInfoOpen) {
            AlertDialog(
                onDismissRequest = { streamInfoOpen = false },
                title = { Text(stringResource(R.string.player_stream_info)) },
                text = {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            streamInfo.compactLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        PlayerStreamInfoRow(stringResource(R.string.player_stream_format), streamInfo.formatLabel)
                        PlayerStreamInfoRow(stringResource(R.string.player_stream_mime), streamInfo.mimeType)
                        if (streamInfo.host.isNotBlank()) {
                            PlayerStreamInfoRow(stringResource(R.string.player_stream_host), streamInfo.host)
                        }
                        PlayerStreamInfoRow(
                            stringResource(R.string.player_stream_type),
                            stringResource(if (streamInfo.adaptive) R.string.player_stream_adaptive else R.string.player_stream_progressive)
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.player_stream_link),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        SelectionContainer {
                            Text(
                                playback.stream.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = ::copyStreamLink) {
                        Icon(Icons.Default.ContentCopy, null)
                        Text(" " + stringResource(R.string.copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { streamInfoOpen = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (chromecastDialogOpen) {
            AlertDialog(
                onDismissRequest = { chromecastDialogOpen = false },
                title = { Text(stringResource(R.string.chromecast_button)) },
                text = {
                    AndroidView(
                        modifier = Modifier.size(72.dp),
                        factory = { routeContext ->
                            FrameLayout(routeContext).apply {
                                val routeView = runCatching {
                                    MediaRouteButton(routeContext).apply {
                                        contentDescription = routeContext.getString(R.string.chromecast_button)
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        CastButtonFactory.setUpMediaRouteButton(routeContext, this)
                                    }
                                }.getOrElse {
                                    TextView(routeContext).apply {
                                        text = routeContext.getString(R.string.chromecast_unavailable)
                                        gravity = Gravity.CENTER
                                    }
                                }
                                addView(
                                    routeView,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        Gravity.CENTER
                                    )
                                )
                            }
                        },
                        update = { chromecastController.prepare(playback, position) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { chromecastDialogOpen = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (manualCastDialogOpen) {
            AlertDialog(
                onDismissRequest = { manualCastDialogOpen = false },
                title = { Text(stringResource(R.string.xbox_cast_manual_title)) },
                text = {
                    OutlinedTextField(
                        value = manualCastAddress,
                        onValueChange = { manualCastAddress = it.filter { character -> character.isDigit() || character == '.' } },
                        label = { Text(stringResource(R.string.xbox_cast_manual_hint)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = manualCastAddress.isNotBlank(),
                        onClick = {
                            manualCastDialogOpen = false
                            castMenuOpen = true
                            playerError = null
                            dlnaController.discoverAt(manualCastAddress)
                            fcastController.discoverAt(manualCastAddress)
                        }
                    ) { Text(stringResource(R.string.xbox_cast_manual_connect)) }
                },
                dismissButton = {
                    TextButton(onClick = { manualCastDialogOpen = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
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
                val knownDuration = duration.takeIf { it > 0L && it != Long.MAX_VALUE }
                val timelineMaximum = knownDuration ?: maxOf(position, playback.startPositionMs, 1L)
                val displayedPosition = (if (scrubbing) scrubPosition.toLong() else position)
                    .coerceIn(0L, timelineMaximum)
                Slider(
                    value = displayedPosition.toFloat(),
                    onValueChange = { value ->
                        if (knownDuration != null) {
                            scrubbing = true
                            scrubPosition = value
                            controlsVisible = true
                            controlsGeneration++
                        }
                    },
                    onValueChangeFinished = {
                        knownDuration?.let { maximum ->
                            val target = scrubPosition.toLong().coerceIn(0L, maximum)
                            seekPlayback(target)
                            scrubbing = false
                        }
                    },
                    valueRange = 0f..timelineMaximum.toFloat(),
                    enabled = knownDuration != null,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = .30f),
                        disabledThumbColor = Color.White.copy(alpha = .72f),
                        disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .72f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = .22f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(displayedPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        knownDuration?.let(::formatTime) ?: "--:--",
                        color = Color.White.copy(alpha = .86f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.ContentCopy, null, tint = Color.White) },
                            label = stringResource(R.string.player_copy_stream_link),
                            onClick = ::copyStreamLink
                        )
                    }
                    item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.Info, null, tint = Color.White) },
                            label = stringResource(R.string.player_stream_info),
                            onClick = {
                                controlsVisible = true
                                streamInfoOpen = true
                            }
                        )
                    }
                    if (playerLanguages.isNotEmpty()) item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.Language, null, tint = Color.White) },
                            label = stringResource(R.string.change_language),
                            onClick = { controlsVisible = true; languageMenuOpen = true }
                        )
                    }
                    if (orderedHosters.isNotEmpty()) item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.Tune, null, tint = Color.White) },
                            label = stringResource(R.string.change_hoster),
                            onClick = { controlsVisible = true; hosterMenuOpen = true }
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.PictureInPictureAlt, null, tint = Color.White) },
                            label = stringResource(R.string.picture_in_picture),
                            onClick = ::enterPictureInPicture
                        )
                    }
                    if (allowExternalPlayer) item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White) },
                            label = stringResource(R.string.open_external_player),
                            onClick = {
                                onProgress(position, duration, true)
                                stopCastForNavigation()
                                launchExternalPlayback(context, playback)
                                    .onSuccess { closePlayer() }
                                    .onFailure { error ->
                                        playerError = if (error is android.content.ActivityNotFoundException) context.getString(R.string.external_player_not_found)
                                        else error.message ?: context.getString(R.string.external_player_failed)
                                    }
                            }
                        )
                    }
                    item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.SkipPrevious, null, tint = if (hasPrevious) Color.White else Color.White.copy(alpha = .32f)) },
                            label = stringResource(R.string.player_previous_episode),
                            enabled = hasPrevious,
                            onClick = {
                                autoNextVisible = false
                                onProgress(position, duration, true)
                                stopCastForNavigation()
                                onPrevious()
                            }
                        )
                    }
                    item {
                        PlayerNavigationAction(
                            icon = { Icon(Icons.Default.SkipNext, null, tint = if (hasNext) Color.White else Color.White.copy(alpha = .32f)) },
                            label = stringResource(R.string.player_next_episode),
                            enabled = hasNext,
                            onClick = {
                                autoNextVisible = false
                                onProgress(position, duration, true)
                                stopCastForNavigation()
                                onNext()
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val target = (position - 10_000L).coerceAtLeast(0L)
                        seekPlayback(target)
                    }) {
                        Icon(Icons.Default.Replay10, stringResource(R.string.seek_back_seconds), tint = Color.White)
                    }
                    IconButton(onClick = {
                        seekPlayback(0L)
                    }) {
                        Icon(Icons.Default.Replay, stringResource(R.string.play_from_beginning), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val target = knownDuration?.let { (position + 10_000L).coerceAtMost(it) }
                            ?: (position + 10_000L)
                        seekPlayback(target)
                    }) {
                        Icon(Icons.Default.Forward10, stringResource(R.string.seek_forward_seconds), tint = Color.White)
                    }
                }
                Text(stringResource(R.string.player_gesture_hint), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                playerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            }
        }
    }
}

@Composable
private fun PlayerStreamInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(.42f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer(Modifier.weight(.58f)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlayerNavigationAction(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(104.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(
                label,
                color = if (enabled) Color.White else Color.White.copy(alpha = .32f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
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
