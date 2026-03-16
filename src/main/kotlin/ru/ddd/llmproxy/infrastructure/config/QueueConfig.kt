package ru.ddd.llmproxy.infrastructure.config

/**
 * Unified configuration for the priority queue system.
 * Merges queue, concurrency, and retry settings into a single config.
 *
 * @param maxLength Maximum queue length
 * @param timeoutMinutes Queue timeout in minutes (0 = disabled)
 * @param timeoutCheckIntervalMs Interval for timeout cleanup checks
 * @param defaultPriority Default priority for requests
 * @param maxConcurrent Global max concurrent requests
 * @param p1MaxThreads Max concurrent P1 requests
 * @param p3MaxThreads Max concurrent P3 requests
 * @param preemptionEnabled Whether P1 can preempt lower priorities
 * @param retryEnabled Whether retry is enabled
 * @param retryMaxAttempts Maximum retry attempts
 * @param retryInitialDelayMs Initial retry delay
 * @param retryMaxDelayMs Maximum retry delay
 * @param retryBackoffMultiplier Exponential backoff multiplier
 * @param retryableStatusCodes HTTP status codes that trigger retry
 */
data class QueueConfig(
    // Queue settings
    val maxLength: Int = 100,
    val timeoutMinutes: Long = 10,
    val timeoutCheckIntervalMs: Long = 60000,
    val defaultPriority: String = "p1",

    // Concurrency settings
    val maxConcurrent: Int = 3,
    val p1MaxThreads: Int = 2,
    val p3MaxThreads: Int = 1,
    val preemptionEnabled: Boolean = true,

    // Retry settings
    val retryEnabled: Boolean = true,
    val retryMaxAttempts: Int = 3,
    val retryInitialDelayMs: Long = 1000,
    val retryMaxDelayMs: Long = 30000,
    val retryBackoffMultiplier: Double = 2.0,
    val retryableStatusCodes: Set<Int> = setOf(429, 500, 502, 503, 504)
) {
    init {
        require(maxLength > 0) { "maxLength must be > 0" }
        require(timeoutMinutes >= 0) { "timeoutMinutes must be >= 0" }
        require(timeoutCheckIntervalMs > 0) { "timeoutCheckIntervalMs must be > 0" }
        require(maxConcurrent > 0) { "maxConcurrent must be > 0" }
        require(p1MaxThreads in 1..maxConcurrent) { "p1MaxThreads must be in 1..maxConcurrent" }
        require(p3MaxThreads in 1..maxConcurrent) { "p3MaxThreads must be in 1..maxConcurrent" }
        require(retryMaxAttempts >= 1) { "retryMaxAttempts must be at least 1" }
        require(retryInitialDelayMs > 0) { "retryInitialDelayMs must be positive" }
        require(retryMaxDelayMs >= retryInitialDelayMs) { "retryMaxDelayMs must be >= retryInitialDelayMs" }
        require(retryBackoffMultiplier >= 1.0) { "retryBackoffMultiplier must be >= 1.0" }
    }

    /**
     * Returns max threads allowed for a priority.
     * P2 has no per-priority limit (uses remaining global capacity).
     */
    fun maxThreadsFor(priority: ru.ddd.llmproxy.domain.model.Priority): Int = when (priority) {
        ru.ddd.llmproxy.domain.model.Priority.P1 -> p1MaxThreads
        ru.ddd.llmproxy.domain.model.Priority.P2 -> maxConcurrent
        ru.ddd.llmproxy.domain.model.Priority.P3 -> p3MaxThreads
    }

    /**
     * Returns whether this priority can preempt others.
     * Only P1 can preempt when preemption is enabled.
     */
    fun canPreempt(priority: ru.ddd.llmproxy.domain.model.Priority): Boolean =
        preemptionEnabled && priority == ru.ddd.llmproxy.domain.model.Priority.P1

    /**
     * Returns priorities that can be preempted by the given priority.
     */
    fun preemptibleBy(priority: ru.ddd.llmproxy.domain.model.Priority): Set<ru.ddd.llmproxy.domain.model.Priority> =
        if (canPreempt(priority)) {
            ru.ddd.llmproxy.domain.model.Priority.entries.filter { it.level > priority.level }.toSet()
        } else {
            emptySet()
        }

    /**
     * Checks if an HTTP status code should trigger a retry.
     */
    fun isRetryable(statusCode: Int): Boolean = statusCode in retryableStatusCodes

    companion object {
        /**
         * Creates QueueConfig from LlmProxyProperties.QueueConfig.
         */
        fun from(queueConfig: LlmProxyProperties.QueueConfig): QueueConfig = QueueConfig(
            maxLength = queueConfig.maxLength,
            timeoutMinutes = queueConfig.timeoutMinutes,
            timeoutCheckIntervalMs = queueConfig.timeoutCheckIntervalMs,
            defaultPriority = queueConfig.defaultPriority,
            maxConcurrent = queueConfig.maxConcurrent,
            p1MaxThreads = queueConfig.p1MaxThreads,
            p3MaxThreads = queueConfig.p3MaxThreads,
            preemptionEnabled = queueConfig.preemptionEnabled,
            retryEnabled = queueConfig.retry.enabled,
            retryMaxAttempts = queueConfig.retry.maxAttempts,
            retryInitialDelayMs = queueConfig.retry.initialDelayMs,
            retryMaxDelayMs = queueConfig.retry.maxDelayMs,
            retryBackoffMultiplier = queueConfig.retry.backoffMultiplier,
            retryableStatusCodes = queueConfig.retry.retryableStatusCodes
        )

        /**
         * Default configuration.
         */
        val DEFAULT = QueueConfig()
    }
}
