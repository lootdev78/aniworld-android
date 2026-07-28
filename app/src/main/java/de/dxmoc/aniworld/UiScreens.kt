@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package de.dxmoc.aniworld

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
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
                                    }
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
                    onVisible = { vm.enrichCatalogItem(item) },
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
                LaunchedEffect(item.series.slug) { vm.enrichCatalogItem(item.series) }
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
                            if (state?.completed == true) AssistChip(onClick = {}, label = { Text(stringResource(R.string.watched)) }, leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) })
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
fun CatalogScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val items = st.filteredCatalog
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
        isRefreshing = st.catalogLoading,
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
                    OutlinedTextField(
                        value = st.catalogQuery,
                        onValueChange = vm::setCatalogQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        label = { Text(stringResource(R.string.catalog_search_all)) },
                        singleLine = true
                    )
                    if (st.catalogQuery.isBlank() && st.preferences.recentSearches.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(st.preferences.recentSearches.take(8), key = { it.query }) { entry ->
                                AssistChip(onClick = { vm.setCatalogQuery(entry.query) }, label = { Text(entry.query) })
                            }
                            item {
                                AssistChip(
                                    onClick = vm::clearRecentSearches,
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
                                label = { Text(stringResource(R.string.all_genres)) },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp)) }
                            )
                        }
                        items(st.catalog.genres, key = { it }) { genre ->
                            FilterChip(selected = st.catalogGenre == genre, onClick = { vm.setCatalogGenre(genre) }, label = { Text(genre) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.catalog_count, items.size, st.catalog.items.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        ViewModeSelector(st.preferences.catalogViewMode, vm::setCatalogViewMode)
                    }
                }
            }

            if (st.catalog.items.isEmpty() && st.catalogLoading) {
                CatalogSkeleton(Modifier.weight(1f))
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
    val ordered = orderFavorites(st.preferences).filter { entry ->
        filter.isBlank() || entry.title.contains(filter, true) || entry.genres.any { genre -> genre.contains(filter, true) }
    }
    Column(Modifier.fillMaxSize()) {
        LibraryScreenHeader(
            title = stringResource(R.string.favorites),
            filter = filter,
            onFilter = { filter = it },
            sort = st.preferences.favoriteSort,
            onSort = vm::setFavoriteSort,
            viewMode = st.preferences.favoritesViewMode,
            onViewMode = vm::setFavoritesViewMode
        )
        if (ordered.isEmpty()) {
            EmptyState(stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_hint), Modifier.weight(1f))
        } else {
            when (st.preferences.favoritesViewMode) {
                LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (expanded) 190.dp else 150.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(ordered, key = { it.slug }) { entry ->
                        AnimePosterCard(
                            series = entry.asSeries(),
                            favorite = true,
                            onOpen = { vm.selectFavorite(entry) },
                            onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                            onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                            onVisible = { vm.enrichCatalogItem(entry.asSeries()) },
                            modifier = Modifier.fillMaxWidth(),
                            watchedCount = st.preferences.watchedCount(entry.slug)
                        )
                    }
                }
                LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ordered, key = { it.slug }) { entry ->
                        LaunchedEffect(entry.slug) { vm.enrichCatalogItem(entry.asSeries()) }
                        val watched = st.preferences.watchedCount(entry.slug)
                        LibraryCard(
                            series = entry.asSeries(),
                            subtitle = if (watched > 0) stringResource(R.string.watched_episodes_count, watched) else "",
                            favorite = true,
                            compact = st.preferences.favoritesViewMode == LibraryViewMode.COMPACT,
                            onOpen = { vm.selectFavorite(entry) },
                            onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                            onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                            moveUp = if (st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, -1) }) else null,
                            moveDown = if (st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, 1) }) else null
                        )
                    }
                }
            }
        }
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
    val ordered = orderWatched(st.preferences).filter { entry ->
        val states = st.preferences.episodeWatchStates.values.filter { it.seriesSlug == entry.slug }
        val statusMatches = when (statusFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.IN_PROGRESS -> states.any { !it.completed && it.positionMs > 0L }
            HistoryFilter.COMPLETED -> states.isNotEmpty() && states.all { it.completed }
            HistoryFilter.FAVORITES -> st.preferences.isFavorite(entry.slug)
        }
        statusMatches && (filter.isBlank() || entry.title.contains(filter, true))
    }
    Column(Modifier.fillMaxSize()) {
        LibraryScreenHeader(
            title = stringResource(R.string.history_title),
            filter = filter,
            onFilter = { filter = it },
            sort = st.preferences.watchedSort,
            onSort = vm::setWatchedSort,
            viewMode = st.preferences.historyViewMode,
            onViewMode = vm::setHistoryViewMode
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { item ->
                FilterChip(selected = statusFilter == item, onClick = { statusFilter = item }, label = { Text(stringResource(item.labelRes)) })
            }
        }
        if (ordered.isEmpty()) {
            EmptyState(stringResource(R.string.no_history), stringResource(R.string.no_history_hint), Modifier.weight(1f))
        } else {
            when (st.preferences.historyViewMode) {
                LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (expanded) 190.dp else 150.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(ordered, key = { it.slug }) { entry ->
                        AnimePosterCard(
                            series = entry.asSeries(),
                            favorite = st.preferences.isFavorite(entry.slug),
                            onOpen = { vm.selectWatched(entry) },
                            onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                            onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                            onVisible = { vm.enrichCatalogItem(entry.asSeries()) },
                            modifier = Modifier.fillMaxWidth(),
                            watchedCount = entry.watchedEpisodes
                        )
                    }
                }
                LibraryViewMode.COMPACT, LibraryViewMode.DETAILED -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ordered, key = { it.slug }) { entry ->
                        LaunchedEffect(entry.slug) { vm.enrichCatalogItem(entry.asSeries()) }
                        LibraryCard(
                            series = entry.asSeries(),
                            subtitle = stringResource(R.string.history_item_subtitle, entry.watchedEpisodes, entry.latestSeason, entry.latestEpisode),
                            favorite = st.preferences.isFavorite(entry.slug),
                            compact = st.preferences.historyViewMode == LibraryViewMode.COMPACT,
                            onOpen = { vm.selectWatched(entry) },
                            onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                            onInfo = { vm.openAnimeInfo(entry.asSeries()) },
                            moveUp = if (st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, -1) }) else null,
                            moveDown = if (st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, 1) }) else null,
                            onDelete = { vm.removeWatched(entry.slug) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreenHeader(
    title: String,
    filter: String,
    onFilter: (String) -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    viewMode: LibraryViewMode? = null,
    onViewMode: (LibraryViewMode) -> Unit = {}
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (viewMode != null) ViewModeSelector(viewMode, onViewMode) else Icon(Icons.Default.Sort, null)
        }
        OutlinedTextField(value = filter, onValueChange = onFilter, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), label = { Text(stringResource(R.string.search_in_list)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibrarySort.entries) { item -> FilterChip(selected = sort == item, onClick = { onSort(item) }, label = { Text(stringResource(item.labelRes)) }) }
        }
    }
}

@Composable
private fun ViewModeSelector(mode: LibraryViewMode, onMode: (LibraryViewMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { onMode(LibraryViewMode.COMPACT) }) {
            Icon(Icons.Default.List, stringResource(R.string.view_compact), tint = if (mode == LibraryViewMode.COMPACT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMode(LibraryViewMode.DETAILED) }) {
            Icon(Icons.Default.ViewAgenda, stringResource(R.string.view_detailed), tint = if (mode == LibraryViewMode.DETAILED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMode(LibraryViewMode.GRID) }) {
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
                            Text(
                                if (total != null && total > 0) stringResource(R.string.watched_out_of, watched, total)
                                else stringResource(R.string.watched_count, watched),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                IconButton(onClick = vm::backToSeasons) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
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
                                    if (watch != null && !watch.completed && watch.progressFraction > 0f) {
                                        LinearProgressIndicator(progress = { watch.progressFraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
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
            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = Color.White)
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
    watchedCount: Int = 0
) {
    LaunchedEffect(series.slug) { onVisible() }
    Card(modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onInfo), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
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
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White) }
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
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onInfo)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(series.coverUrl, series.title, series.slug, Modifier.width(if (compact) 58.dp else 82.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                if (!compact && series.genres.isNotEmpty()) Text(series.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Row {
                    IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite)) }
                    moveUp?.let { IconButton(onClick = it) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up)) } }
                    moveDown?.let { IconButton(onClick = it) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down)) } }
                    onDelete?.let { IconButton(onClick = it) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(progress: ProgressEntry, prefs: AppPreferences, onOpen: () -> Unit, onInfo: () -> Unit) {
    val state = prefs.episodeWatchStates[episodeKey(progress.seriesSlug, progress.season, progress.episode)]
    Card(modifier = Modifier.width(250.dp).combinedClickable(onClick = onOpen, onLongClick = onInfo)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(progress.coverUrl, progress.seriesTitle, progress.seriesSlug, Modifier.width(80.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(progress.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${progress.localizedEpisodeLabel()} · ${progress.localizedEpisodeTitle()}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                state?.takeIf { it.progressFraction > 0f }?.let {
                    LinearProgressIndicator(progress = { it.progressFraction }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.progress_percent, (it.progressFraction * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier.fillMaxSize()) = Box(modifier, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.titles_count, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ViewModeSelector(viewMode) { viewMode = it }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text(stringResource(R.string.search_in_list)) },
            singleLine = true
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
                        onVisible = { vm.enrichCatalogItem(series) },
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
                        onVisible = { vm.enrichCatalogItem(series) }
                    )
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
    var query by rememberSaveable(page.title) { mutableStateOf("") }
    val items = remember(page.items, query) {
        page.items.filter { item ->
            query.isBlank() || item.series.title.contains(query, true) ||
                item.episode.title.contains(query, true) || item.episode.secondaryTitle.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.episodes_count, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text(stringResource(R.string.search_in_list)) },
            singleLine = true
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

@Composable
fun EpisodeOptionsScreen(
    episode: Episode,
    hosters: List<Hoster>,
    prefs: AppPreferences,
    resolving: Boolean,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
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
            if (resolving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Button(onClick = onAuto, enabled = !resolving && hosters.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play_best_hoster))
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
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
                if (headline.isNotEmpty()) Text(headline.joinToString("  •  "), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                Text(series.description.ifBlank { stringResource(R.string.no_description) })
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

@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onPermissions: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCatalogMetadata() }
    val startMetadataRefresh = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.refreshCatalogMetadata()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
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
                    listOf(Language.GER_DUB, Language.GER_SUB, Language.ENG_SUB).forEach { lang ->
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
            item {
                HorizontalDivider()
                Text(stringResource(R.string.display_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            }
            item { SettingSwitch(stringResource(R.string.dynamic_colors), stringResource(R.string.dynamic_colors_desc), prefs.useDynamicColors, vm::setDynamicColors) }
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
            item { OutlinedButton(onClick = vm::openDefaultChallenge, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Security, null); Text(stringResource(R.string.web_verification)) } }
            item { OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BugReport, null); Text(stringResource(R.string.diagnostics)) } }
            item { OutlinedButton(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_permissions)) } }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChecked) }
}
