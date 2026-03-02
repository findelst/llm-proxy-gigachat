package ru.ddd.llmproxy.domain.repository

import ru.ddd.llmproxy.domain.model.CacheKey

/**
 * Cache statistics.
 */
data class CacheStats(
    val hits: Long,
    val misses: Long,
    val sets: Long,
    val evictions: Long
) {
    val hitRate: Double
        get() = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
}

/**
 * Repository interface for caching chat completion responses.
 *
 * This interface defines the contract for cache implementations.
 * Implementations should be thread-safe and support concurrent access.
 */
interface CacheRepository<T> {

    /**
     * Retrieves a cached response by key.
     *
     * @param key The cache key
     * @return The cached response, or null if not found or expired
     */
    suspend fun get(key: CacheKey): T?

    /**
     * Stores a response in the cache.
     *
     * @param key The cache key
     * @param value The response to cache
     */
    suspend fun set(key: CacheKey, value: T)

    /**
     * Removes a cached response by key.
     *
     * @param key The cache key to invalidate
     */
    suspend fun invalidate(key: CacheKey)

    /**
     * Clears all cached entries.
     */
    suspend fun clear()

    /**
     * Returns current cache statistics.
     *
     * @return Cache statistics including hits, misses, sets, and evictions
     */
    fun stats(): CacheStats

    /**
     * Checks if caching is enabled.
     *
     * @return true if caching is enabled, false otherwise
     */
    fun isEnabled(): Boolean = true
}
