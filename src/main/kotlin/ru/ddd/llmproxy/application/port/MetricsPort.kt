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

    /**
     * Records a retry attempt.
     *
     * @param priority The priority level of the request being retried
     * @param attemptNumber The retry attempt number (1 = first retry)
     */
    fun recordRetryAttempt(priority: Priority, attemptNumber: Int)

    /**
     * Records a successful retry (operation succeeded after at least one retry).
     *
     * @param totalAttempts Total number of attempts including initial
     */
    fun recordRetrySuccess(totalAttempts: Int)

    /**
     * Records a retry failure (all retry attempts exhausted).
     *
     * @param priority The priority level of the failed request
     * @param errorCode The error code that caused the failure
     */
    fun recordRetryFailure(priority: Priority, errorCode: String)

    /**
     * Records a queue timeout event.
     *
     * @param priority The priority level of the timed out request
     */
    fun recordQueueTimeout(priority: Priority)

    /**
     * Records a preemption event.
     *
     * @param preemptedPriority The priority that was preempted
     * @param preemptedByPriority The priority that caused the preemption
     */
    fun recordPreemption(preemptedPriority: Priority, preemptedByPriority: Priority)

    /**
     * Records a P3 throttling event (P3 request blocked due to max 1 concurrent).
     */
    fun recordP3Throttled()

    /**
     * Updates the current concurrent requests gauge.
     *
     * @param priority The priority level
     * @param count Current concurrent request count for this priority
     */
    fun setConcurrentRequests(priority: Priority, count: Int)

    /**
     * Records the number of available slots.
     *
     * @param available Number of available slots
     */
    fun setAvailableSlots(available: Int)
}
