package ru.ddd.llmproxy.application.port

import ru.ddd.llmproxy.domain.model.Priority

/**
 * Port interface for metrics collection.
 *
 * This interface abstracts the metrics implementation,
 * allowing different backends (Prometheus, StatsD, etc.)
 */
interface MetricsPort {

    /**
     * Records a request metric.
     *
     * @param endpoint The API endpoint (e.g., "/v1/chat/completions")
     * @param priority The priority level used
     * @param status The request status ("success" or "error")
     * @param errorCode The error code if status is "error", null otherwise
     */
    fun recordRequest(
        endpoint: String,
        priority: Priority,
        status: String,
        errorCode: String? = null
    )

    /**
     * Records queue overflow event.
     *
     * @param priority The priority level that overflowed
     */
    fun recordQueueOverflow(priority: Priority)

    /**
     * Records queue wait time.
     *
     * @param endpoint The API endpoint
     * @param priority The priority level
     * @param waitMs Time spent waiting in queue in milliseconds
     */
    fun recordQueueWait(endpoint: String, priority: Priority, waitMs: Long)

    /**
     * Records provider latency.
     *
     * @param endpoint The API endpoint
     * @param priority The priority level
     * @param latencyMs Provider response time in milliseconds
     */
    fun recordProviderLatency(endpoint: String, priority: Priority, latencyMs: Long)

    /**
     * Updates the current queue length gauge.
     *
     * @param priority The priority level
     * @param length Current queue length for this priority
     */
    fun setQueueLength(priority: Priority, length: Int)

    /**
     * Updates the current in-flight requests gauge.
     *
     * @param priority The priority level
     * @param count Current in-flight request count for this priority
     */
    fun setInFlight(priority: Priority, count: Int)

    /**
     * Increments the in-flight counter.
     *
     * @param priority The priority level
     */
    fun incrementInFlight(priority: Priority)

    /**
     * Decrements the in-flight counter.
     *
     * @param priority The priority level
     */
    fun decrementInFlight(priority: Priority)

    /**
     * Records a cache hit.
     */
    fun recordCacheHit()

    /**
     * Records a cache miss.
     */
    fun recordCacheMiss()
}
