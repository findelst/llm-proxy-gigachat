package ru.ddd.llmproxy.domain.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Entity representing a request in the priority queue.
 * Contains all tracking data inline (no separate QueueMetrics needed).
 *
 * @param id Unique identifier for the request
 * @param priority Priority level for this request
 * @param payload The actual request payload
 * @param endpoint The API endpoint being called
 * @param queuedAt When the request was queued
 * @param startedAt When processing started (mutable, set when dequeued)
 * @param completedAt When processing completed (mutable, set when done)
 * @param retryAttempts Number of retry attempts made
 * @param deferred CompletableDeferred for async result delivery
 */
data class QueuedRequest<T, R>(
    val id: String = UUID.randomUUID().toString(),
    val priority: Priority,
    val payload: T,
    val endpoint: String = "",
    val queuedAt: Instant = Instant.now(),
    var startedAt: Instant? = null,
    var completedAt: Instant? = null,
    var retryAttempts: Int = 0,
    val deferred: CompletableDeferred<R> = CompletableDeferred()
) : Comparable<QueuedRequest<T, R>> {

    /**
     * Time spent waiting in queue in milliseconds.
     * Null if processing hasn't started yet.
     */
    val queueWaitMs: Long?
        get() = startedAt?.let { start ->
            Duration.between(queuedAt, start).toMillis()
        }

    /**
     * Time spent processing the request by the provider in milliseconds.
     * Null if processing hasn't completed yet.
     */
    val providerLatencyMs: Long?
        get() = completedAt?.let { end ->
            startedAt?.let { start ->
                Duration.between(start, end).toMillis()
            }
        }

    /**
     * Compares by priority level (lower level = higher priority = processed first).
     */
    override fun compareTo(other: QueuedRequest<T, R>): Int {
        return this.priority.level.compareTo(other.priority.level)
    }

    /**
     * Marks processing as started.
     */
    fun startProcessing() {
        startedAt = Instant.now()
    }

    /**
     * Marks processing as completed.
     */
    fun completeProcessing() {
        completedAt = Instant.now()
    }

    /**
     * Records a retry attempt.
     */
    fun recordRetry() {
        retryAttempts++
    }

    /**
     * Completes the request with a successful result.
     */
    fun complete(result: R) {
        deferred.complete(result)
    }

    /**
     * Completes the request with an exception.
     */
    fun completeExceptionally(exception: Throwable) {
        deferred.completeExceptionally(exception)
    }

    companion object {
        /**
         * Creates a new QueuedRequest with generated ID.
         */
        fun <T, R> create(
            payload: T,
            priority: Priority,
            endpoint: String
        ): QueuedRequest<T, R> = QueuedRequest(
            priority = priority,
            payload = payload,
            endpoint = endpoint
        )
    }
}

/**
 * Entity representing a request currently being processed.
 *
 * @param requestId Unique identifier for the request
 * @param priority Priority level for this request
 * @param job The coroutine job executing this request
 * @param startTime When processing started
 * @param queuedAt When the request was originally queued
 */
data class RunningRequest(
    val requestId: String,
    val priority: Priority,
    val job: Job,
    val startTime: Instant = Instant.now(),
    val queuedAt: Instant
) {
    /**
     * Elapsed time since processing started in milliseconds.
     */
    val elapsedTimeMs: Long
        get() = Duration.between(startTime, Instant.now()).toMillis()

    /**
     * Time spent waiting in queue before processing started.
     */
    val queueWaitMs: Long
        get() = Duration.between(queuedAt, startTime).toMillis()

    /**
     * Cancels this request with the given reason.
     */
    fun cancel(reason: String) {
        job.cancel(kotlinx.coroutines.CancellationException(reason))
    }

    /**
     * Whether this request is still actively processing.
     */
    val isActive: Boolean
        get() = job.isActive

    /**
     * Whether this request has completed.
     */
    val isCompleted: Boolean
        get() = job.isCompleted

    /**
     * Whether this request was cancelled.
     */
    val isCancelled: Boolean
        get() = job.isCancelled
}
