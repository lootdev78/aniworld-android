package io.github.lootdev78.aniworld.aniskip

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AniskipRepository(private val service: AniskipService) {
    private val cache = mutableMapOf<String, List<AniskipSegment>>()
    private val mutex = Mutex()

    suspend fun fetchSegmentsFor(mediaUrl: String): List<AniskipSegment> {
        mutex.withLock {
            cache[mediaUrl]?.let { return it }
            return try {
                val resp = service.getSkipSegments(AniskipRequest(video_url = mediaUrl))
                cache[mediaUrl] = resp
                resp
            } catch (e: Exception) {
                // On any error return empty list (no skip available)
                emptyList()
            }
        }
    }
}
