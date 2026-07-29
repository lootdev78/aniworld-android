package io.github.lootdev78.aniworld

import android.content.Context
import android.content.Intent
import android.net.Uri

fun launchExternalPlayback(context: Context, playback: ResolvedPlayback): Result<Unit> = runCatching {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(playback.stream.url), playback.stream.mimeType ?: "video/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_TITLE, "${playback.seriesTitle} · ${playback.episode.localizedLabel(context)}")
        playback.stream.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value?.let { referer ->
            putExtra(Intent.EXTRA_REFERRER, Uri.parse(referer))
        }
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.open_external_player)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/**
 * Miracast/Wi-Fi Display has no public third-party sender API on modern Android. Open the best OEM
 * system picker available, with fallbacks for devices that expose the older Wi-Fi Display action.
 */
fun launchMiracastPicker(context: Context): Result<Unit> = runCatching {
    val candidates = listOf(
        Intent(android.provider.Settings.ACTION_CAST_SETTINGS),
        Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
    )
    val intent = candidates.firstOrNull { candidate ->
        candidate.resolveActivity(context.packageManager) != null
    } ?: error(context.getString(R.string.miracast_settings_failed))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
