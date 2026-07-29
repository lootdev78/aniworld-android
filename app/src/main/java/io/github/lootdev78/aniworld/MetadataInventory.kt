package io.github.lootdev78.aniworld

/** Comparison between the currently available catalog and the persisted offline metadata/cache. */
data class MetadataInventory(
    val expectedCount: Int,
    val storedCount: Int,
    val missingSlugs: List<String>,
    val incompleteSlugs: List<String>,
    val coverCacheFiles: Int,
    val coverCacheBytes: Long
) {
    val completeCount: Int get() = (expectedCount - missingSlugs.size - incompleteSlugs.size).coerceAtLeast(0)
    val isComplete: Boolean get() = missingSlugs.isEmpty() && incompleteSlugs.isEmpty()
}

internal fun Series.hasCompleteOfflineMetadata(): Boolean =
    slug.isNotBlank() &&
        title.isNotBlank() &&
        description.isNotBlank() &&
        coverUrl.isNotBlank() &&
        genres.any(String::isNotBlank)
