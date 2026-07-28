@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package io.github.lootdev78.aniworld

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(st: UiState, vm: AppViewModel) {
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = st.homeLoading,
            onRefresh = { vm.loadHome(true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (st.homeLoading && st.homeFeed.isEmpty) item { HomeSkeleton() }
                st.homeError?.let { message -> item { ErrorCard(message) { vm.loadHome(true) } } }
                if (!st.homeLoading && st.homeError == null && st.homeFeed.isEmpty) item {
                    EmptyState(stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_subtitle), Modifier.fillMaxWidth().height(260.dp))
                }
                if (st.homeFeed.news.isNotEmpty()) item {
                    HomeNewsRow(st.homeFeed.news) { news -> vm.openManualPage(news.url, news.title) }
                }
                st.homeFeed.featured?.let { series ->
                    item {
                        HomeHero(
                            series,
                            st.preferences.isFavorite(series.slug),
                            { vm.select(series) },
                            { vm.toggleFavorite(series) },
                            { vm.openAnimeInfo(series) }
                        )
                    }
                }
                val continueWatching = st.preferences.progress.values.sortedByDescending { it.updatedAt }.take(12)
                if (continueWatching.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.continue_watching), stringResource(R.string.your_last_position))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(continueWatching, key = { it.seriesSlug }) { progress ->
                                ContinueCard(
                                    progress,
                                    st.preferences,
                                    { vm.openProgress(progress) },
                                    {
                                        vm.openAnimeInfo(
                                            Series(
                                                title = progress.seriesTitle,
                                                slug = progress.seriesSlug,
                                                url = progress.seriesUrl,
                                                coverUrl = progress.coverUrl
                                            )
                                        )
                                    },
                                    { vm.toggleProgressWatched(progress) }
                                )
                            }
                        }
                    }
                }
                if (st.homeFeed.popularAtAniWorld.isNotEmpty()) item {
                    val title = stringResource(R.string.popular_at_aniworld)
                    HomeSeriesRow(title, st.homeFeed.popularAtAniWorld, st, vm) { vm.openSeriesCollection(title, st.homeFeed.popularAtAniWorld) }
                }
                if (st.homeFeed.latestEpisodes.isNotEmpty()) item {
                    val title = stringResource(R.string.latest_episodes)
                    LatestEpisodesRow(st.homeFeed.latestEpisodes, st, vm) { vm.openEpisodeCollection(title, st.homeFeed.latestEpisodes) }
                }
                if (st.homeFeed.newAnimes.isNotEmpty()) item {
                    val title = stringResource(R.string.new_animes)
                    HomeSeriesRow(title, st.homeFeed.newAnimes, st, vm) { vm.openSeriesCollection(title, st.homeFeed.newAnimes) }
                }
                if (st.homeFeed.currentlyPopular.isNotEmpty()) item {
                    val title = stringResource(R.string.currently_popular)
                    HomeSeriesRow(title, st.homeFeed.currentlyPopular, st, vm) { vm.openSeriesCollection(title, st.homeFeed.currentlyPopular) }
                }
                if (st.homeFeed.communityWatching.isNotEmpty()) item {
                    val title = stringResource(R.string.community_watching)
                    HomeSeriesRow(title, st.homeFeed.communityWatching, st, vm) { vm.openSeriesCollection(title, st.homeFeed.communityWatching) }
                }
                if (st.homeFeed.mostWatched.isNotEmpty()) item {
                    val title = stringResource(R.string.most_watched_top_50)
                    HomeSeriesRow(title, st.homeFeed.mostWatched, st, vm) { vm.openSeriesCollection(title, st.homeFeed.mostWatched) }
                }
            }
        }
    }
}

@Composable
private fun HomeNewsRow(news: List<HomeNews>, onOpen: (HomeNews) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.anime_news), stringResource(R.string.live_from_aniworld))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(news, key = { it.url }) { item ->
                Card(
                    modifier = Modifier.width(286.dp).clickable { onOpen(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .86f))
                ) {
                    Box(Modifier.fillMaxWidth().height(150.dp)) {
                        AnimeImage(item.imageUrl, item.title, item.url, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.Newspaper, stringResource(R.string.anime_news), Modifier.padding(7.dp).size(18.dp))
                        }
                        Text(
                            item.title,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            item.subtitle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    series: Series,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp).combinedClickable(
            onClick = onOpen,
            onLongClick = onInfo
        )
    ) {
        AnimeImage(series.coverUrl, series.title, series.slug, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.05f), Color.Black.copy(.35f), MaterialTheme.colorScheme.background))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(series.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            if (series.genres.isNotEmpty()) Text(series.genres.take(4).joinToString("  •  "), color = MaterialTheme.colorScheme.secondary)
            if (series.description.isNotBlank()) Text(series.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpen) { Icon(Icons.Default.PlayArrow, null); Text(stringResource(R.string.open)) }
                OutlinedButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null); Text(if (favorite) stringResource(R.string.remove) else stringResource(R.string.remember)) }
            }
        }
    }
}

@Composable
private fun HomeSeriesRow(
    title: String,
    series: List<Series>,
    st: UiState,
    vm: AppViewModel,
    onShowAll: () -> Unit
) {
    val uniqueSeries = remember(series) { series.distinctBy(Series::slug) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, stringResource(R.string.titles_count, uniqueSeries.size), onShowAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uniqueSeries.take(10), key = { it.slug }) { item ->
                AnimePosterCard(
                    series = item,
                    favorite = st.preferences.isFavorite(item.slug),
                    onOpen = { vm.select(item) },
                    onFavorite = { vm.toggleFavorite(item) },
                    onVisible = { vm.enrichLiveSeries(item) },
                    watchedCount = st.preferences.watchedCount(item.slug),
                    onInfo = { vm.openAnimeInfo(item) }
                )
            }
            if (uniqueSeries.size > 10) item { MoreCard(uniqueSeries.size - 10, onShowAll) }
        }
    }
}

@Composable
private fun LatestEpisodesRow(
    episodes: List<HomeEpisode>,
    st: UiState,
    vm: AppViewModel,
    onShowAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.latest_episodes), stringResource(R.string.new_episodes_and_movies), onShowAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(episodes.take(10), key = { it.episode.url }) { item ->
                LaunchedEffect(item.series.slug) { vm.enrichLiveSeries(item.series) }
                Card(
                    modifier = Modifier.width(260.dp).combinedClickable(
                        onClick = { vm.openHomeEpisode(item) },
                        onLongClick = { vm.openEpisodeInfo(item.episode) }
                    )
                ) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cover(item.series.coverUrl, item.series.title, item.series.slug, Modifier.width(82.dp).aspectRatio(.70f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item.series.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.episode.localizedLabel()} · ${item.episode.localizedDisplayTitle()}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            if (item.releasedAt.isNotBlank()) Text(item.releasedAt, style = MaterialTheme.typography.labelSmall)
                            val state = st.preferences.episodeWatchStates[item.episode.key]
                            if (state?.completed == true) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                                        Text(stringResource(R.string.watched), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (episodes.size > 10) item { MoreCard(episodes.size - 10, onShowAll) }
        }
    }
}

@Composable
private fun MoreCard(remaining: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(154.dp).aspectRatio(.68f)) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ChevronRight, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.show_more), fontWeight = FontWeight.Bold)
            if (remaining > 0) Text(stringResource(R.string.more_items_count, remaining), style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun <T> SearchFieldWithSuggestions(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<T>,
    suggestionTitle: (T) -> String,
    suggestionSubtitle: (T) -> String = { "" },
    onSuggestion: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val visibleSuggestions = suggestions.take(3)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                    }
                }
            },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled
        )
        if (value.isNotBlank() && visibleSuggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    visibleSuggestions.forEachIndexed { index, item ->
                        val subtitle = suggestionSubtitle(item)
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSuggestion(item) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(suggestionTitle(item), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            if (subtitle.isNotBlank()) {
                                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < visibleSuggestions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val items = st.filteredCatalog
    val catalogSuggestions = remember(items, st.catalogQuery) {
        if (st.catalogQuery.isBlank()) emptyList() else items.take(3)
    }
    val isGrid = st.preferences.catalogViewMode == LibraryViewMode.GRID

    LaunchedEffect(st.catalogQuery, st.catalogGenre, st.preferences.catalogViewMode) {
        controlsVisible = true
        if (isGrid) gridState.scrollToItem(0) else listState.scrollToItem(0)
    }
    LaunchedEffect(listState, isGrid) {
        if (isGrid) return@LaunchedEffect
        var previous = 0
        snapshotFlow { listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset }
            .collect { current ->
                controlsVisible = current <= previous || current < 32
                previous = current
            }
    }
    LaunchedEffect(gridState, isGrid) {
        if (!isGrid) return@LaunchedEffect
        var previous = 0
        snapshotFlow { gridState.firstVisibleItemIndex * 100_000 + gridState.firstVisibleItemScrollOffset }
            .collect { current ->
                controlsVisible = current <= previous || current < 32
                previous = current
            }
    }
    if (st.catalog.items.isEmpty() && !st.catalogLoading) LaunchedEffect(Unit) { vm.loadCatalog() }

    PullToRefreshBox(
        isRefreshing = st.catalogLoading || st.catalogMetadataUpdating,
        onRefresh = { vm.loadCatalog(true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
            ) {
                Column {
                    SearchFieldWithSuggestions(
                        value = st.catalogQuery,
                        onValueChange = vm::setCatalogQuery,
                        label = stringResource(R.string.catalog_search_all),
                        suggestions = catalogSuggestions,
                        suggestionTitle = { it.title },
                        suggestionSubtitle = { it.genres.take(3).joinToString(" · ") },
                        onSuggestion = vm::select,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        enabled = st.catalogMetadataReady
                    )
                    if (st.catalogQuery.isBlank() && st.preferences.recentSearches.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(st.preferences.recentSearches.take(8), key = { it.query }) { entry ->
                                AssistChip(onClick = { vm.setCatalogQuery(entry.query) }, enabled = st.catalogMetadataReady, label = { Text(entry.query) })
                            }
                            item {
                                AssistChip(
                                    onClick = vm::clearRecentSearches,
                                    enabled = st.catalogMetadataReady,
                                    label = { Text(stringResource(R.string.clear)) },
                                    leadingIcon = { Icon(Icons.Default.ClearAll, null, Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item {
                            FilterChip(
                                selected = st.catalogGenre == null,
                                onClick = { vm.setCatalogGenre(null) },
                                enabled = st.catalogMetadataReady,
                                label = { Text(stringResource(R.string.all_genres)) },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp)) }
                            )
                        }
                        items(st.catalog.genres, key = { it }) { genre ->
                            FilterChip(selected = st.catalogGenre == genre, onClick = { vm.setCatalogGenre(genre) }, enabled = st.catalogMetadataReady, label = { Text(genre) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.catalog_count, items.size, st.catalog.items.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        ViewModeSelector(st.preferences.catalogViewMode, vm::setCatalogViewMode, enabled = st.catalogMetadataReady)
                    }
                }
            }

            if (!st.catalogMetadataReady) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CatalogSkeleton(Modifier.fillMaxSize())
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 6.dp
                    ) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (st.catalogMetadataUpdating) CircularProgressIndicator()
                            Text(stringResource(R.string.catalog_metadata_required), fontWeight = FontWeight.Bold)
                            Text(
                                st.catalogMetadataError
                                    ?: if (st.catalogMetadataUpdating && st.catalogMetadataTotal > 0) stringResource(R.string.catalog_metadata_progress, st.catalogMetadataCompleted, st.catalogMetadataTotal)
                                    else stringResource(R.string.catalog_metadata_required_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (st.catalogMetadataError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!st.catalogLoading && items.isEmpty()) {
                EmptyState(stringResource(R.string.no_anime_found), stringResource(R.string.adjust_catalog_filters), Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (st.preferences.catalogViewMode) {
                        LibraryViewMode.GRID -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (expanded) 190.dp else 145.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().padding(end = 28.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(items, key = { it.slug }) { series ->
                                AnimePosterCard(
                                    series = series,
                                    favorite = st.preferences.isFavorite(series.slug),
                                    onOpen = { vm.select(series) },
                                    onFavorite = { vm.toggleFavorite(series) },
                                    onInfo = { vm.openAnimeInfo(series) },
                                    onVisible = { vm.enrichCatalogItem(series) },
                                    modifier = Modifier.fillMaxWidth(),
                                    watchedCount = st.preferences.watchedCount(series.slug)
                                )
                            }
                        }
                        LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 28.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(items, key = { it.slug }) { series ->
                                CatalogListCard(
                                    series = series,
                                    favorite = st.preferences.isFavorite(series.slug),
                                    watchedCount = st.preferences.watchedCount(series.slug),
                                    detailed = st.preferences.catalogViewMode == LibraryViewMode.DETAILED,
                                    onOpen = { vm.select(series) },
                                    onFavorite = { vm.toggleFavorite(series) },
                                    onInfo = { vm.openAnimeInfo(series) },
                                    onVisible = { vm.enrichCatalogItem(series) }
                                )
                            }
                        }
                    }
                    val firstVisible = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    val activeLetter = items.getOrNull(firstVisible)?.title?.firstOrNull()?.let { if (it.isLetter()) it.uppercase() else "#" }
                    CatalogAlphabetRail(
                        letters = st.catalog.letters,
                        activeLetter = activeLetter,
                        onLetter = { letter ->
                            val index = items.indexOfFirst { item ->
                                val first = item.title.firstOrNull()
                                if (letter == "#") first != null && !first.isLetter() else first?.uppercase() == letter
                            }
                            if (index >= 0) scope.launch {
                                controlsVisible = false
                                if (isGrid) gridState.animateScrollToItem(index) else listState.animateScrollToItem(index)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                    if (!controlsVisible) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clickable { controlsVisible = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)
                        ) {
                            Icon(Icons.Default.Search, stringResource(R.string.show_catalog_controls), Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogAlphabetRail(
    letters: List<String>,
    activeLetter: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.padding(end = 2.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    modifier = Modifier.clickable { onLetter(letter) }.padding(horizontal = 5.dp, vertical = 1.dp),
                    color = if (letter == activeLetter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (letter == activeLetter) FontWeight.Black else FontWeight.Medium,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    var filter by rememberSaveable { mutableStateOf("") }
    var selectedSlugs by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val allFavorites = orderFavorites(st.preferences)
    val favoriteSuggestions = remember(allFavorites, filter) {
        if (filter.isBlank()) emptyList() else allFavorites.filter { entry ->
            entry.title.contains(filter, true) || entry.genres.any { it.contains(filter, true) }
        }.take(3).map { it.asSeries() }
    }
    val ordered = allFavorites.filter { entry ->
        filter.isBlank() || entry.title.contains(filter, true) || entry.genres.any { genre -> genre.contains(filter, true) }
    }
    val selectionMode = selectedSlugs.isNotEmpty()
    val quickTargets = libraryQuickTargets(st.preferences.favoriteSort, ordered.map { it.title }, ordered.map { it.updatedAt })
    val isGrid = st.preferences.favoritesViewMode == LibraryViewMode.GRID
    val activeIndex = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex

    fun toggleSelection(slug: String) {
        selectedSlugs = selectedSlugs.toMutableSet().apply { if (!add(slug)) remove(slug) }
    }

    Column(Modifier.fillMaxSize()) {
        LibraryScreenControls(
            filter = filter,
            onFilter = { filter = it },
            sort = st.preferences.favoriteSort,
            onSort = { selectedSlugs = emptySet(); vm.setFavoriteSort(it) },
            viewMode = st.preferences.favoritesViewMode,
            onViewMode = { selectedSlugs = emptySet(); vm.setFavoritesViewMode(it) },
            suggestions = favoriteSuggestions,
            onSuggestion = { series -> st.preferences.favorites.firstOrNull { it.slug == series.slug }?.let(vm::selectFavorite) }
        )
        if (selectionMode) {
            LibrarySelectionToolbar(
                count = selectedSlugs.size,
                allSelected = selectedSlugs.size == ordered.size && ordered.isNotEmpty(),
                onSelectAll = { selectedSlugs = if (selectedSlugs.size == ordered.size) emptySet() else ordered.map { it.slug }.toSet() },
                onDelete = { confirmDelete = true },
                onCancel = { selectedSlugs = emptySet() }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ordered.isEmpty()) {
                EmptyState(stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_hint), Modifier.fillMaxSize())
            } else {
                when (st.preferences.favoritesViewMode) {
                    LibraryViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (expanded) 190.dp else 150.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(end = 30.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(ordered, key = { it.slug }) { entry ->
                            val selected = entry.slug in selectedSlugs
                            AnimePosterCard(
                                series = entry.asSeries(),
                                favorite = true,
                                onOpen = { if (selectionMode) toggleSelection(entry.slug) else vm.selectFavorite(entry) },
                                onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                                onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                                onVisible = { vm.enrichCatalogItem(entry.asSeries()) },
                                modifier = Modifier.fillMaxWidth(),
                                watchedCount = st.preferences.watchedCount(entry.slug),
                                selectionMode = selectionMode,
                                selected = selected,
                                onLongClick = { toggleSelection(entry.slug) }
                            )
                        }
                    }
                    LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 30.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ordered, key = { it.slug }) { entry ->
                            LaunchedEffect(entry.slug) { vm.enrichCatalogItem(entry.asSeries()) }
                            val watched = st.preferences.watchedCount(entry.slug)
                            val selected = entry.slug in selectedSlugs
                            LibraryCard(
                                series = entry.asSeries(),
                                subtitle = if (watched > 0) stringResource(R.string.watched_episodes_count, watched) else "",
                                favorite = true,
                                compact = st.preferences.favoritesViewMode == LibraryViewMode.COMPACT,
                                onOpen = { if (selectionMode) toggleSelection(entry.slug) else vm.selectFavorite(entry) },
                                onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                                onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                                moveUp = if (!selectionMode && st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, -1) }) else null,
                                moveDown = if (!selectionMode && st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, 1) }) else null,
                                selectionMode = selectionMode,
                                selected = selected,
                                onLongClick = { toggleSelection(entry.slug) }
                            )
                        }
                    }
                }
                if (quickTargets.size > 1) {
                    QuickIndexRail(
                        targets = quickTargets,
                        activeIndex = activeIndex,
                        onTarget = { target -> scope.launch { if (isGrid) gridState.animateScrollToItem(target.index) else listState.animateScrollToItem(target.index) } },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteSelectionDialog(
            count = selectedSlugs.size,
            onConfirm = {
                vm.removeFavorites(selectedSlugs)
                selectedSlugs = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

private enum class HistoryFilter(@StringRes val labelRes: Int) {
    ALL(R.string.all),
    IN_PROGRESS(R.string.started),
    COMPLETED(R.string.watched),
    FAVORITES(R.string.favorites)
}

@Composable
fun HistoryScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    var filter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var selectedSlugs by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val allWatched = orderWatched(st.preferences)
    val historySuggestions = remember(allWatched, filter) {
        if (filter.isBlank()) emptyList() else allWatched.filter { it.title.contains(filter, true) }.take(3).map { it.asSeries() }
    }
    val ordered = allWatched.filter { entry ->
        val states = st.preferences.episodeWatchStates.values.filter { it.seriesSlug == entry.slug }
        val statusMatches = when (statusFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.IN_PROGRESS -> states.any { !it.completed && it.positionMs > 0L }
            HistoryFilter.COMPLETED -> states.isNotEmpty() && states.all { it.completed }
            HistoryFilter.FAVORITES -> st.preferences.isFavorite(entry.slug)
        }
        statusMatches && (filter.isBlank() || entry.title.contains(filter, true))
    }
    val selectionMode = selectedSlugs.isNotEmpty()
    val quickTargets = libraryQuickTargets(st.preferences.watchedSort, ordered.map { it.title }, ordered.map { it.updatedAt })
    val isGrid = st.preferences.historyViewMode == LibraryViewMode.GRID
    val activeIndex = if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex

    fun toggleSelection(slug: String) {
        selectedSlugs = selectedSlugs.toMutableSet().apply { if (!add(slug)) remove(slug) }
    }

    Column(Modifier.fillMaxSize()) {
        LibraryScreenControls(
            filter = filter,
            onFilter = { filter = it },
            sort = st.preferences.watchedSort,
            onSort = { selectedSlugs = emptySet(); vm.setWatchedSort(it) },
            viewMode = st.preferences.historyViewMode,
            onViewMode = { selectedSlugs = emptySet(); vm.setHistoryViewMode(it) },
            suggestions = historySuggestions,
            onSuggestion = { series -> allWatched.firstOrNull { it.slug == series.slug }?.let(vm::selectWatched) }
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { item ->
                FilterChip(selected = statusFilter == item, onClick = { selectedSlugs = emptySet(); statusFilter = item }, label = { Text(stringResource(item.labelRes)) })
            }
        }
        if (selectionMode) {
            LibrarySelectionToolbar(
                count = selectedSlugs.size,
                allSelected = selectedSlugs.size == ordered.size && ordered.isNotEmpty(),
                onSelectAll = { selectedSlugs = if (selectedSlugs.size == ordered.size) emptySet() else ordered.map { it.slug }.toSet() },
                onDelete = { confirmDelete = true },
                onCancel = { selectedSlugs = emptySet() }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ordered.isEmpty()) {
                EmptyState(stringResource(R.string.no_history), stringResource(R.string.no_history_hint), Modifier.fillMaxSize())
            } else {
                when (st.preferences.historyViewMode) {
                    LibraryViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (expanded) 190.dp else 150.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(end = 30.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(ordered, key = { it.slug }) { entry ->
                            val selected = entry.slug in selectedSlugs
                            AnimePosterCard(
                                series = entry.asSeries(),
                                favorite = st.preferences.isFavorite(entry.slug),
                                onOpen = { if (selectionMode) toggleSelection(entry.slug) else vm.selectWatched(entry) },
                                onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                                onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                                onVisible = { vm.enrichCatalogItem(entry.asSeries()) },
                                modifier = Modifier.fillMaxWidth(),
                                watchedCount = entry.watchedEpisodes,
                                selectionMode = selectionMode,
                                selected = selected,
                                onLongClick = { toggleSelection(entry.slug) }
                            )
                        }
                    }
                    LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 30.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ordered, key = { it.slug }) { entry ->
                            LaunchedEffect(entry.slug) { vm.enrichCatalogItem(entry.asSeries()) }
                            val selected = entry.slug in selectedSlugs
                            LibraryCard(
                                series = entry.asSeries(),
                                subtitle = if (entry.watchedEpisodes > 0) {
                                    stringResource(R.string.history_item_subtitle, entry.watchedEpisodes, entry.latestSeason, entry.latestEpisode)
                                } else {
                                    stringResource(R.string.history_item_started_subtitle, entry.latestSeason, entry.latestEpisode)
                                },
                                favorite = st.preferences.isFavorite(entry.slug),
                                compact = st.preferences.historyViewMode == LibraryViewMode.COMPACT,
                                onOpen = { if (selectionMode) toggleSelection(entry.slug) else vm.selectWatched(entry) },
                                onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                                onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                                moveUp = if (!selectionMode && st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, -1) }) else null,
                                moveDown = if (!selectionMode && st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, 1) }) else null,
                                onDelete = if (!selectionMode) ({ vm.removeWatched(entry.slug) }) else null,
                                selectionMode = selectionMode,
                                selected = selected,
                                onLongClick = { toggleSelection(entry.slug) }
                            )
                        }
                    }
                }
                if (quickTargets.size > 1) {
                    QuickIndexRail(
                        targets = quickTargets,
                        activeIndex = activeIndex,
                        onTarget = { target -> scope.launch { if (isGrid) gridState.animateScrollToItem(target.index) else listState.animateScrollToItem(target.index) } },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteSelectionDialog(
            count = selectedSlugs.size,
            onConfirm = {
                vm.removeWatched(selectedSlugs)
                selectedSlugs = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false }
        )
    }
}


private data class QuickTarget(val label: String, val index: Int)

@Composable
private fun libraryQuickTargets(sort: LibrarySort, titles: List<String>, updatedAt: List<Long>): List<QuickTarget> {
    val recentLabel = stringResource(R.string.quick_recent)
    val weekLabel = stringResource(R.string.quick_week)
    val monthLabel = stringResource(R.string.quick_month)
    val olderLabel = stringResource(R.string.quick_older)
    return remember(sort, titles, updatedAt, recentLabel, weekLabel, monthLabel, olderLabel) {
        when (sort) {
            LibrarySort.ALPHABETICAL -> titles.mapIndexed { index, title ->
                val label = title.trim().firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#"
                QuickTarget(label, index)
            }.distinctBy { it.label }
            LibrarySort.UPDATED -> {
                val now = System.currentTimeMillis()
                val day = 24L * 60L * 60L * 1000L
                listOf(
                    recentLabel to { age: Long -> age < day },
                    weekLabel to { age: Long -> age in day until (7L * day) },
                    monthLabel to { age: Long -> age in (7L * day) until (30L * day) },
                    olderLabel to { age: Long -> age >= 30L * day }
                ).mapNotNull { (label, predicate) ->
                    updatedAt.indexOfFirst { value -> predicate((now - value).coerceAtLeast(0L)) }
                        .takeIf { it >= 0 }?.let { QuickTarget(label, it) }
                }
            }
            LibrarySort.CUSTOM -> {
                if (titles.isEmpty()) emptyList() else listOf(
                    QuickTarget("1", 0),
                    QuickTarget("¼", titles.lastIndex / 4),
                    QuickTarget("½", titles.lastIndex / 2),
                    QuickTarget("¾", titles.lastIndex * 3 / 4),
                    QuickTarget("↓", titles.lastIndex)
                ).distinctBy { it.index }
            }
        }
    }
}

@Composable
private fun QuickIndexRail(
    targets: List<QuickTarget>,
    activeIndex: Int,
    onTarget: (QuickTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = targets.lastOrNull { it.index <= activeIndex } ?: targets.firstOrNull()
    Surface(
        modifier = modifier.padding(end = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            targets.forEach { target ->
                Text(
                    text = target.label,
                    modifier = Modifier.clickable { onTarget(target) }.padding(horizontal = 5.dp, vertical = 3.dp),
                    color = if (target == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (target == active) FontWeight.Black else FontWeight.Medium,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LibrarySelectionToolbar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.selected_count, count), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = onSelectAll) {
                Icon(if (allSelected) Icons.Default.DoneAll else Icons.Default.SelectAll, stringResource(R.string.select_all))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun DeleteSelectionDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_selected_title)) },
        text = { Text(stringResource(R.string.delete_selected_message, count)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun LibraryScreenControls(
    filter: String,
    onFilter: (String) -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    viewMode: LibraryViewMode? = null,
    onViewMode: (LibraryViewMode) -> Unit = {},
    suggestions: List<Series> = emptyList(),
    onSuggestion: (Series) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SearchFieldWithSuggestions(
            value = filter,
            onValueChange = onFilter,
            label = stringResource(R.string.search_in_list),
            suggestions = suggestions,
            suggestionTitle = { it.title },
            suggestionSubtitle = { it.genres.take(3).joinToString(" · ") },
            onSuggestion = onSuggestion,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LibrarySort.entries) { item ->
                    FilterChip(
                        selected = sort == item,
                        onClick = { onSort(item) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
            if (viewMode != null) ViewModeSelector(viewMode, onViewMode)
        }
    }
}

@Composable
private fun ViewModeSelector(mode: LibraryViewMode, onMode: (LibraryViewMode) -> Unit, enabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { onMode(LibraryViewMode.COMPACT) }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.view_compact), tint = if (mode == LibraryViewMode.COMPACT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMode(LibraryViewMode.DETAILED) }, enabled = enabled) {
            Icon(Icons.Default.ViewAgenda, stringResource(R.string.view_detailed), tint = if (mode == LibraryViewMode.DETAILED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMode(LibraryViewMode.GRID) }, enabled = enabled) {
            Icon(Icons.Default.GridView, stringResource(R.string.view_grid), tint = if (mode == LibraryViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class EpisodeFilter(@StringRes val labelRes: Int) { ALL(R.string.all), UNWATCHED(R.string.unwatched), STARTED(R.string.started), WATCHED(R.string.watched) }

@Composable
fun DetailScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    val series = st.selected ?: return
    var episodeFilter by rememberSaveable(series.slug, st.season) { mutableStateOf(EpisodeFilter.ALL) }
    if (st.season == null) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = st.detailScrollIndex,
            initialFirstVisibleItemScrollOffset = st.detailScrollOffset
        )
        DisposableEffect(listState) {
            onDispose { vm.saveDetailScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                NetflixDetailHeader(series, st, vm, expanded)
            }
            item {
                SectionTitle(stringResource(R.string.seasons_and_movies), stringResource(R.string.areas_count, st.seasons.size))
            }
            items(st.seasons, key = { it }) { season ->
                Card(onClick = { vm.season(season) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        val watched = st.preferences.watchedCount(series.slug, season)
                        val total = st.preferences.seasonTotal(series.slug, season)
                        val completed = total != null && total > 0 && watched >= total
                        Icon(
                            if (completed) Icons.Default.CheckCircle else if (season == 0) Icons.Default.Movie else Icons.Default.PlayArrow,
                            null,
                            tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (season == 0) stringResource(R.string.movies) else stringResource(R.string.season_number, season), fontWeight = FontWeight.Bold)
                            if (watched > 0) {
                                Text(
                                    if (total != null && total > 0) stringResource(R.string.watched_out_of, watched, total)
                                    else stringResource(R.string.watched_count, watched),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            item { SeriesInfo(series, Modifier.padding(horizontal = 16.dp)) }
        }
    } else {
        val episodeListState = rememberLazyListState(
            initialFirstVisibleItemIndex = st.episodeScrollIndex,
            initialFirstVisibleItemScrollOffset = st.episodeScrollOffset
        )
        DisposableEffect(episodeListState) {
            onDispose { vm.saveEpisodeScroll(episodeListState.firstVisibleItemIndex, episodeListState.firstVisibleItemScrollOffset) }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::backToSeasons) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(series.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (st.season == 0) stringResource(R.string.movies) else stringResource(R.string.season_number, st.season ?: 0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { vm.toggleFavorite(series) }) {
                    Icon(if (st.preferences.isFavorite(series.slug)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite))
                }
                TextButton(onClick = vm::toggleSeasonWatched) { Icon(Icons.Default.DoneAll, null); Text(stringResource(R.string.toggle_all)) }
            }
            if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.seasons, key = { it }) { season ->
                    FilterChip(
                        selected = st.season == season,
                        onClick = { vm.season(season) },
                        label = { Text(if (season == 0) stringResource(R.string.movies) else stringResource(R.string.season_number, season)) }
                    )
                }
            }
            val quickEpisode = st.episodes.firstOrNull { st.preferences.episodeWatchStates[it.key]?.completed != true } ?: st.episodes.firstOrNull()
            if (quickEpisode != null) {
                Button(
                    onClick = { vm.playEpisode(quickEpisode) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(" " + stringResource(if ((st.preferences.episodeWatchStates[quickEpisode.key]?.positionMs ?: 0L) > 0L) R.string.continue_playback else R.string.play_now))
                }
            }
            if (st.seasonDescription.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(if (st.season == 0) R.string.movie_description else R.string.season_description), fontWeight = FontWeight.Bold)
                        Text(st.seasonDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EpisodeFilter.entries) { item ->
                    FilterChip(selected = episodeFilter == item, onClick = { episodeFilter = item }, label = { Text(stringResource(item.labelRes)) })
                }
            }
            val visibleEpisodes = st.episodes.filter { episode ->
                val watch = st.preferences.episodeWatchStates[episode.key]
                when (episodeFilter) {
                    EpisodeFilter.ALL -> true
                    EpisodeFilter.UNWATCHED -> watch == null || (!watch.completed && watch.positionMs <= 0L)
                    EpisodeFilter.STARTED -> watch != null && !watch.completed && watch.positionMs > 0L
                    EpisodeFilter.WATCHED -> watch?.completed == true
                }
            }
            if (visibleEpisodes.isEmpty() && !st.loading) {
                EmptyState(stringResource(R.string.no_matching_episodes), stringResource(R.string.no_matching_episodes_hint), Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = episodeListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleEpisodes, key = { it.key }) { episode ->
                        val watch = st.preferences.episodeWatchStates[episode.key]
                        Card(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { vm.inspectEpisode(episode) },
                                onLongClick = { vm.openEpisodeInfo(episode) }
                            )
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (watch?.completed == true) Icons.Default.CheckCircle else Icons.Default.PlayCircle, null, tint = if (watch?.completed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(episode.localizedLabel(), fontWeight = FontWeight.Bold)
                                    Text(episode.localizedDisplayTitle(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (episode.releasedAt.isNotBlank()) Text(episode.releasedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    if (episode.description.isNotBlank()) Text(episode.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (watch != null && !watch.completed && watch.positionMs > 0L) {
                                        LinearProgressIndicator(progress = { watch.progressFraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                        Text(formatPlaybackTimeRange(watch.positionMs, watch.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { vm.toggleEpisodeWatched(episode) }) {
                                    Icon(if (watch?.completed == true) Icons.Default.CheckCircle else Icons.Default.Add, stringResource(R.string.toggle_watched))
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
private fun NetflixDetailHeader(series: Series, st: UiState, vm: AppViewModel, expanded: Boolean) {
    Box(Modifier.fillMaxWidth().height(if (expanded) 500.dp else 430.dp)) {
        AnimeImage(series.coverUrl, series.title, series.slug, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .42f), MaterialTheme.colorScheme.background)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent, Color.Black.copy(alpha = .12f)))
            )
        )
        IconButton(onClick = vm::backToSearch, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Cover(series.coverUrl, series.title, series.slug, Modifier.width(if (expanded) 150.dp else 108.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(series.title, style = if (expanded) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                val meta = listOf(series.year, series.ageRating).filter(String::isNotBlank)
                if (meta.isNotEmpty()) Text(meta.joinToString("  •  "), color = Color.White.copy(alpha = .82f), fontWeight = FontWeight.SemiBold)
                if (series.genres.isNotEmpty()) Text(series.genres.take(5).joinToString("  •  "), color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                Text(series.description.ifBlank { stringResource(R.string.no_description) }, color = Color.White.copy(alpha = .88f), maxLines = if (expanded) 5 else 3, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = vm::resumeSelected) { Icon(Icons.Default.PlayArrow, null); Text(" " + stringResource(if (st.preferences.progress.containsKey(series.slug)) R.string.continue_playback else R.string.play_now)) }
                    OutlinedButton(onClick = { vm.toggleFavorite(series) }) {
                        Icon(if (st.preferences.isFavorite(series.slug)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                        Text(" " + stringResource(if (st.preferences.isFavorite(series.slug)) R.string.remove_favorite else R.string.favorite))
                    }
                    OutlinedButton(onClick = { vm.openAnimeInfo(series) }) { Icon(Icons.Default.Info, null); Text(" " + stringResource(R.string.info)) }
                }
            }
        }
    }
}

@Composable
private fun SeriesInfo(series: Series, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (series.directors.isNotEmpty()) InfoValue(stringResource(R.string.directors), series.directors.joinToString(", "))
        if (series.producers.isNotEmpty()) InfoValue(stringResource(R.string.producers), series.producers.joinToString(", "))
        if (series.actors.isNotEmpty()) InfoValue(stringResource(R.string.actors), series.actors.joinToString(", "))
        if (series.countries.isNotEmpty()) InfoValue(stringResource(R.string.countries), series.countries.joinToString(", "))
        if (series.userRating.isNotBlank()) {
            InfoValue(
                stringResource(R.string.user_rating),
                if (series.ratingCount > 0) stringResource(R.string.user_rating_with_count, series.userRating, series.ratingCount)
                else stringResource(R.string.user_rating_without_count, series.userRating)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogListCard(
    series: Series,
    favorite: Boolean,
    watchedCount: Int,
    detailed: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onVisible: () -> Unit
) {
    LaunchedEffect(series.slug) { onVisible() }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onOpen,
            onLongClick = onInfo
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (detailed) 12.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(series.coverUrl, series.title, series.slug, Modifier.width(if (detailed) 82.dp else 58.dp).aspectRatio(.70f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (series.genres.isNotEmpty()) Text(series.genres.take(if (detailed) 5 else 2).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                if (detailed && series.description.isNotBlank()) Text(series.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (watchedCount > 0) Text(stringResource(R.string.watched_episodes_count, watchedCount), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite))
            }
        }
    }
}

@Composable
private fun InfoValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimePosterCard(
    series: Series,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier.width(154.dp),
    onVisible: () -> Unit = {},
    watchedCount: Int = 0,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    LaunchedEffect(series.slug) { onVisible() }
    Card(
        modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onLongClick ?: onInfo),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f) else Color.Transparent
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box {
            Cover(series.coverUrl, series.title, series.slug, Modifier.fillMaxWidth().aspectRatio(.68f))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f)))))
            if (watchedCount > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = .72f)
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" $watchedCount", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    stringResource(if (selected) R.string.selected else R.string.not_selected),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White
                )
            } else {
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White)
                }
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(series.genres.take(2).joinToString(" · ").ifBlank { stringResource(R.string.anime) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeListCard(
    series: Series,
    favorite: Boolean,
    watchedCount: Int,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onInfo)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(series.coverUrl, series.title, series.slug, Modifier.width(90.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2)
                if (series.genres.isNotEmpty()) Text(series.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (series.description.isNotBlank()) Text(series.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (watchedCount > 0) Text(stringResource(R.string.watched_episodes_count, watchedCount), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite)) }
        }
    }
}

@Composable
private fun LibraryCard(
    series: Series,
    subtitle: String,
    favorite: Boolean,
    compact: Boolean = false,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit = onOpen,
    moveUp: (() -> Unit)? = null,
    moveDown: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongClick ?: onInfo),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f)
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(
                series.coverUrl,
                series.title,
                series.slug,
                Modifier.width(if (compact) 62.dp else 78.dp).aspectRatio(.70f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    series.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!compact && series.genres.isNotEmpty()) {
                    Text(
                        series.genres.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            stringResource(if (selected) R.string.selected else R.string.not_selected),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (selected) stringResource(R.string.selected) else stringResource(R.string.tap_to_select),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorite)
                            )
                        }
                        IconButton(onClick = onInfo, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Info, stringResource(R.string.details))
                        }
                        moveUp?.let { action ->
                            IconButton(onClick = action, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up))
                            }
                        }
                        moveDown?.let { action ->
                            IconButton(onClick = action, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down))
                            }
                        }
                        onDelete?.let { action ->
                            IconButton(onClick = action, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
            Icon(
                if (selectionMode && selected) Icons.Default.CheckCircle else if (selectionMode) Icons.Default.RadioButtonUnchecked else Icons.Default.ChevronRight,
                stringResource(if (selectionMode && selected) R.string.selected else if (selectionMode) R.string.not_selected else R.string.open_anime),
                tint = if (selectionMode && selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatPlaybackClock(valueMs: Long): String {
    val totalSeconds = (valueMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatPlaybackTimeRange(positionMs: Long, durationMs: Long): String =
    "${formatPlaybackClock(positionMs)} – ${formatPlaybackClock(durationMs)}"

@Composable
private fun ContinueCard(
    progress: ProgressEntry,
    prefs: AppPreferences,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val state = prefs.episodeWatchStates[episodeKey(progress.seriesSlug, progress.season, progress.episode)]
    Card(modifier = Modifier.width(270.dp).combinedClickable(onClick = onOpen, onLongClick = onInfo)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(progress.coverUrl, progress.seriesTitle, progress.seriesSlug, Modifier.width(80.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(progress.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${progress.localizedEpisodeLabel()} · ${progress.localizedEpisodeTitle()}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                state?.takeIf { it.progressFraction > 0f }?.let {
                    LinearProgressIndicator(progress = { it.progressFraction }, modifier = Modifier.fillMaxWidth())
                    Text(formatPlaybackTimeRange(it.positionMs, it.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleWatched) {
                Icon(
                    if (state?.completed == true) Icons.Default.CheckCircle else Icons.Default.DoneAll,
                    stringResource(if (state?.completed == true) R.string.mark_unwatched else R.string.mark_watched),
                    tint = if (state?.completed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Cover(url: String, title: String, animeKey: String, modifier: Modifier) {
    Surface(modifier.clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
        AnimeImage(url, title, animeKey, Modifier.fillMaxSize(), ContentScale.Crop)
    }
}

@Composable
private fun AnimeImage(url: String, title: String, animeKey: String, modifier: Modifier, contentScale: ContentScale) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    if (!isUsableAnimeImage(url) || failed) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, title, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("anime-cover:${animeKey.trim().lowercase()}:$url")
                .diskCacheKey("anime-cover:${animeKey.trim().lowercase()}:$url")
                .build(),
            contentDescription = title,
            modifier = modifier,
            contentScale = contentScale,
            onError = { failed = true }
        )
    }
}

private fun isUsableAnimeImage(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    return listOf(
        "aniworld_logo", "aniworld-logo", "/logo.", "/logos/", "favicon", "placeholder",
        "loading", "spinner", "avatar", "profile", "facebook", "twitter", "instagram",
        "discord", "yandex", "tracking", "pixel.gif", "blank.gif", "transparent"
    ).none(lower::contains)
}

@Composable
private fun SectionTitle(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(
            if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.Default.ChevronRight, stringResource(R.string.show_all))
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(Modifier.fillMaxWidth().height(330.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.padding(horizontal = 16.dp).width(190.dp).height(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {}
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(5) { Surface(Modifier.width(150.dp).aspectRatio(.68f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {} }
                }
            }
        }
    }
}

@Composable
private fun CatalogSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(12) { Surface(Modifier.fillMaxWidth().aspectRatio(.68f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {} }
    }
}

@Composable
private fun LoadingBlock(text: String) = Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(10.dp)); Text(text) } }

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) = Card(Modifier.padding(16.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error); Text(message); Button(onClick = retry) { Text(stringResource(R.string.retry)) } } }

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier.fillMaxSize()
) = Box(
    modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    contentAlignment = Alignment.Center
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppErrorBanner(message: String, onLogs: () -> Unit, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(12.dp), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null)
            Text(message, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onLogs) { Icon(Icons.Default.BugReport, stringResource(R.string.details)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}

private fun orderFavorites(prefs: AppPreferences): List<FavoriteEntry> = when (prefs.favoriteSort) {
    LibrarySort.ALPHABETICAL -> prefs.favorites.sortedBy { it.title.lowercase() }
    LibrarySort.UPDATED -> prefs.favorites.sortedByDescending { it.updatedAt }
    LibrarySort.CUSTOM -> prefs.favorites.sortedBy { entry -> prefs.favoriteOrder.indexOf(entry.slug).let { if (it < 0) Int.MAX_VALUE else it } }
}

private fun orderWatched(prefs: AppPreferences): List<WatchedSeriesEntry> = when (prefs.watchedSort) {
    LibrarySort.ALPHABETICAL -> prefs.watchedSeries().sortedBy { it.title.lowercase() }
    LibrarySort.UPDATED -> prefs.watchedSeries().sortedByDescending { it.updatedAt }
    LibrarySort.CUSTOM -> prefs.watchedSeries().sortedBy { entry -> prefs.watchedOrder.indexOf(entry.slug).let { if (it < 0) Int.MAX_VALUE else it } }
}

@Composable
fun SeriesCollectionScreen(
    page: SeriesCollectionPage,
    st: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    onBack: () -> Unit
) {
    var query by rememberSaveable(page.title) { mutableStateOf("") }
    var viewMode by rememberSaveable(page.title) { mutableStateOf(LibraryViewMode.GRID) }
    val items = remember(page.items, query) {
        page.items.filter { query.isBlank() || it.title.contains(query, true) || it.genres.any { genre -> genre.contains(query, true) } }
    }
    val suggestions = remember(page.items, query) {
        if (query.isBlank()) emptyList() else page.items.filter { item ->
            item.title.contains(query, true) || item.genres.any { it.contains(query, true) }
        }.take(3)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.titles_count, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ViewModeSelector(viewMode, onMode = { viewMode = it })
        }
        SearchFieldWithSuggestions(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search_in_list),
            suggestions = suggestions,
            suggestionTitle = { it.title },
            suggestionSubtitle = { it.genres.take(3).joinToString(" · ") },
            onSuggestion = { series -> onBack(); vm.select(series) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )
        when (viewMode) {
            LibraryViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 190.dp else 150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items, key = { it.slug }) { series ->
                    AnimePosterCard(
                        series = series,
                        favorite = st.preferences.isFavorite(series.slug),
                        onOpen = { onBack(); vm.select(series) },
                        onFavorite = { vm.toggleFavorite(series) },
                        onInfo = { vm.openAnimeInfo(series) },
                        onVisible = { vm.enrichLiveSeries(series) },
                        modifier = Modifier.fillMaxWidth(),
                        watchedCount = st.preferences.watchedCount(series.slug)
                    )
                }
            }
            LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.slug }) { series ->
                    CatalogListCard(
                        series = series,
                        favorite = st.preferences.isFavorite(series.slug),
                        watchedCount = st.preferences.watchedCount(series.slug),
                        detailed = viewMode == LibraryViewMode.DETAILED,
                        onOpen = { onBack(); vm.select(series) },
                        onFavorite = { vm.toggleFavorite(series) },
                        onInfo = { vm.openAnimeInfo(series) },
                        onVisible = { vm.enrichLiveSeries(series) }
                    )
                }
            }
        }
    }
}

}
@Composable
fun EpisodeCollectionScreen(
    page: EpisodeCollectionPage,
    st: UiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable(page.title) { mutableStateOf("") }
    val items = remember(page.items, query) {
        page.items.filter { item ->
            query.isBlank() || item.series.title.contains(query, true) ||
                item.episode.title.contains(query, true) || item.episode.secondaryTitle.contains(query, true)
        }
    }
    val suggestions = remember(page.items, query) {
        if (query.isBlank()) emptyList() else page.items.filter { item ->
            item.series.title.contains(query, true) || item.episode.localizedDisplayTitle(context).contains(query, true)
        }.take(3)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.episodes_count, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SearchFieldWithSuggestions(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search_in_list),
            suggestions = suggestions,
            suggestionTitle = { it.series.title },
            suggestionSubtitle = { "${it.episode.localizedLabel(context)} · ${it.episode.localizedDisplayTitle(context)}" },
            onSuggestion = { item -> onBack(); vm.openHomeEpisode(item) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.episode.url }) { item ->
                Card(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onBack(); vm.openHomeEpisode(item) },
                    onLongClick = { vm.openEpisodeInfo(item.episode) }
                )) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Cover(item.series.coverUrl, item.series.title, item.series.slug, Modifier.width(82.dp).aspectRatio(.70f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.series.title, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text("${item.episode.localizedLabel()} · ${item.episode.localizedDisplayTitle()}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            if (item.releasedAt.isNotBlank()) Text(item.releasedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            if (item.episode.description.isNotBlank()) Text(item.episode.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            if (st.preferences.episodeWatchStates[item.episode.key]?.completed == true) Text(stringResource(R.string.watched), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

}
@Composable
fun EpisodeOptionsScreen(
    episode: Episode,
    hosters: List<Hoster>,
    prefs: AppPreferences,
    resolving: Boolean,
    allowExternalPlayer: Boolean,
    watched: Boolean,
    onDismiss: () -> Unit,
    onToggleWatched: () -> Unit,
    onAuto: () -> Unit,
    onExternal: () -> Unit,
    onLanguage: (Language) -> Unit,
    onHoster: (Hoster) -> Unit,
    onHosterWeb: (Hoster) -> Unit
) {
    val languages = hosters.map { it.lang }.filter { it != Language.UNKNOWN }.distinct()
    val ordered = hosters.sortedWith(compareBy<Hoster> {
        prefs.languagePriority.indexOf(it.lang).let { index -> if (index < 0) Int.MAX_VALUE else index }
    }.thenBy {
        prefs.hosterPriority.indexOfFirst { preferred -> HosterCatalog.normalize(preferred) == HosterCatalog.normalize(it.name) }
            .let { index -> if (index < 0) Int.MAX_VALUE else index }
    })
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            Text(episode.localizedLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(episode.localizedDisplayTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (episode.secondaryTitle.isNotBlank()) Text(episode.secondaryTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (episode.releasedAt.isNotBlank()) Text(episode.releasedAt, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.description), fontWeight = FontWeight.Bold)
                        Text(episode.description.ifBlank { stringResource(R.string.no_description) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onToggleWatched, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (watched) Icons.Default.CheckCircle else Icons.Default.DoneAll, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched))
                }
            }
            if (resolving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAuto, enabled = !resolving && hosters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.play_best_hoster))
                    }
                    if (allowExternalPlayer) {
                        OutlinedButton(onClick = onExternal, enabled = !resolving && hosters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.play_best_hoster_external))
                        }
                    }
                }
            }
            if (languages.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.choose_language), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = prefs.languagePriority.firstOrNull() == lang,
                                onClick = { onLanguage(lang) },
                                label = { Text(lang.localizedLabel()) },
                                leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.choose_hoster), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
            if (ordered.isEmpty()) {
                item { Text(stringResource(R.string.no_hosters_web_session), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                ordered.groupBy(Hoster::lang).forEach { (language, languageHosters) ->
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(language.localizedLabel(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.status_hosters_found, languageHosters.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(languageHosters, key = { it.redirectUrl }) { hoster ->
                        val preferred = ordered.firstOrNull()?.redirectUrl == hoster.redirectUrl
                        Card(
                            onClick = { onHoster(hoster) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (preferred) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f)
                            )
                        ) {
                            ListItem(
                                headlineContent = { Text(localizedHosterName(hoster.name), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(if (preferred) stringResource(R.string.preferred) else hoster.lang.localizedLabel()) },
                                leadingContent = {
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                                        Icon(Icons.Default.PlayArrow, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onHosterWeb(hoster) }) {
                                        Icon(Icons.Default.OpenInBrowser, stringResource(R.string.media_detector_open))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

}
@Composable
fun AnimeInfoScreen(
    series: Series,
    episode: Episode?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onOpenAnime: () -> Unit,
    onOpenImdb: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            Text(series.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 2)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.takeIf(String::isNotBlank)?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (series.coverUrl.isNotBlank()) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Cover(series.coverUrl, series.title, series.slug, Modifier.widthIn(max = 300.dp).aspectRatio(.70f))
                }
            }
            episode?.let { itemEpisode ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(itemEpisode.localizedLabel(), fontWeight = FontWeight.Bold)
                            Text(itemEpisode.localizedDisplayTitle(), style = MaterialTheme.typography.titleMedium)
                            if (itemEpisode.secondaryTitle.isNotBlank()) Text(itemEpisode.secondaryTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (itemEpisode.releasedAt.isNotBlank()) Text(itemEpisode.releasedAt, color = MaterialTheme.colorScheme.secondary)
                            Text(itemEpisode.description.ifBlank { stringResource(R.string.no_description) })
                        }
                    }
                }
            }
            item {
                val headline = listOf(series.year, series.ageRating).filter(String::isNotBlank)
                if (headline.isNotEmpty()) {
                    Text(
                        headline.joinToString("  •  "),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (episode == null) {
                    Text(series.description.ifBlank { stringResource(R.string.no_description) })
                }
            }
            if (series.genres.isNotEmpty()) item { InfoValue(stringResource(R.string.genres), series.genres.joinToString(" · ")) }
            if (series.directors.isNotEmpty()) item { InfoValue(stringResource(R.string.directors), series.directors.joinToString(", ")) }
            if (series.producers.isNotEmpty()) item { InfoValue(stringResource(R.string.producers), series.producers.joinToString(", ")) }
            if (series.actors.isNotEmpty()) item { InfoValue(stringResource(R.string.actors), series.actors.joinToString(", ")) }
            if (series.countries.isNotEmpty()) item { InfoValue(stringResource(R.string.countries), series.countries.joinToString(", ")) }
            if (series.userRating.isNotBlank()) item {
                InfoValue(
                    stringResource(R.string.user_rating),
                    if (series.ratingCount > 0) stringResource(R.string.user_rating_with_count, series.userRating, series.ratingCount)
                    else stringResource(R.string.user_rating_without_count, series.userRating)
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenAnime) { Text(stringResource(R.string.open_anime)) }
                    if (series.imdbUrl.isNotBlank()) OutlinedButton(onClick = onOpenImdb) { Text(stringResource(R.string.imdb)) }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

}
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCatalogMetadata() }
    val startMetadataRefresh = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.refreshCatalogMetadata()
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(stringResource(R.string.playback_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.preferred_language), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Language.DEFAULT_PRIORITY.forEach { lang ->
                        FilterChip(selected = prefs.languagePriority.firstOrNull() == lang, onClick = { vm.setPrimaryLanguage(lang) }, label = { Text(lang.localizedLabel()) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.hoster_order), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HosterCatalog.DEFAULT_PRIORITY.forEach { hoster ->
                        FilterChip(
                            selected = HosterCatalog.normalize(prefs.hosterPriority.firstOrNull().orEmpty()) == HosterCatalog.normalize(hoster),
                            onClick = { vm.setPrimaryHoster(hoster) },
                            label = { Text(stringResource(R.string.hoster_first, hoster)) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            item { SettingSwitch(stringResource(R.string.verify_stream_before_start), stringResource(R.string.verify_stream_before_start_desc), prefs.verifyStreams, vm::setVerifyStreams) }
            item { SettingSwitch(stringResource(R.string.auto_next), stringResource(R.string.auto_next_desc), prefs.autoNextEnabled, vm::setAutoNextEnabled) }
            item { SettingSwitch(stringResource(R.string.external_player), stringResource(R.string.external_player_desc), prefs.allowExternalPlayer, vm::setAllowExternalPlayer) }
            item { SettingSwitch(stringResource(R.string.auto_play_preferred_hoster), stringResource(R.string.auto_play_preferred_hoster_desc), prefs.autoPlayPreferredHoster, vm::setAutoPlayPreferredHoster) }
            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.web_adblock),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            item { SettingSwitch(stringResource(R.string.web_adblock), stringResource(R.string.web_adblock_desc), prefs.webAdBlockEnabled, vm::setWebAdBlockEnabled) }
            if (prefs.webAdBlockEnabled) {
                item {
                    Text(stringResource(R.string.web_filter_lists), fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            WebFilterList.ADVERTISING to R.string.web_filter_advertising,
                            WebFilterList.TRACKING to R.string.web_filter_tracking,
                            WebFilterList.POPUPS to R.string.web_filter_popups,
                            WebFilterList.REDIRECTS to R.string.web_filter_redirects
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = id in prefs.webFilterLists,
                                onClick = {
                                    val updated = prefs.webFilterLists.toMutableSet().apply {
                                        if (!add(id)) remove(id)
                                    }
                                    vm.setWebFilterLists(updated)
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.startup_area), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.startup_area_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(HomeTab.START, HomeTab.FAVORITES, HomeTab.HISTORY).forEach { tab ->
                        FilterChip(
                            selected = prefs.startupTab == tab.name,
                            onClick = { vm.setStartupTab(tab) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.display_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
            }
            item { SettingSwitch(stringResource(R.string.dynamic_colors), stringResource(R.string.dynamic_colors_desc), prefs.useDynamicColors, vm::setDynamicColors) }
            item {
                Text(stringResource(R.string.accent_color), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.accent_color_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppAccent.entries.forEach { accent ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                modifier = Modifier.size(46.dp).clip(CircleShape).clickable { vm.setAccentColor(accent) },
                                shape = CircleShape,
                                color = accentPreviewColor(accent),
                                tonalElevation = if (prefs.accentColor == accent) 8.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (prefs.accentColor == accent && !prefs.useDynamicColors) {
                                        Icon(Icons.Default.CheckCircle, stringResource(R.string.selected), tint = accentPreviewContentColor(accent))
                                    }
                                }
                            }
                            Text(stringResource(accent.labelRes()), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.catalog_and_cache), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            }
            item { OutlinedButton(onClick = startMetadataRefresh, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDownload, null); Text(" " + stringResource(R.string.update_metadata)) } }
            item { OutlinedButton(onClick = vm::resetCoverDataAndCache, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ClearAll, null); Text(" " + stringResource(R.string.reset_cover_cache)) } }
            item { OutlinedButton(onClick = vm::resetSettingsButtonPosition, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Tune, null); Text(" " + stringResource(R.string.reset_settings_button_position)) } }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.tools), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            }
            item { OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BugReport, null); Text(stringResource(R.string.diagnostics)) } }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.about_and_credits), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.credits_created_by), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/lootdev78"))) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Icon(Icons.Default.OpenInBrowser, null); Text(" GitHub · lootdev78") }
                OutlinedButton(
                    onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/lootdev78/aniworld-android"))) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Icon(Icons.Default.OpenInBrowser, null); Text(" aniworld-android") }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
    }
}

private fun AppAccent.labelRes(): Int = when (this) {
    AppAccent.RED -> R.string.color_red
    AppAccent.BLUE -> R.string.color_blue
    AppAccent.PURPLE -> R.string.color_purple
    AppAccent.GREEN -> R.string.color_green
    AppAccent.ORANGE -> R.string.color_orange
    AppAccent.CYAN -> R.string.color_cyan
    AppAccent.PINK -> R.string.color_pink
}

private fun accentPreviewColor(accent: AppAccent): Color = when (accent) {
    AppAccent.RED -> Color(0xFFFF4655)
    AppAccent.BLUE -> Color(0xFF74B4FF)
    AppAccent.PURPLE -> Color(0xFFC6A0FF)
    AppAccent.GREEN -> Color(0xFF64DFA0)
    AppAccent.ORANGE -> Color(0xFFFFB060)
    AppAccent.CYAN -> Color(0xFF62D8E8)
    AppAccent.PINK -> Color(0xFFFF8FC7)
}

private fun accentPreviewContentColor(accent: AppAccent): Color =
    if (accent == AppAccent.RED || accent == AppAccent.PURPLE || accent == AppAccent.PINK) Color.White else Color.Black

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
