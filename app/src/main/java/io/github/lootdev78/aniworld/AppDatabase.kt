package io.github.lootdev78.aniworld

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

private const val LIST_SEPARATOR = "\u001F"

@Entity(tableName = "favorites", indices = [Index("updatedAt")])
data class FavoriteEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val url: String,
    val coverUrl: String,
    val description: String,
    val genres: String,
    val updatedAt: Long,
    val sortIndex: Int
) {
    fun toModel() = FavoriteEntry(title, slug, url, coverUrl, description, decodeList(genres), updatedAt)
    companion object {
        fun from(series: Series, sortIndex: Int, updatedAt: Long = System.currentTimeMillis()) = FavoriteEntity(
            slug = series.slug,
            title = series.title,
            url = series.url,
            coverUrl = series.coverUrl,
            description = series.description,
            genres = encodeList(series.genres),
            updatedAt = updatedAt,
            sortIndex = sortIndex
        )
    }
}

@Entity(tableName = "watchlist", indices = [Index("updatedAt")])
data class WatchlistEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val url: String,
    val coverUrl: String,
    val updatedAt: Long
) {
    fun toModel() = WatchEntry(title, slug, url, coverUrl, updatedAt)
}

@Entity(tableName = "progress", indices = [Index("updatedAt")])
data class ProgressEntity(
    @PrimaryKey val seriesSlug: String,
    val seriesTitle: String,
    val seriesUrl: String,
    val coverUrl: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val updatedAt: Long
) {
    fun toModel() = ProgressEntry(seriesTitle, seriesSlug, seriesUrl, coverUrl, season, episode, episodeTitle, episodeUrl, updatedAt)
}

@Entity(
    tableName = "episode_states",
    indices = [Index("seriesSlug"), Index("updatedAt")]
)
data class EpisodeStateEntity(
    @PrimaryKey val key: String,
    val seriesTitle: String,
    val seriesSlug: String,
    val seriesUrl: String,
    val coverUrl: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
) {
    fun toModel() = EpisodeWatchState(
        seriesTitle, seriesSlug, seriesUrl, coverUrl, season, episode, episodeTitle, episodeUrl,
        positionMs, durationMs, completed, updatedAt
    )
}

@Entity(tableName = "season_totals")
data class SeasonTotalEntity(@PrimaryKey val key: String, val total: Int)

@Entity(tableName = "recent_searches", indices = [Index("updatedAt")])
data class RecentSearchEntity(@PrimaryKey val query: String, val updatedAt: Long) {
    fun toModel() = SearchEntry(query, updatedAt)
}

@Entity(tableName = "watched_order")
data class WatchedOrderEntity(@PrimaryKey val slug: String, val sortIndex: Int)

@Entity(tableName = "page_cache", indices = [Index("updatedAt")])
data class PageCacheEntity(
    @PrimaryKey val cacheKey: String,
    val body: String,
    val updatedAt: Long,
    val contentType: String = "text/html"
)


@Entity(tableName = "series_metadata", indices = [Index("title"), Index("updatedAt")])
data class SeriesMetadataEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val url: String,
    val description: String,
    val coverUrl: String,
    val genres: String,
    val year: String,
    val ageRating: String,
    val updatedAt: Long
) {
    fun toModel(): Series = Series(
        title = title,
        slug = slug,
        url = url,
        description = description,
        coverUrl = coverUrl,
        genres = decodeList(genres),
        year = year,
        ageRating = ageRating
    )

    companion object {
        fun from(series: Series, updatedAt: Long = System.currentTimeMillis()) = SeriesMetadataEntity(
            slug = series.slug,
            title = series.title,
            url = series.url,
            description = series.description,
            coverUrl = series.coverUrl,
            genres = encodeList(series.genres),
            year = series.year,
            ageRating = series.ageRating,
            updatedAt = updatedAt
        )
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM favorites ORDER BY sortIndex ASC, updatedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM watchlist ORDER BY updatedAt DESC LIMIT 200")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM episode_states ORDER BY updatedAt DESC")
    fun observeEpisodeStates(): Flow<List<EpisodeStateEntity>>

    @Query("SELECT * FROM season_totals")
    fun observeSeasonTotals(): Flow<List<SeasonTotalEntity>>

    @Query("SELECT * FROM recent_searches ORDER BY updatedAt DESC LIMIT 15")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Query("SELECT * FROM watched_order ORDER BY sortIndex ASC")
    fun observeWatchedOrder(): Flow<List<WatchedOrderEntity>>

    @Query("SELECT * FROM favorites WHERE slug = :slug LIMIT 1")
    suspend fun favorite(slug: String): FavoriteEntity?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM favorites")
    suspend fun maxFavoriteIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorites(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE slug = :slug")
    suspend fun deleteFavorite(slug: String)

    @Query("SELECT * FROM favorites ORDER BY sortIndex ASC, updatedAt DESC")
    suspend fun favoritesNow(): List<FavoriteEntity>

    @Query("UPDATE favorites SET sortIndex = :sortIndex WHERE slug = :slug")
    suspend fun updateFavoriteOrder(slug: String, sortIndex: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlist(entity: WatchlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlist(entities: List<WatchlistEntity>)

    @Query("DELETE FROM watchlist WHERE slug = :slug")
    suspend fun deleteWatchlist(slug: String)

    @Query("DELETE FROM watchlist WHERE slug NOT IN (SELECT slug FROM watchlist ORDER BY updatedAt DESC LIMIT 60)")
    suspend fun trimWatchlist()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entities: List<ProgressEntity>)

    @Query("DELETE FROM progress WHERE seriesSlug = :slug")
    suspend fun deleteProgress(slug: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeState(entity: EpisodeStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeStates(entities: List<EpisodeStateEntity>)

    @Query("SELECT * FROM episode_states WHERE `key` = :key LIMIT 1")
    suspend fun episodeState(key: String): EpisodeStateEntity?

    @Query("DELETE FROM episode_states WHERE `key` = :key")
    suspend fun deleteEpisodeState(key: String)

    @Query("DELETE FROM episode_states WHERE seriesSlug = :slug")
    suspend fun deleteEpisodeStatesForSeries(slug: String)

    @Query("SELECT COUNT(*) FROM episode_states WHERE seriesSlug = :slug")
    suspend fun episodeStateCount(slug: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonTotal(entity: SeasonTotalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonTotals(entities: List<SeasonTotalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(entity: RecentSearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearches(entities: List<RecentSearchEntity>)

    @Query("DELETE FROM recent_searches WHERE query NOT IN (SELECT query FROM recent_searches ORDER BY updatedAt DESC LIMIT 15)")
    suspend fun trimSearches()

    @Query("DELETE FROM recent_searches")
    suspend fun clearSearches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedOrder(entity: WatchedOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedOrders(entities: List<WatchedOrderEntity>)

    @Query("DELETE FROM watched_order WHERE slug = :slug")
    suspend fun deleteWatchedOrder(slug: String)

    @Query("SELECT * FROM watched_order ORDER BY sortIndex ASC")
    suspend fun watchedOrderNow(): List<WatchedOrderEntity>

    @Query("UPDATE watched_order SET sortIndex = :sortIndex WHERE slug = :slug")
    suspend fun updateWatchedOrder(slug: String, sortIndex: Int)

    @Query("UPDATE favorites SET title = :title, url = :url, coverUrl = CASE WHEN :coverUrl = '' THEN coverUrl ELSE :coverUrl END, description = CASE WHEN :description = '' THEN description ELSE :description END, genres = CASE WHEN :genres = '' THEN genres ELSE :genres END WHERE slug = :slug")
    suspend fun updateFavoriteMetadata(slug: String, title: String, url: String, coverUrl: String, description: String, genres: String)

    @Query("UPDATE watchlist SET title = :title, url = :url, coverUrl = CASE WHEN :coverUrl = '' THEN coverUrl ELSE :coverUrl END WHERE slug = :slug")
    suspend fun updateWatchlistMetadata(slug: String, title: String, url: String, coverUrl: String)

    @Query("UPDATE progress SET seriesTitle = :title, seriesUrl = :url, coverUrl = CASE WHEN :coverUrl = '' THEN coverUrl ELSE :coverUrl END WHERE seriesSlug = :slug")
    suspend fun updateProgressMetadata(slug: String, title: String, url: String, coverUrl: String)

    @Query("UPDATE episode_states SET seriesTitle = :title, seriesUrl = :url, coverUrl = CASE WHEN :coverUrl = '' THEN coverUrl ELSE :coverUrl END WHERE seriesSlug = :slug")
    suspend fun updateEpisodeMetadata(slug: String, title: String, url: String, coverUrl: String)

    @Query("UPDATE favorites SET coverUrl = ''")
    suspend fun clearFavoriteCovers()

    @Query("UPDATE watchlist SET coverUrl = ''")
    suspend fun clearWatchlistCovers()

    @Query("UPDATE progress SET coverUrl = ''")
    suspend fun clearProgressCovers()

    @Query("UPDATE episode_states SET coverUrl = ''")
    suspend fun clearEpisodeStateCovers()

    @Transaction
    suspend fun moveFavorite(slug: String, delta: Int) {
        val items = favoritesNow().toMutableList()
        val from = items.indexOfFirst { it.slug == slug }
        if (from < 0 || items.isEmpty()) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return
        items.add(to, items.removeAt(from))
        items.forEachIndexed { index, item -> updateFavoriteOrder(item.slug, index) }
    }

    @Transaction
    suspend fun moveWatched(slug: String, delta: Int) {
        val items = watchedOrderNow().toMutableList()
        val from = items.indexOfFirst { it.slug == slug }
        if (from < 0 || items.isEmpty()) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from == to) return
        items.add(to, items.removeAt(from))
        items.forEachIndexed { index, item -> updateWatchedOrder(item.slug, index) }
    }
}


@Dao
interface SeriesMetadataDao {
    @Query("SELECT * FROM series_metadata ORDER BY title COLLATE NOCASE ASC")
    suspend fun all(): List<SeriesMetadataEntity>

    @Query("SELECT * FROM series_metadata WHERE slug = :slug LIMIT 1")
    suspend fun get(slug: String): SeriesMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeriesMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SeriesMetadataEntity>)

    @Query("SELECT COUNT(*) FROM series_metadata")
    suspend fun count(): Int

    @Query("DELETE FROM series_metadata")
    suspend fun clear()
}

@Dao
interface PageCacheDao {
    @Query("SELECT * FROM page_cache WHERE cacheKey = :key LIMIT 1")
    fun get(key: String): PageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PageCacheEntity)

    @Query("DELETE FROM page_cache WHERE cacheKey = :key")
    fun delete(key: String)

    @Query("DELETE FROM page_cache WHERE updatedAt < :before")
    fun deleteOlderThan(before: Long)

    @Query("DELETE FROM page_cache")
    fun clear()
}

@Database(
    entities = [
        FavoriteEntity::class,
        WatchlistEntity::class,
        ProgressEntity::class,
        EpisodeStateEntity::class,
        SeasonTotalEntity::class,
        RecentSearchEntity::class,
        WatchedOrderEntity::class,
        PageCacheEntity::class,
        SeriesMetadataEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun pageCacheDao(): PageCacheDao
    abstract fun seriesMetadataDao(): SeriesMetadataDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS series_metadata (
                        slug TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        description TEXT NOT NULL,
                        coverUrl TEXT NOT NULL,
                        genres TEXT NOT NULL,
                        year TEXT NOT NULL,
                        ageRating TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_metadata_title ON series_metadata(title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_metadata_updatedAt ON series_metadata(updatedAt)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "aniworld.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

private fun encodeList(values: List<String>): String = values.filter(String::isNotBlank).distinct().joinToString(LIST_SEPARATOR)
private fun decodeList(value: String): List<String> = value.split(LIST_SEPARATOR).filter(String::isNotBlank)
