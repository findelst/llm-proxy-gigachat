package ru.ddd.llmproxy.domain.model

import java.time.Instant

/**
 * Entity representing metrics for a queued request.
 */
data class QueueMetrics(
    val requestId: String,
    val priority: Priority,
    val endpoint: String,
    val queuedAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null
) {
    /**
     * Time spent waiting in queue in milliseconds.
     * Null if processing hasn't started yet.
     */
    val queueWaitMs: Long?
        get() = startedAt?.let { start ->
            start.toEpochMilli() - queuedAt.toEpochMilli()
        }

    /**
     * Time spent processing the request by the provider in milliseconds.
     * Null if processing hasn't completed yet.
     */
    val providerLatencyMs: Long?
        get() = completedAt?.let { end ->
            startedAt?.let { start ->
                end.toEpochMilli() - start.toEpochMilli()
            }
        }

    /**
     * Creates a new instance with processing started timestamp.
     */
    fun startProcessing(): QueueMetrics = copy(startedAt = Instant.now())

    /**
     * Creates a new instance with processing completed timestamp.
     */
    fun completeProcessing(): QueueMetrics = copy(completedAt = Instant.now())

    companion object {
        /**
         * Creates a new QueueMetrics instance for a request.
         */
        fun create(
            requestId: String,
            priority: Priority,
            endpoint: String
        ): QueueMetrics = QueueMetrics(
            requestId = requestId,
            priority = priority,
            endpoint = endpoint,
            queuedAt = Instant.now()
        )
    }
}
