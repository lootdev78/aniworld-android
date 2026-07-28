package de.dxmoc.aniworld

import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

private const val DETAIL_ROUTE = "detail"

class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<String?>(null)
    private val playbackAction = MutableStateFlow<String?>(null)
    private var playbackReceiverRegistered = false
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            playbackAction.value = intent?.action
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.dataString
        if (!playbackReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                playbackReceiver,
                IntentFilter().apply {
                    addAction(PlaybackService.ACTION_PREVIOUS)
                    addAction(PlaybackService.ACTION_NEXT)
                    addAction(PlaybackService.ACTION_STOPPED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            playbackReceiverRegistered = true
        }
        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val link by deepLink.collectAsStateWithLifecycle()
            val mediaAction by playbackAction.collectAsStateWithLifecycle()
            LaunchedEffect(link) { vm.openDeepLink(link) }
            LaunchedEffect(mediaAction) {
                when (mediaAction) {
                    PlaybackService.ACTION_PREVIOUS -> vm.playPreviousEpisode()
                    PlaybackService.ACTION_NEXT -> vm.playNextEpisode()
                    PlaybackService.ACTION_STOPPED -> vm.closePlayer()
                }
                if (mediaAction != null) playbackAction.value = null
            }
            AniWorldTheme(useDynamicColors = state.preferences.useDynamicColors) {
                AniWorldApp(vm)
            }
        }
    }

    override fun onDestroy() {
        if (playbackReceiverRegistered) {
            unregisterReceiver(playbackReceiver)
            playbackReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.dataString
    }
}

@Composable
fun AniWorldTheme(useDynamicColors: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fallback = darkColorScheme(
        primary = Color(0xFFE50914),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF680006),
        secondary = Color(0xFFB8C4FF),
        tertiary = Color(0xFF73DCE8),
        background = Color(0xFF07080B),
        surface = Color(0xFF111319),
        surfaceVariant = Color(0xFF1B1E25),
        onBackground = Color(0xFFF4F4F6),
        onSurface = Color(0xFFF4F4F6),
        onSurfaceVariant = Color(0xFFC5C7CE),
        error = Color(0xFFFF6B6B)
    )
    val scheme = if (useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = fallback.background,
            surface = fallback.surface,
            surfaceVariant = fallback.surfaceVariant,
            onBackground = fallback.onBackground,
            onSurface = fallback.onSurface,
            onSurfaceVariant = fallback.onSurfaceVariant
        )
    } else fallback
    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
            content = content
        )
    }
}

enum class HomeTab(val route: String, @StringRes val labelRes: Int) {
    START("start", R.string.tab_start),
    CATALOG("catalog", R.string.tab_catalog),
    FAVORITES("favorites", R.string.tab_favorites),
    HISTORY("history", R.string.tab_history);

    companion object {
        fun fromRoute(route: String?): HomeTab = entries.firstOrNull { it.route == route } ?: START
        fun fromStored(value: String): HomeTab = entries.firstOrNull { it.name == value } ?: START
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniWorldApp(vm: AppViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: HomeTab.START.route
    val currentTab = HomeTab.fromRoute(currentRoute)
    var settingsOpen by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var permissionOpen by remember { mutableStateOf(false) }
    var restoredTab by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun navigateToTab(tab: HomeTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        vm.setLastHomeTab(tab)
        if (tab == HomeTab.CATALOG) vm.loadCatalog()
    }

    LaunchedEffect(st.preferencesReady, st.preferences.lastHomeTab) {
        if (st.preferencesReady && !restoredTab) {
            restoredTab = true
            val stored = HomeTab.fromStored(st.preferences.lastHomeTab)
            if (st.selected == null && stored != HomeTab.START) navigateToTab(stored)
        }
    }

    LaunchedEffect(st.status) {
        st.status?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.dismissStatus()
        }
    }

    LaunchedEffect(st.selected?.slug) {
        if (st.selected != null && navController.currentBackStackEntry?.destination?.route != DETAIL_ROUTE) {
            navController.navigate(DETAIL_ROUTE) { launchSingleTop = true }
        }
    }

    st.challenge?.let { request ->
        ChallengeScreen(
            request = request,
            checking = st.challengeChecking,
            sessionStatus = st.challengeStatus,
            adBlockEnabled = st.preferences.webAdBlockEnabled,
            enabledFilterLists = st.preferences.webFilterLists,
            sessionPanelExpanded = st.preferences.webSessionPanelExpanded,
            mediaPanelExpanded = st.preferences.webMediaPanelExpanded,
            onSessionPanelExpanded = vm::setWebSessionPanelExpanded,
            onMediaPanelExpanded = vm::setWebMediaPanelExpanded,
            onVerify = vm::verifyChallenge,
            onClearSession = vm::clearChallengeSession,
            onPlayDetectedMedia = vm::playDetectedMedia,
            onClose = vm::closeChallenge
        )
        return
    }
    st.playback?.let { playback ->
        val currentIndex = st.episodes.indexOfFirst { it.key == playback.episode.key }
        PlayerScreen(
            playback = playback,
            hasPrevious = currentIndex > 0,
            hasNext = currentIndex >= 0 && currentIndex < st.episodes.lastIndex,
            autoNextEnabled = st.preferences.autoNextEnabled,
            onPrevious = vm::playPreviousEpisode,
            onNext = vm::playNextEpisode,
            onClose = vm::closePlayer,
            onProgress = vm::onPlaybackProgress,
            onEnded = vm::onPlaybackEnded,
            onError = vm::reportPlayerError
        )
        return
    }

    st.infoSeries?.let { series ->
        BackHandler { vm.dismissInfoDialog() }
        AnimeInfoScreen(
            series = series,
            episode = st.infoEpisode,
            loading = st.infoLoading,
            error = st.infoError,
            onDismiss = vm::dismissInfoDialog,
            onOpenAnime = {
                vm.dismissInfoDialog()
                vm.closeCollection()
                vm.select(series)
            },
            onOpenImdb = {
                vm.dismissInfoDialog()
                vm.openManualPage(series.imdbUrl, "IMDb · ${series.title}")
            }
        )
        return
    }

    st.pendingEpisode?.let { episode ->
        BackHandler { vm.dismissEpisodeOptions() }
        EpisodeOptionsScreen(
            episode = episode,
            hosters = st.pendingHosters,
            prefs = st.preferences,
            resolving = st.resolving,
            onDismiss = vm::dismissEpisodeOptions,
            onAuto = { vm.playEpisode(episode) },
            onLanguage = { vm.playEpisode(episode, it) },
            onHoster = { hoster -> vm.playEpisode(episode, hoster.lang.takeIf { it != Language.UNKNOWN }, hoster) },
            onHosterWeb = { hoster -> vm.openHosterPage(episode, hoster) }
        )
        return
    }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(
            prefs = st.preferences,
            vm = vm,
            onDismiss = { settingsOpen = false },
            onPermissions = { permissionOpen = true },
            onDiagnostics = { settingsOpen = false; diagnosticsOpen = true }
        )
        if (diagnosticsOpen) DiagnosticDialog(st.diagnostics, vm::clearDiagnostics, { diagnosticsOpen = false }, context)
        if (permissionOpen) PermissionDialog(
            onDone = { vm.markPermissionIntroSeen(); permissionOpen = false },
            onDismiss = { vm.markPermissionIntroSeen(); permissionOpen = false }
        )
        return
    }

    val collectionExpanded = LocalConfiguration.current.screenWidthDp >= 720
    st.seriesCollection?.let { page ->
        BackHandler { vm.closeCollection() }
        SeriesCollectionScreen(page, st, vm, collectionExpanded, vm::closeCollection)
        return
    }
    st.episodeCollection?.let { page ->
        BackHandler { vm.closeCollection() }
        EpisodeCollectionScreen(page, st, vm, vm::closeCollection)
        return
    }

    val inDetail = currentRoute == DETAIL_ROUTE
    BackHandler(enabled = inDetail) {
        if (st.season != null) {
            vm.backToSeasons()
        } else {
            vm.backToSearch()
            navController.popBackStack()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp || maxWidth > maxHeight
        Row(Modifier.fillMaxSize()) {
            if (expanded && !inDetail) AppNavigationRail(currentTab, ::navigateToTab)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = { if (!expanded && !inDetail) AppBottomBar(currentTab, ::navigateToTab) }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(navController = navController, startDestination = HomeTab.START.route) {
                        composable(HomeTab.START.route) { HomeScreen(st, vm) }
                        composable(HomeTab.CATALOG.route) { CatalogScreen(st, vm, expanded) }
                        composable(HomeTab.FAVORITES.route) { FavoritesScreen(st, vm, expanded) }
                        composable(HomeTab.HISTORY.route) { HistoryScreen(st, vm, expanded) }
                        composable(DETAIL_ROUTE) {
                            if (st.selected != null) DetailScreen(st, vm, expanded)
                            else LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                    }
                    st.error?.let { AppErrorBanner(it, { diagnosticsOpen = true }, vm::dismissError) }
                    DraggableSettingsButton(
                        x = st.preferences.settingsButtonX,
                        y = st.preferences.settingsButtonY,
                        onOpen = { settingsOpen = true },
                        onPosition = vm::setSettingsButtonPosition,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (diagnosticsOpen) DiagnosticDialog(st.diagnostics, vm::clearDiagnostics, { diagnosticsOpen = false }, context)
    if (permissionOpen) PermissionDialog(
        onDone = { vm.markPermissionIntroSeen(); permissionOpen = false },
        onDismiss = { vm.markPermissionIntroSeen(); permissionOpen = false }
    )
}

@Composable
private fun DraggableSettingsButton(
    x: Float,
    y: Float,
    onOpen: () -> Unit,
    onPosition: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val buttonSize = 56.dp
        val buttonPx = with(density) { buttonSize.toPx() }
        val maxXPx = with(density) { maxWidth.toPx() }.minus(buttonPx).coerceAtLeast(0f)
        val maxYPx = with(density) { maxHeight.toPx() }.minus(buttonPx).coerceAtLeast(0f)
        var localX by remember(maxXPx, x) { mutableFloatStateOf(x.coerceIn(0f, 1f) * maxXPx) }
        var localY by remember(maxYPx, y) { mutableFloatStateOf(y.coerceIn(0f, 1f) * maxYPx) }

        androidx.compose.material3.SmallFloatingActionButton(
            onClick = onOpen,
            modifier = Modifier
                .offset { IntOffset(localX.roundToInt(), localY.roundToInt()) }
                .size(buttonSize)
                .pointerInput(maxXPx, maxYPx) {
                    detectDragGestures(
                        onDragEnd = {
                            onPosition(
                                if (maxXPx == 0f) 0f else localX / maxXPx,
                                if (maxYPx == 0f) 0f else localY / maxYPx
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            localX = (localX + dragAmount.x).coerceIn(0f, maxXPx)
                            localY = (localY + dragAmount.y).coerceIn(0f, maxYPx)
                        }
                    )
                }
        ) {
            Icon(Icons.Default.Settings, stringResource(R.string.settings))
        }
    }
}

@Composable
private fun AppBottomBar(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        containerColor = Color(0xF2111216),
        tonalElevation = 8.dp
    ) {
        HomeTab.entries.forEach { item ->
            NavigationBarItem(selected = tab == item, onClick = { onTab(item) }, icon = { Icon(tabIcon(item), null) }, label = { Text(stringResource(item.labelRes)) })
        }
    }
    }
}

@Composable
private fun AppNavigationRail(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavigationRail(Modifier.width(92.dp), containerColor = Color(0xFF0B0C10)) {
        HomeTab.entries.forEach { item ->
            NavigationRailItem(selected = tab == item, onClick = { onTab(item) }, icon = { Icon(tabIcon(item), null) }, label = { Text(stringResource(item.labelRes)) })
        }
    }
}

private fun tabIcon(tab: HomeTab) = when (tab) {
    HomeTab.START -> Icons.Default.Home
    HomeTab.CATALOG -> Icons.Default.Explore
    HomeTab.FAVORITES -> Icons.Default.Favorite
    HomeTab.HISTORY -> Icons.Default.History
}

@Composable
private fun PermissionDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions_privacy_title)) },
        text = {
            Text(stringResource(R.string.permissions_privacy_body))
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.understood)) } },
        dismissButton = {
            TextButton(onClick = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { AppLogger.error(context.getString(R.string.permissions_privacy_title), context.getString(R.string.app_settings_open_error), it) }
            }) { Text(stringResource(R.string.app_settings)) }
        }
    )
}

@Composable
fun DiagnosticDialog(entries: List<DiagnosticEntry>, onClear: () -> Unit, onDismiss: () -> Unit, context: Context) {
    val text = entries.joinToString("\n\n") { it.asText() }.ifBlank { context.getString(R.string.diagnostic_empty) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostic_title_count, entries.size)) },
        text = { androidx.compose.foundation.text.selection.SelectionContainer { androidx.compose.foundation.lazy.LazyColumn { item { Text(text, style = MaterialTheme.typography.bodySmall) } } } },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostic_clip_label), text))
                }.onFailure { AppLogger.error(context.getString(R.string.diagnostics), context.getString(R.string.diagnostic_copy_error), it) }
            }) { Text(stringResource(R.string.copy)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostic_share_subject))
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                context.getString(R.string.diagnostic_share_chooser)
                            )
                        )
                    }.onFailure { AppLogger.error(context.getString(R.string.diagnostics), context.getString(R.string.diagnostic_share_error), it) }
                }) { Text(stringResource(R.string.share)) }
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    )
}
