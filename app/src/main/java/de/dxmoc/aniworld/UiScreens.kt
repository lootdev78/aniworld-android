@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package de.dxmoc.aniworld

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(st: UiState, vm: AppViewModel) {
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
            st.homeFeed.featured?.let { series -> item { HomeHero(series, st.preferences.isFavorite(series.slug), { vm.select(series) }, { vm.toggleFavorite(series) }) } }
            val continueWatching = st.preferences.progress.values.sortedByDescending { it.updatedAt }.take(12)
            if (continueWatching.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.continue_watching), stringResource(R.string.your_last_position))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.seriesSlug }) { progress -> ContinueCard(progress, st.preferences, { vm.openProgress(progress) }) }
                    }
                }
            }
            if (st.homeFeed.popularAtAniWorld.isNotEmpty()) item { HomeSeriesRow(stringResource(R.string.popular_at_aniworld), st.homeFeed.popularAtAniWorld, st, vm) }
            if (st.homeFeed.latestEpisodes.isNotEmpty()) item { LatestEpisodesRow(st.homeFeed.latestEpisodes, st, vm) }
            if (st.homeFeed.newAnimes.isNotEmpty()) item { HomeSeriesRow(stringResource(R.string.new_animes), st.homeFeed.newAnimes, st, vm) }
            if (st.homeFeed.currentlyPopular.isNotEmpty()) item { HomeSeriesRow(stringResource(R.string.currently_popular), st.homeFeed.currentlyPopular, st, vm) }
            if (st.homeFeed.communityWatching.isNotEmpty()) item { HomeSeriesRow(stringResource(R.string.community_watching), st.homeFeed.communityWatching, st, vm) }
        }
    }
}

@Composable
private fun HomeHero(series: Series, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp)) {
        AsyncImage(model = series.coverUrl, contentDescription = series.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
private fun HomeSeriesRow(title: String, series: List<Series>, st: UiState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, stringResource(R.string.titles_count, series.size))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series, key = { it.slug }) { item ->
                AnimePosterCard(
                    series = item,
                    favorite = st.preferences.isFavorite(item.slug),
                    onOpen = { vm.select(item) },
                    onFavorite = { vm.toggleFavorite(item) },
                    onVisible = { vm.enrichCatalogItem(item) },
                    watchedCount = st.preferences.watchedCount(item.slug)
                )
            }
        }
    }
}

@Composable
private fun LatestEpisodesRow(episodes: List<HomeEpisode>, st: UiState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.latest_episodes), stringResource(R.string.new_episodes_and_movies))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(episodes, key = { it.episode.url }) { item ->
                LaunchedEffect(item.series.slug) { vm.enrichCatalogItem(item.series) }
                Card(onClick = { vm.openHomeEpisode(item) }, modifier = Modifier.width(260.dp)) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cover(item.series.coverUrl, item.series.title, Modifier.width(82.dp).aspectRatio(.70f))
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
        }
    }
}

@Composable
fun CatalogScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    if (st.catalog.items.isEmpty() && !st.catalogLoading) LaunchedEffect(Unit) { vm.loadCatalog() }
    PullToRefreshBox(
        isRefreshing = st.catalogLoading,
        onRefresh = { vm.loadCatalog(true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = st.catalogQuery,
                onValueChange = vm::setCatalogQuery,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text(stringResource(R.string.catalog_search_all)) },
                singleLine = true
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { FilterChip(selected = st.catalogLetter == null, onClick = { vm.setCatalogLetter(null) }, label = { Text(stringResource(R.string.all)) }) }
                items(st.catalog.letters) { letter -> FilterChip(selected = st.catalogLetter == letter, onClick = { vm.setCatalogLetter(letter) }, label = { Text(letter) }) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { FilterChip(selected = st.catalogGenre == null, onClick = { vm.setCatalogGenre(null) }, label = { Text(stringResource(R.string.all_genres)) }, leadingIcon = { Icon(Icons.Default.FilterAlt, null, Modifier.size(16.dp)) }) }
                items(st.catalog.genres, key = { it }) { genre -> FilterChip(selected = st.catalogGenre == genre, onClick = { vm.setCatalogGenre(genre) }, label = { Text(genre) }) }
            }
            Text(stringResource(R.string.catalog_count, st.filteredCatalog.size, st.catalog.items.size), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (st.catalog.items.isEmpty() && st.catalogLoading) {
                CatalogSkeleton(Modifier.weight(1f))
            } else if (!st.catalogLoading && st.filteredCatalog.isEmpty()) {
                EmptyState(stringResource(R.string.no_anime_found), stringResource(R.string.adjust_catalog_filters), Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (expanded) 180.dp else 148.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(st.filteredCatalog, key = { it.slug }) { series ->
                        AnimePosterCard(
                            series = series,
                            favorite = st.preferences.isFavorite(series.slug),
                            onOpen = { vm.select(series) },
                            onFavorite = { vm.toggleFavorite(series) },
                            modifier = Modifier.fillMaxWidth(),
                            onVisible = { vm.enrichCatalogItem(series) },
                            watchedCount = st.preferences.watchedCount(series.slug)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    LaunchedEffect(st.query) {
        val query = st.query.trim()
        if (query.length >= 3) {
            delay(550L)
            vm.search(query, rememberQuery = false)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = st.query,
            onValueChange = vm::query,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text(stringResource(R.string.search_anime)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { vm.search() }) { Icon(Icons.Default.Search, stringResource(R.string.search)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() })
        )
        if (st.preferences.recentSearches.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recent_searches), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::clearRecentSearches) { Icon(Icons.Default.ClearAll, null); Text(stringResource(R.string.clear)) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(st.preferences.recentSearches, key = { it.query }) { entry -> AssistChip(onClick = { vm.search(entry.query) }, label = { Text(entry.query) }) }
            }
        }
        if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!st.loading && st.results.isEmpty()) {
            EmptyState(
                if (st.query.isBlank()) stringResource(R.string.search_anime) else stringResource(R.string.no_results),
                if (st.query.isBlank()) stringResource(R.string.search_empty_hint) else stringResource(R.string.search_no_results_hint),
                Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 260.dp else 220.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(st.results, key = { it.slug }) { series ->
                    AnimeListCard(series, st.preferences.isFavorite(series.slug), st.preferences.watchedCount(series.slug), { vm.select(series) }, { vm.toggleFavorite(series) })
                }
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
        LibraryScreenHeader(stringResource(R.string.favorites), filter, { filter = it }, st.preferences.favoriteSort, vm::setFavoriteSort)
        if (ordered.isEmpty()) {
            EmptyState(stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_hint), Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 320.dp else 260.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ordered, key = { it.slug }) { entry ->
                    LibraryCard(
                        series = entry.asSeries(),
                        subtitle = stringResource(R.string.watched_episodes_count, st.preferences.watchedCount(entry.slug)),
                        favorite = true,
                        onOpen = { vm.selectFavorite(entry) },
                        onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                        moveUp = if (st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, -1) }) else null,
                        moveDown = if (st.preferences.favoriteSort == LibrarySort.CUSTOM) ({ vm.moveFavorite(entry.slug, 1) }) else null
                    )
                }
            }
        }
    }
}

private enum class HistoryFilter(@StringRes val labelRes: Int) { ALL(R.string.all), IN_PROGRESS(R.string.started), COMPLETED(R.string.watched) }

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
        }
        statusMatches && (filter.isBlank() || entry.title.contains(filter, true))
    }
    Column(Modifier.fillMaxSize()) {
        LibraryScreenHeader(stringResource(R.string.history_title), filter, { filter = it }, st.preferences.watchedSort, vm::setWatchedSort)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { item ->
                FilterChip(selected = statusFilter == item, onClick = { statusFilter = item }, label = { Text(stringResource(item.labelRes)) })
            }
        }
        if (ordered.isEmpty()) {
            EmptyState(stringResource(R.string.no_history), stringResource(R.string.no_history_hint), Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 340.dp else 270.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ordered, key = { it.slug }) { entry ->
                    LibraryCard(
                        series = entry.asSeries(),
                        subtitle = stringResource(R.string.history_item_subtitle, entry.watchedEpisodes, entry.latestSeason, entry.latestEpisode),
                        favorite = st.preferences.isFavorite(entry.slug),
                        onOpen = { vm.selectWatched(entry) },
                        onFavorite = { vm.toggleFavorite(entry.asSeries()) },
                        moveUp = if (st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, -1) }) else null,
                        moveDown = if (st.preferences.watchedSort == LibrarySort.CUSTOM) ({ vm.moveWatched(entry.slug, 1) }) else null,
                        onDelete = { vm.removeWatched(entry.slug) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreenHeader(title: String, filter: String, onFilter: (String) -> Unit, sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Sort, null)
        }
        OutlinedTextField(value = filter, onValueChange = onFilter, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), label = { Text(stringResource(R.string.search_in_list)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibrarySort.entries) { item -> FilterChip(selected = sort == item, onClick = { onSort(item) }, label = { Text(stringResource(item.labelRes)) }) }
        }
    }
}

private enum class EpisodeFilter(@StringRes val labelRes: Int) { ALL(R.string.all), UNWATCHED(R.string.unwatched), STARTED(R.string.started), WATCHED(R.string.watched) }

@Composable
fun DetailScreen(st: UiState, vm: AppViewModel, expanded: Boolean) {
    val series = st.selected ?: return
    var episodeFilter by rememberSaveable(series.slug, st.season) { mutableStateOf(EpisodeFilter.ALL) }
    if (st.season == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                if (expanded) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Cover(series.coverUrl, series.title, Modifier.width(240.dp).aspectRatio(.70f))
                    SeriesInfo(series, st, vm, Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().heightIn(max = 460.dp).aspectRatio(.70f))
                    SeriesInfo(series, st, vm, Modifier.fillMaxWidth())
                }
            }
            item { SectionTitle(stringResource(R.string.seasons_and_movies), stringResource(R.string.areas_count, st.seasons.size)) }
            items(st.seasons, key = { it }) { season ->
                Card(onClick = { vm.season(season) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            Text(if (total != null) stringResource(R.string.watched_out_of, watched, total) else stringResource(R.string.watched_count, watched), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (st.season == 0) stringResource(R.string.movies) else stringResource(R.string.season_number, st.season ?: 0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::toggleSeasonWatched) { Icon(Icons.Default.DoneAll, null); Text(stringResource(R.string.toggle_all)) }
            }
            if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleEpisodes, key = { it.key }) { episode ->
                        val watch = st.preferences.episodeWatchStates[episode.key]
                        Card(onClick = { vm.inspectEpisode(episode) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (watch?.completed == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (watch?.completed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${episode.localizedLabel()} · ${episode.localizedDisplayTitle()}", fontWeight = FontWeight.Bold)
                                    if (episode.secondaryTitle.isNotBlank()) Text(episode.secondaryTitle, style = MaterialTheme.typography.bodySmall)
                                    if (watch != null && !watch.completed && watch.progressFraction > 0f) {
                                        LinearProgressIndicator(progress = { watch.progressFraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                        Text(stringResource(R.string.progress_percent, (watch.progressFraction * 100).toInt()), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(onClick = { vm.toggleEpisodeWatched(episode) }) { Icon(if (watch?.completed == true) Icons.Default.CheckCircle else Icons.Default.Add, stringResource(R.string.toggle_watched)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesInfo(series: Series, st: UiState, vm: AppViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(series.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        if (series.genres.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            series.genres.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
        }
        Text(series.description.ifBlank { stringResource(R.string.no_description) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.toggleFavorite(series) }) { Icon(if (st.preferences.isFavorite(series.slug)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null); Text(if (st.preferences.isFavorite(series.slug)) stringResource(R.string.remove_favorite) else stringResource(R.string.favorite)) }
            OutlinedButton(onClick = { vm.openManualPage(series.url, series.title) }) { Icon(Icons.Default.OpenInBrowser, null); Text(stringResource(R.string.website)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimePosterCard(
    series: Series,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier.width(154.dp),
    onVisible: () -> Unit = {},
    watchedCount: Int = 0
) {
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(series.slug) { onVisible() }
    Card(modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box {
            Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(.68f))
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
    if (menu) AnimeLongPressDialog(series, favorite, onOpen, onFavorite) { menu = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeListCard(series: Series, favorite: Boolean, watchedCount: Int, onOpen: () -> Unit, onFavorite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(series.coverUrl, series.title, Modifier.width(90.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2)
                if (series.genres.isNotEmpty()) Text(series.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                if (series.description.isNotBlank()) Text(series.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (watchedCount > 0) Text(stringResource(R.string.watched_episodes_count, watchedCount), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite)) }
        }
    }
    if (menu) AnimeLongPressDialog(series, favorite, onOpen, onFavorite) { menu = false }
}

@Composable
private fun AnimeLongPressDialog(series: Series, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(series.title) }, text = { Text(stringResource(R.string.action_select)) }, confirmButton = {
        TextButton(onClick = { onFavorite(); onDismiss() }) { Icon(if (favorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite, null); Text(if (favorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_to_favorites)) }
    }, dismissButton = { Row { TextButton(onClick = { onOpen(); onDismiss() }) { Text(stringResource(R.string.open)) }; TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } } })
}

@Composable
private fun LibraryCard(series: Series, subtitle: String, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, moveUp: (() -> Unit)? = null, moveDown: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(series.coverUrl, series.title, Modifier.width(82.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                if (series.genres.isNotEmpty()) Text(series.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Row {
                    IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite)) }
                    moveUp?.let { IconButton(onClick = it) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up)) } }
                    moveDown?.let { IconButton(onClick = it) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down)) } }
                    onDelete?.let { IconButton(onClick = it) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } }
                }
            }
        }
    }
    if (menu) AnimeLongPressDialog(series, favorite, onOpen, onFavorite) { menu = false }
}

@Composable
private fun ContinueCard(progress: ProgressEntry, prefs: AppPreferences, onOpen: () -> Unit) {
    val state = prefs.episodeWatchStates[episodeKey(progress.seriesSlug, progress.season, progress.episode)]
    Card(onClick = onOpen, modifier = Modifier.width(250.dp)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(progress.coverUrl, progress.seriesTitle, Modifier.width(80.dp).aspectRatio(.70f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(progress.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${progress.localizedEpisodeLabel()} · ${progress.localizedEpisodeTitle()}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                state?.let {
                    LinearProgressIndicator(progress = { it.progressFraction }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.progress_percent, (it.progressFraction * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String, title: String, modifier: Modifier) {
    Surface(modifier.clip(RoundedCornerShape(12.dp)), color = MaterialTheme.colorScheme.surfaceVariant) {
        if (url.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, title, Modifier.size(42.dp)) }
        else AsyncImage(model = url, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeOptionsDialog(
    episode: Episode,
    hosters: List<Hoster>,
    prefs: AppPreferences,
    resolving: Boolean,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onLanguage: (Language) -> Unit,
    onManualHoster: (Hoster) -> Unit
) {
    val languages = hosters.map { it.lang }.filter { it != Language.UNKNOWN }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${episode.localizedLabel()} · ${episode.localizedDisplayTitle()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (languages.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    languages.forEach { lang -> FilterChip(selected = prefs.languagePriority.firstOrNull() == lang, onClick = { onLanguage(lang) }, label = { Text(lang.localizedLabel()) }, leadingIcon = { Icon(Icons.Default.Subtitles, null, Modifier.size(16.dp)) }) }
                }
                if (hosters.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    hosters.forEach { hoster -> AssistChip(onClick = { onManualHoster(hoster) }, label = { Text("${localizedHosterName(hoster.name)} · ${hoster.lang.localizedLabel()}") }, leadingIcon = { Icon(Icons.Default.OpenInBrowser, null, Modifier.size(16.dp)) }) }
                } else Text(stringResource(R.string.no_hosters_web_session))
            }
        },
        confirmButton = { TextButton(onClick = onAuto, enabled = !resolving) { Text(stringResource(R.string.play)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(prefs: AppPreferences, vm: AppViewModel, onDismiss: () -> Unit, onPermissions: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text(stringResource(R.string.preferred_language), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(Language.GER_DUB, Language.GER_SUB, Language.ENG_SUB).forEach { lang -> FilterChip(selected = prefs.languagePriority.firstOrNull() == lang, onClick = { vm.setPrimaryLanguage(lang) }, label = { Text(lang.localizedLabel()) }) } }
                }
                item {
                    Text(stringResource(R.string.hoster_order), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { HosterCatalog.DEFAULT_PRIORITY.forEach { hoster -> FilterChip(selected = HosterCatalog.normalize(prefs.hosterPriority.firstOrNull().orEmpty()) == HosterCatalog.normalize(hoster), onClick = { vm.setPrimaryHoster(hoster) }, label = { Text(stringResource(R.string.hoster_first, hoster)) }, leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }) } }
                }
                item { SettingSwitch(stringResource(R.string.verify_stream_before_start), stringResource(R.string.verify_stream_before_start_desc), prefs.verifyStreams, vm::setVerifyStreams) }
                item { SettingSwitch(stringResource(R.string.dynamic_colors), stringResource(R.string.dynamic_colors_desc), prefs.useDynamicColors, vm::setDynamicColors) }
                item { OutlinedButton(onClick = vm::openDefaultChallenge) { Icon(Icons.Default.Security, null); Text(stringResource(R.string.web_verification)) } }
                item { OutlinedButton(onClick = onPermissions) { Text(stringResource(R.string.manage_permissions)) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChecked) }
}
