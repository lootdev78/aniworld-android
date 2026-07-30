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
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Player-only palette inspired by the original AniWorld.to website.
private val AniWorldPlayerAccent = Color(0xFF627BEE)
private val AniWorldPlayerAccentStrong = Color(0xFF4C63C7)
private val AniWorldPlayerBackground = Color(0xFF101C24)
private val AniWorldPlayerPanel = Color(0xF21A2B35)
private val AniWorldPlayerControl = Color(0xFF263B49)
private val AniWorldPlayerTextMuted = Color(0xFFB9C5CC)
private val AniWorldPlayerDivider = Color(0xFF3A4D59)

private enum class PlayerCastProtocol { GOOGLE_CAST, SMART_VIEW, DLNA, FCAST }

private data class PlayerCastChoice(
    val protocol: PlayerCastProtocol,
    val label: String,
    val connect: () -> Unit
)

private fun normalizeCastDeviceName(value: String): String = java.text.Normalizer
    .normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("\\b(chromecast|google cast|smartview|smart view|dlna|upnp|fcast|renderer|media renderer)\\b"), " ")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .ifBlank { value.trim().lowercase() }

@Composable
fun PlayerScreen(
    playback: ResolvedPlayback,
    hasPrevious: Boolean,
    hasNext: Boolean,
    autoNextEnabled: Boolean,
    availableHosters: List<Hoster>,
    allowExternalPlayer: Boolean,
    castEnabled: Boolean,
    pipEnabled: Boolean,
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
    var moreMenuOpen by remember(playback.id) { mutableStateOf(false) }
    var manualCastDialogOpen by remember(playback.id) { mutableStateOf(false) }
    var selectedCastGroup by remember(playback.id) { mutableStateOf<String?>(null) }
    var manualCastAddress by remember(playback.id) { mutableStateOf("") }
    var streamInfoOpen by remember(playback.id) { mutableStateOf(false) }
    var internalPausedForCast by remember(playback.id) { mutableStateOf(false) }
    val playerOverlayOpen = languageMenuOpen || hosterMenuOpen || castMenuOpen || moreMenuOpen ||
        manualCastDialogOpen || streamInfoOpen || autoNextVisible || playerError != null
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
    val activeCastProtocol = when {
        smartViewActive -> PlayerCastProtocol.SMART_VIEW
        chromecastActive -> PlayerCastProtocol.GOOGLE_CAST
        fcastActive -> PlayerCastProtocol.FCAST
        dlnaActive -> PlayerCastProtocol.DLNA
        else -> null
    }
    val remoteVolumePercent = when (activeCastProtocol) {
        PlayerCastProtocol.SMART_VIEW -> smartViewState.volume
        PlayerCastProtocol.GOOGLE_CAST -> chromecastState.volumePercent
        PlayerCastProtocol.DLNA -> dlnaState.volume
        else -> null
    }
    val latestRemoteVolumePercent by rememberUpdatedState(remoteVolumePercent)
    val playerLanguages = remember(availableHosters, playback.stream.language) {
        (availableHosters.map { it.lang } + playback.stream.language).filter { it != Language.UNKNOWN }.distinct()
    }
    val orderedHosters = remember(availableHosters) {
        availableHosters.distinctBy { "${HosterCatalog.normalize(it.name)}:${it.lang.token}" }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val nearbyDevicesPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            selectedCastGroup = null
            castMenuOpen = true
            chromecastController.prepare(playback, position)
            chromecastController.discover()
            dlnaController.discover()
            fcastController.discover()
            smartViewController.discover()
        } else {
            playerError = context.getString(R.string.xbox_cast_permission_denied)
            controlsVisible = true
        }
    }

    LaunchedEffect(castEnabled) {
        if (!castEnabled) {
            castMenuOpen = false
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

    LaunchedEffect(controlsVisible, controlsGeneration, isPlaying, playerOverlayOpen) {
        if (controlsVisible && isPlaying && !playerOverlayOpen) {
            delay(3_200L)
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

    LaunchedEffect(castActive, activeCastProtocol) {
        if (!castActive || activeCastProtocol == PlayerCastProtocol.FCAST) return@LaunchedEffect
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        fun androidPercent(): Int = ((audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / max)
            .toInt().coerceIn(0, 100)
        fun setAndroidPercent(value: Int) {
            val target = ((value.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        fun sendRemote(value: Int) {
            when (activeCastProtocol) {
                PlayerCastProtocol.SMART_VIEW -> smartViewController.setVolume(value)
                PlayerCastProtocol.GOOGLE_CAST -> chromecastController.syncVolumeFromAndroid(value / 100f)
                PlayerCastProtocol.DLNA -> dlnaController.setVolume(value)
                else -> Unit
            }
        }

        var lastAndroid = androidPercent()
        var lastRemote = latestRemoteVolumePercent
        lastRemote?.let { if (kotlin.math.abs(it - lastAndroid) > 1) setAndroidPercent(it) }
        lastAndroid = androidPercent()

        while (castActive) {
            val currentAndroid = androidPercent()
            val currentRemote = latestRemoteVolumePercent
            when {
                currentRemote != null && currentRemote != lastRemote && kotlin.math.abs(currentRemote - currentAndroid) > 1 -> {
                    setAndroidPercent(currentRemote)
                    lastAndroid = currentRemote
                }
                currentAndroid != lastAndroid && (currentRemote == null || kotlin.math.abs(currentAndroid - currentRemote) > 1) -> {
                    sendRemote(currentAndroid)
                    lastAndroid = currentAndroid
                }
            }
            lastRemote = currentRemote
            delay(300L)
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
            selectedCastGroup = null
            castMenuOpen = true
            chromecastController.prepare(playback, position)
            chromecastController.discover()
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

    fun disconnectOtherCastProtocols(keep: PlayerCastProtocol) {
        if (keep != PlayerCastProtocol.SMART_VIEW && smartViewActive) smartViewController.disconnect()
        if (keep != PlayerCastProtocol.GOOGLE_CAST && chromecastActive) chromecastController.disconnect()
        if (keep != PlayerCastProtocol.FCAST && fcastActive) fcastController.disconnect()
        if (keep != PlayerCastProtocol.DLNA && dlnaActive) dlnaController.disconnect()
    }

    fun beginRemoteCast(protocol: PlayerCastProtocol) {
        playerError = null
        disconnectOtherCastProtocols(protocol)
        PlaybackService.pause(context)
        internalPausedForCast = true
        controlsVisible = true
        controlsGeneration++
    }

    fun stopCastForNavigation() {
        if (smartViewActive) smartViewController.disconnect()
        if (chromecastActive) chromecastController.disconnect()
        if (fcastActive) fcastController.disconnect()
        if (dlnaActive) dlnaController.disconnect()
        internalPausedForCast = false
    }

    fun closePlayer() {
        languageMenuOpen = false
        hosterMenuOpen = false
        castMenuOpen = false
        moreMenuOpen = false
        manualCastDialogOpen = false
        streamInfoOpen = false
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

    Box(Modifier.fillMaxSize().background(AniWorldPlayerBackground)) {
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
                        tint = AniWorldPlayerAccent,
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
                        color = AniWorldPlayerTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AniWorldPlayerBackground.copy(alpha = .92f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = AniWorldPlayerControl.copy(alpha = .92f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    IconButton(onClick = ::closePlayer) {
                        Icon(Icons.Default.Close, stringResource(R.string.player_close), tint = Color.White)
                    }
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        playback.seriesTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        PlayerMetadataBadge(stringResource(R.string.player_season_badge, playback.episode.season))
                        PlayerMetadataBadge(stringResource(R.string.player_episode_badge, playback.episode.number))
                        Text(
                            playback.episode.localizedDisplayTitle(context),
                            modifier = Modifier.weight(1f),
                            color = AniWorldPlayerTextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (languageMenuOpen) {
            AlertDialog(
                onDismissRequest = { languageMenuOpen = false; controlsGeneration++ },
                containerColor = AniWorldPlayerPanel,
                titleContentColor = Color.White,
                textContentColor = Color.White,
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
                                if (playback.stream.language == language) Icon(Icons.Default.Check, null, tint = AniWorldPlayerAccent)
                                Text(language.localizedLabel(), modifier = Modifier.padding(start = 8.dp).weight(1f), color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { languageMenuOpen = false }) { Text(stringResource(R.string.close), color = AniWorldPlayerAccent) } }
            )
        }

        if (hosterMenuOpen) {
            AlertDialog(
                onDismissRequest = { hosterMenuOpen = false; controlsGeneration++ },
                containerColor = AniWorldPlayerPanel,
                titleContentColor = Color.White,
                textContentColor = Color.White,
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
                                    Icon(Icons.Default.Check, null, tint = AniWorldPlayerAccent)
                                }
                                Text(
                                    "${localizedHosterName(hoster.name)} · ${hoster.lang.localizedLabel()}",
                                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { hosterMenuOpen = false }) { Text(stringResource(R.string.close), color = AniWorldPlayerAccent) } }
            )
        }

        if (streamInfoOpen) {
            AlertDialog(
                onDismissRequest = { streamInfoOpen = false; controlsGeneration++ },
                containerColor = AniWorldPlayerPanel,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(stringResource(R.string.player_stream_info)) },
                text = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        HorizontalDivider(color = AniWorldPlayerDivider)
                        Text(
                            stringResource(R.string.player_stream_link),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        SelectionContainer {
                            Text(
                                playback.stream.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = AniWorldPlayerTextMuted
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = ::copyStreamLink) {
                        Icon(Icons.Default.ContentCopy, null, tint = AniWorldPlayerAccent)
                        Text(" " + stringResource(R.string.copy), color = AniWorldPlayerAccent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { streamInfoOpen = false }) {
                        Text(stringResource(R.string.close), color = AniWorldPlayerAccent)
                    }
                }
            )
        }

        if (castMenuOpen) {
            val googleCastProtocolLabel = stringResource(R.string.cast_protocol_google)
            val smartViewProtocolLabel = stringResource(R.string.cast_protocol_smartview)
            val dlnaProtocolLabel = stringResource(R.string.cast_protocol_dlna)
            val fcastProtocolLabel = stringResource(R.string.cast_protocol_fcast)
            fun protocolLabel(protocol: PlayerCastProtocol): String = when (protocol) {
                PlayerCastProtocol.GOOGLE_CAST -> googleCastProtocolLabel
                PlayerCastProtocol.SMART_VIEW -> smartViewProtocolLabel
                PlayerCastProtocol.DLNA -> dlnaProtocolLabel
                PlayerCastProtocol.FCAST -> fcastProtocolLabel
            }

            val protocolMap = linkedMapOf<String, Pair<String, MutableList<PlayerCastChoice>>>()
            fun addCastChoice(name: String, protocol: PlayerCastProtocol, action: () -> Unit) {
                val key = normalizeCastDeviceName(name)
                val entry = protocolMap.getOrPut(key) { name.trim() to mutableListOf() }
                entry.second += PlayerCastChoice(protocol, protocolLabel(protocol), action)
            }

            chromecastState.devices.forEach { device ->
                addCastChoice(device.name, PlayerCastProtocol.GOOGLE_CAST) {
                    beginRemoteCast(PlayerCastProtocol.GOOGLE_CAST)
                    chromecastController.prepare(playback, position)
                    chromecastController.selectDevice(device.id)
                }
            }
            smartViewState.devices.filter { it.dmpSupported != false }.forEach { device ->
                addCastChoice(device.displayName, PlayerCastProtocol.SMART_VIEW) {
                    beginRemoteCast(PlayerCastProtocol.SMART_VIEW)
                    smartViewController.cast(device, playback, position)
                }
            }
            dlnaState.devices.forEach { device ->
                addCastChoice(device.displayName, PlayerCastProtocol.DLNA) {
                    beginRemoteCast(PlayerCastProtocol.DLNA)
                    dlnaController.cast(device, playback, position)
                }
            }
            fcastState.devices.forEach { device ->
                addCastChoice(device.displayName, PlayerCastProtocol.FCAST) {
                    beginRemoteCast(PlayerCastProtocol.FCAST)
                    fcastController.cast(device, playback, position)
                }
            }

            val activeKey = remoteDeviceName.takeIf(String::isNotBlank)?.let(::normalizeCastDeviceName)
            val grouped = protocolMap.mapNotNull { (key, entry) ->
                val filteredChoices = entry.second
                    .filterNot { key == activeKey && it.protocol == activeCastProtocol }
                    .distinctBy(PlayerCastChoice::protocol)
                    .sortedBy { it.protocol.ordinal }
                entry.first.takeIf { filteredChoices.isNotEmpty() }?.let { it to filteredChoices }
            }.sortedBy { it.first.lowercase() }
            val discovering = chromecastState.discovering || smartViewState.discovering ||
                dlnaState.discovering || fcastState.discovering
            val activeProtocolLabel = activeCastProtocol?.let(::protocolLabel).orEmpty()
            val activeVolume = remoteVolumePercent

            AlertDialog(
                onDismissRequest = {
                    castMenuOpen = false
                    selectedCastGroup = null
                    controlsGeneration++
                },
                containerColor = AniWorldPlayerPanel,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (castActive) Icons.Default.CastConnected else Icons.Default.Cast,
                            null,
                            tint = AniWorldPlayerAccent
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.local_cast), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.cast_unified_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = AniWorldPlayerTextMuted
                            )
                        }
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (castActive) {
                            item {
                                PlayerCastListItem(
                                    title = remoteDeviceName,
                                    subtitle = stringResource(R.string.cast_connected_via, activeProtocolLabel),
                                    connected = true,
                                    onClick = {
                                        castMenuOpen = false
                                        selectedCastGroup = null
                                        disconnectCastAndResume()
                                    }
                                )
                            }
                            activeVolume?.let { volume ->
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = AniWorldPlayerControl.copy(alpha = .56f),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    stringResource(R.string.cast_volume),
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text("${volume}%", color = AniWorldPlayerTextMuted)
                                            }
                                            Slider(
                                                value = volume.toFloat(),
                                                onValueChange = { value ->
                                                    val percent = value.toInt().coerceIn(0, 100)
                                                    when (activeCastProtocol) {
                                                        PlayerCastProtocol.SMART_VIEW -> smartViewController.setVolume(percent)
                                                        PlayerCastProtocol.GOOGLE_CAST -> chromecastController.syncVolumeFromAndroid(percent / 100f)
                                                        PlayerCastProtocol.DLNA -> dlnaController.setVolume(percent)
                                                        else -> Unit
                                                    }
                                                },
                                                valueRange = 0f..100f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = AniWorldPlayerAccent,
                                                    inactiveTrackColor = AniWorldPlayerDivider
                                                )
                                            )
                                            Text(
                                                stringResource(R.string.cast_volume_sync_desc),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AniWorldPlayerTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                            item { HorizontalDivider(color = AniWorldPlayerDivider) }
                        }

                        if (playback.stream.headers.keys.any { !it.equals("User-Agent", true) }) {
                            item {
                                Text(
                                    stringResource(R.string.cast_header_warning),
                                    color = AniWorldPlayerTextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (grouped.isEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (discovering) CircularProgressIndicator(color = AniWorldPlayerAccent, modifier = Modifier.size(28.dp))
                                    Icon(Icons.Default.Cast, null, tint = AniWorldPlayerTextMuted)
                                    Text(
                                        stringResource(if (discovering) R.string.cast_searching else R.string.cast_no_local_devices),
                                        color = AniWorldPlayerTextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        items(grouped, key = { normalizeCastDeviceName(it.first) }) { group ->
                            val displayName = group.first
                            val choices = group.second
                            val groupId = normalizeCastDeviceName(displayName) + "::" + choices.joinToString("|") { it.protocol.name }
                            PlayerCastListItem(
                                title = displayName,
                                subtitle = choices.joinToString(" · ") { it.label },
                                connected = false,
                                onClick = {
                                    if (choices.size == 1) {
                                        castMenuOpen = false
                                        selectedCastGroup = null
                                        choices.first().connect()
                                    } else {
                                        selectedCastGroup = if (selectedCastGroup == groupId) null else groupId
                                    }
                                }
                            )
                            if (selectedCastGroup == groupId) {
                                choices.forEach { choice ->
                                    PlayerCastProtocolItem(choice.label) {
                                        selectedCastGroup = null
                                        castMenuOpen = false
                                        choice.connect()
                                    }
                                }
                            }
                        }

                        item { HorizontalDivider(color = AniWorldPlayerDivider, modifier = Modifier.padding(top = 5.dp)) }
                        item {
                            PlayerCastActionItem(Icons.Default.Refresh, stringResource(R.string.cast_refresh_local)) {
                                selectedCastGroup = null
                                chromecastController.discover()
                                smartViewController.discover()
                                dlnaController.discover()
                                fcastController.discover()
                            }
                        }
                        item {
                            PlayerCastActionItem(Icons.Default.Tune, stringResource(R.string.cast_manual_address)) {
                                selectedCastGroup = null
                                castMenuOpen = false
                                manualCastDialogOpen = true
                            }
                        }
                        item {
                            PlayerCastActionItem(Icons.Default.Cast, stringResource(R.string.miracast_system_settings)) {
                                castMenuOpen = false
                                launchMiracastPicker(context).onFailure { error ->
                                    playerError = error.message ?: context.getString(R.string.miracast_settings_failed)
                                    controlsVisible = true
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.cast_hotspot_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = AniWorldPlayerTextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        castMenuOpen = false
                        selectedCastGroup = null
                        controlsGeneration++
                    }) {
                        Text(stringResource(R.string.close), color = AniWorldPlayerAccent)
                    }
                }
            )
        }


        if (manualCastDialogOpen) {
            AlertDialog(
                onDismissRequest = { manualCastDialogOpen = false; controlsGeneration++ },
                containerColor = AniWorldPlayerPanel,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text(stringResource(R.string.xbox_cast_manual_title)) },
                text = {
                    OutlinedTextField(
                        value = manualCastAddress,
                        onValueChange = { manualCastAddress = it.filter { character -> character.isDigit() || character == '.' } },
                        label = { Text(stringResource(R.string.xbox_cast_manual_hint)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AniWorldPlayerAccent,
                            unfocusedBorderColor = AniWorldPlayerDivider,
                            focusedLabelColor = AniWorldPlayerAccent,
                            unfocusedLabelColor = AniWorldPlayerTextMuted,
                            cursorColor = AniWorldPlayerAccent
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = manualCastAddress.isNotBlank(),
                        onClick = {
                            manualCastDialogOpen = false
                            selectedCastGroup = null
                            castMenuOpen = true
                            playerError = null
                            dlnaController.discoverAt(manualCastAddress)
                            fcastController.discoverAt(manualCastAddress)
                        }
                    ) { Text(stringResource(R.string.xbox_cast_manual_connect), color = AniWorldPlayerAccent) }
                },
                dismissButton = {
                    TextButton(onClick = { manualCastDialogOpen = false }) {
                        Text(stringResource(R.string.cancel), color = AniWorldPlayerAccent)
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
                    Button(
                        onClick = { autoNextVisible = false },
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AniWorldPlayerAccentStrong)
                    ) {
                        Text(stringResource(R.string.cancel), color = AniWorldPlayerAccent)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || playerError != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val knownDuration = duration.takeIf { it > 0L && it != Long.MAX_VALUE }
            val timelineMaximum = knownDuration ?: maxOf(position, playback.startPositionMs, 1L)
            val displayedPosition = (if (scrubbing) scrubPosition.toLong() else position).coerceIn(0L, timelineMaximum)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                color = AniWorldPlayerPanel,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                seekPlayback(scrubPosition.toLong().coerceIn(0L, maximum))
                                scrubbing = false
                            }
                        },
                        valueRange = 0f..timelineMaximum.toFloat(),
                        enabled = knownDuration != null,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = AniWorldPlayerAccent,
                            inactiveTrackColor = AniWorldPlayerControl
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(displayedPosition), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(knownDuration?.let(::formatTime) ?: "--:--", color = AniWorldPlayerTextMuted, style = MaterialTheme.typography.labelMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerRoundButton(Icons.Default.SkipPrevious, stringResource(R.string.player_previous_episode), enabled = hasPrevious, size = 46.dp) {
                            autoNextVisible = false; onProgress(position, duration, true); stopCastForNavigation(); onPrevious()
                        }
                        PlayerRoundButton(Icons.Default.Replay10, stringResource(R.string.seek_back_seconds), size = 50.dp) {
                            seekPlayback((position - 10_000L).coerceAtLeast(0L))
                        }
                        PlayerPrimaryButton(
                            icon = if ((castActive && remoteTransportState != XboxTransportState.PAUSED) || (!castActive && isPlaying)) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.playback_paused) else stringResource(R.string.play)
                        ) {
                            when {
                                smartViewActive -> smartViewController.togglePlayPause()
                                chromecastActive -> chromecastController.togglePlayPause()
                                fcastActive -> fcastController.togglePlayPause()
                                dlnaActive -> dlnaController.togglePlayPause()
                                isPlaying -> PlaybackService.pause(context)
                                else -> PlaybackService.play(context)
                            }
                            controlsGeneration++
                        }
                        PlayerRoundButton(Icons.Default.Forward10, stringResource(R.string.seek_forward_seconds), size = 50.dp) {
                            seekPlayback(knownDuration?.let { (position + 10_000L).coerceAtMost(it) } ?: (position + 10_000L))
                        }
                        PlayerRoundButton(Icons.Default.SkipNext, stringResource(R.string.player_next_episode), enabled = hasNext, size = 46.dp) {
                            autoNextVisible = false; onProgress(position, duration, true); stopCastForNavigation(); onNext()
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (castEnabled) {
                            PlayerFeatureButton(
                                if (castActive) Icons.Default.CastConnected else Icons.Default.Cast,
                                stringResource(R.string.local_cast),
                                active = castActive,
                                onClick = ::openCastPicker
                            )
                        }
                        if (playerLanguages.isNotEmpty()) {
                            PlayerFeatureButton(Icons.Default.Language, stringResource(R.string.change_language)) {
                                controlsVisible = true
                                controlsGeneration++
                                languageMenuOpen = true
                            }
                        }
                        if (orderedHosters.isNotEmpty()) {
                            PlayerFeatureButton(Icons.Default.Tune, stringResource(R.string.change_hoster)) {
                                controlsVisible = true
                                controlsGeneration++
                                hosterMenuOpen = true
                            }
                        }
                        if (pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            PlayerFeatureButton(
                                Icons.Default.PictureInPictureAlt,
                                stringResource(R.string.picture_in_picture),
                                onClick = ::enterPictureInPicture
                            )
                        }
                        PlayerFeatureButton(Icons.Default.Replay, stringResource(R.string.play_from_beginning)) {
                            seekPlayback(0L)
                        }
                        Box {
                            PlayerFeatureButton(Icons.Default.MoreVert, stringResource(R.string.player_more_options)) {
                                controlsVisible = true
                                controlsGeneration++
                                moreMenuOpen = true
                            }
                            DropdownMenu(
                                expanded = moreMenuOpen,
                                onDismissRequest = {
                                    moreMenuOpen = false
                                    controlsGeneration++
                                },
                                modifier = Modifier.width(280.dp).background(AniWorldPlayerPanel)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_stream_info), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = AniWorldPlayerAccent) },
                                    onClick = {
                                        moreMenuOpen = false
                                        streamInfoOpen = true
                                        controlsVisible = true
                                    }
                                )
                                if (allowExternalPlayer) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.open_external_player), color = Color.White) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AniWorldPlayerAccent) },
                                        onClick = {
                                            moreMenuOpen = false
                                            onProgress(position, duration, true)
                                            stopCastForNavigation()
                                            launchExternalPlayback(context, playback)
                                                .onSuccess { closePlayer() }
                                                .onFailure { error ->
                                                    playerError = if (error is android.content.ActivityNotFoundException) {
                                                        context.getString(R.string.external_player_not_found)
                                                    } else {
                                                        error.message ?: context.getString(R.string.external_player_failed)
                                                    }
                                                }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_copy_stream_link), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = AniWorldPlayerAccent) },
                                    onClick = {
                                        moreMenuOpen = false
                                        copyStreamLink()
                                    }
                                )
                            }
                        }
                    }
                    playerError?.let { message ->
                        Surface(
                            color = Color(0xFF7F2831).copy(alpha = .68f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(
                                    message,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3
                                )
                                IconButton(onClick = {
                                    playerError = null
                                    controlsGeneration++
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun PlayerRoundButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 5.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = if (enabled) .12f else .05f)
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription, tint = Color.White.copy(alpha = if (enabled) 1f else .28f), modifier = Modifier.size(size * .52f))
        }
    }
}

@Composable
private fun PlayerPrimaryButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 8.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = AniWorldPlayerAccentStrong,
        shadowElevation = 12.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(68.dp)) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun PlayerFeatureButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    var labelVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier.padding(horizontal = 5.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (labelVisible) {
            Surface(
                modifier = Modifier.padding(bottom = 56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = .94f),
                shadowElevation = 8.dp
            ) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    semanticsOnClick {
                        onClick()
                        true
                    }
                }
                .pointerInput(label, onClick) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onPress = {
                            coroutineScope {
                                val tooltipJob = launch {
                                    delay(420L)
                                    labelVisible = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                tryAwaitRelease()
                                tooltipJob.cancel()
                                labelVisible = false
                            }
                        }
                    )
                },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (active) AniWorldPlayerAccent.copy(alpha = .34f) else AniWorldPlayerControl.copy(alpha = .96f),
            shadowElevation = if (active) 7.dp else 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) AniWorldPlayerAccent else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerMetadataBadge(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
        color = AniWorldPlayerAccent.copy(alpha = .20f)
    ) {
        Text(
            label,
            color = AniWorldPlayerAccent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun PlayerCastListItem(
    title: String,
    subtitle: String,
    connected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (connected) AniWorldPlayerAccent.copy(alpha = .16f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (connected) AniWorldPlayerAccent.copy(alpha = .28f) else AniWorldPlayerControl,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(
                    if (connected) Icons.Default.CastConnected else Icons.Default.Cast,
                    null,
                    tint = if (connected) AniWorldPlayerAccent else Color.White,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AniWorldPlayerTextMuted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun PlayerCastProtocolItem(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = AniWorldPlayerControl.copy(alpha = .50f),
        modifier = Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Cast, null, tint = AniWorldPlayerAccent, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = 10.dp), color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PlayerCastActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = AniWorldPlayerAccent, modifier = Modifier.size(20.dp))
            Text(label, modifier = Modifier.padding(start = 12.dp), color = Color.White)
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
            color = AniWorldPlayerTextMuted
        )
        SelectionContainer(Modifier.weight(.58f)) {
            Text(
                value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
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
