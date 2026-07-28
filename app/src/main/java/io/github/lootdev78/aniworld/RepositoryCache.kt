package io.github.lootdev78.aniworld

interface RepositoryCache {
    fun get(key: String): PageCacheEntity?
    fun put(key: String, body: String, contentType: String = "text/html")
    fun remove(key: String)
    fun prune()
}

class RoomRepositoryCache(private val dao: PageCacheDao) : RepositoryCache {
    override fun get(key: String): PageCacheEntity? = dao.get(key)

    override fun put(key: String, body: String, contentType: String) {
        dao.upsert(PageCacheEntity(key, body, System.currentTimeMillis(), contentType))
    }

    override fun remove(key: String) = dao.delete(key)

    override fun prune() {
        dao.deleteOlderThan(System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1_000L)
    }
}
