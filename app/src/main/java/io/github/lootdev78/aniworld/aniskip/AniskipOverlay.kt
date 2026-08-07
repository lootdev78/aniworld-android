package io.github.lootdev78.aniworld.aniskip

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import android.content.Context
import androidx.compose.ui.platform.LocalContext

/**
 * Lightweight overlay that shows two manual skip buttons (Intro / Outro) when available.
 * It loads segments from Aniskip on demand and uses seek callback to jump to segment end.
 */
@Composable
fun AniskipOverlay(
    mediaUrl: String,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    visible: Boolean
) {
    val context = LocalContext.current
    val aniskipEnabled by AniskipPrefs.isEnabledFlow(context).collectAsState(initial = true)
    val baseUrl by AniskipPrefs.baseUrlFlow(context).collectAsState(initial = "https://api.aniskip.com")

    var segments by remember { mutableStateOf<List<AniskipSegment>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mediaUrl, baseUrl, aniskipEnabled) {
        if (!aniskipEnabled) return@LaunchedEffect
        try {
            val service = AniskipApi.create(baseUrl)
            val repo = AniskipRepository(service)
            // network call on IO
            val resp = withContext(Dispatchers.IO) { repo.fetchSegmentsFor(mediaUrl) }
            segments = resp
            // If user enabled always-skip, we do not auto-skip here — PlayerScreen controls auto behavior.
        } catch (_: Exception) {
            segments = emptyList()
        }
    }

    if (!visible || !aniskipEnabled) return

    val nextIntro = segments.filter { it.type.equals("intro", true) && it.start_ms >= positionMs }.minByOrNull { it.start_ms }
    val nextOutro = segments.filter { it.type.equals("outro", true) && it.start_ms >= positionMs }.minByOrNull { it.start_ms }

    Row(Modifier.padding(8.dp)) {
        if (nextIntro != null) {
            IconButton(onClick = { onSeekTo(nextIntro.end_ms) }) {
                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Skip Intro")
            }
        }
        if (nextOutro != null) {
            IconButton(onClick = { onSeekTo(nextOutro.end_ms) }) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Skip Outro")
            }
        }
    }
}
