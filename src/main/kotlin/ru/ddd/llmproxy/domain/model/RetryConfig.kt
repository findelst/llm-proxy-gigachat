package ru.ddd.llmproxy.domain.model

/**
 * Configuration for retry behavior in the queue.
 *
 * @param enabled Whether retry is enabled
 * @param maxAttempts Maximum number of retry attempts (including initial attempt)
 * @param initialDelayMs Initial delay before first retry in milliseconds
 * @param maxDelayMs Maximum delay between retries in milliseconds
 * @param backoffMultiplier Multiplier for exponential backoff
 * @param retryableStatusCodes HTTP status codes that should trigger retry
 */
data class RetryConfig(
    val enabled: Boolean = true,
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0,
    val retryableStatusCodes: Set<Int> = setOf(429, 500, 502, 503, 504)
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(initialDelayMs > 0) { "initialDelayMs must be positive" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
    }

    /**
     * Checks if an HTTP status code should trigger a retry.
     *
     * @param statusCode The HTTP status code
     * @return true if the status code is retryable
     */
    fun isRetryable(statusCode: Int): Boolean = statusCode in retryableStatusCodes

    companion object {
        /**
         * Default retry configuration.
         */
        val DEFAULT = RetryConfig()

        /**
         * Disabled retry configuration.
         */
        val DISABLED = RetryConfig(enabled = false)
    }
}
