package ru.ddd.llmproxy.infrastructure.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats as CaffeineCacheStats
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.domain.model.CacheKey
import ru.ddd.llmproxy.domain.repository.CacheRepository
import ru.ddd.llmproxy.domain.repository.CacheStats
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/**
 * Caffeine-based implementation of CacheRepository.
 *
 * Features:
 * - TTL-based expiration
 * - Maximum size limit
 * - Statistics tracking
 * - Thread-safe operations
 */
@Component
class CaffeineCacheRepository<T>(
    private val properties: LlmProxyProperties
) : CacheRepository<T> {

    private val cache: Cache<CacheKey, T> = Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterWrite(properties.cache.ttl.toMillis(), TimeUnit.MILLISECONDS)
        .recordStats()
        .build()

    override fun isEnabled(): Boolean = properties.cache.enabled

    override suspend fun get(key: CacheKey): T? {
        if (!isEnabled()) {
            log.debug { "Cache disabled, skipping get for key: ${key.hash.take(8)}..." }
            return null
        }

        val value = cache.getIfPresent(key)
        if (value != null) {
            log.debug { "Cache hit for key: ${key.hash.take(8)}..." }
        } else {
            log.debug { "Cache miss for key: ${key.hash.take(8)}..." }
        }
        return value
    }

    override suspend fun set(key: CacheKey, value: T) {
        if (!isEnabled()) {
            log.debug { "Cache disabled, skipping set for key: ${key.hash.take(8)}..." }
            return
        }

        cache.put(key, value)
        log.debug { "Cached value for key: ${key.hash.take(8)}..." }
    }

    override suspend fun invalidate(key: CacheKey) {
        cache.invalidate(key)
        log.debug { "Invalidated cache key: ${key.hash.take(8)}..." }
    }

    override suspend fun clear() {
        cache.invalidateAll()
        log.info { "Cleared all cache entries" }
    }

    override fun stats(): CacheStats {
        val caffeineStats: CaffeineCacheStats = cache.stats()
        return CacheStats(
            hits = caffeineStats.hitCount(),
            misses = caffeineStats.missCount(),
            sets = caffeineStats.loadCount(),
            evictions = caffeineStats.evictionCount()
        )
    }

    /**
     * Returns the current number of entries in the cache.
     */
    fun size(): Long = cache.estimatedSize()

    /**
     * Returns the underlying Caffeine cache for advanced operations.
     */
    fun underlyingCache(): Cache<CacheKey, T> = cache
}
