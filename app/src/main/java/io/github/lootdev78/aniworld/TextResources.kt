package io.github.lootdev78.aniworld

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

fun Context.text(@StringRes id: Int, vararg args: Any): String = getString(id, *args)

@Composable
fun Language.localizedLabel(): String = "$flag ${stringResource(labelRes)}"

fun Language.localizedLabel(context: Context): String = "$flag ${context.getString(labelRes)}"

@Composable
fun Episode.localizedLabel(): String = if (season == 0) {
    stringResource(R.string.movie_number, number)
} else {
    stringResource(R.string.season_episode_short, season, number)
}

fun Episode.localizedLabel(context: Context): String = if (season == 0) {
    context.getString(R.string.movie_number, number)
} else {
    context.getString(R.string.season_episode_short, season, number)
}

@Composable
fun Episode.localizedDisplayTitle(): String = title.ifBlank {
    if (season == 0) stringResource(R.string.movie_number, number)
    else stringResource(R.string.episode_number, number)
}

fun Episode.localizedDisplayTitle(context: Context): String = title.ifBlank {
    if (season == 0) context.getString(R.string.movie_number, number)
    else context.getString(R.string.episode_number, number)
}

@Composable
fun ProgressEntry.localizedEpisodeLabel(): String = if (season == 0) {
    stringResource(R.string.movie_number, episode)
} else {
    stringResource(R.string.season_episode_short, season, episode)
}

fun ProgressEntry.localizedEpisodeLabel(context: Context): String = if (season == 0) {
    context.getString(R.string.movie_number, episode)
} else {
    context.getString(R.string.season_episode_short, season, episode)
}

@Composable
fun ProgressEntry.localizedEpisodeTitle(): String = episodeTitle.ifBlank {
    if (season == 0) stringResource(R.string.movie_number, episode)
    else stringResource(R.string.episode_number, episode)
}

fun ProgressEntry.localizedEpisodeTitle(context: Context): String = episodeTitle.ifBlank {
    if (season == 0) context.getString(R.string.movie_number, episode)
    else context.getString(R.string.episode_number, episode)
}

@Composable
fun localizedHosterName(name: String): String = HosterCatalog.displayName(name).ifBlank {
    stringResource(R.string.language_unknown)
}

fun localizedHosterName(context: Context, name: String): String = HosterCatalog.displayName(name).ifBlank {
    context.getString(R.string.language_unknown)
}
