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
