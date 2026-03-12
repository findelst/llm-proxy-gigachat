package ru.ddd.llmproxy.domain.model

import kotlinx.coroutines.Job
import java.time.Duration
import java.time.Instant

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
     * Uses cooperative coroutine cancellation.
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
     * Whether this request has completed (successfully or with exception).
     */
    val isCompleted: Boolean
        get() = job.isCompleted

    /**
     * Whether this request was cancelled.
     */
    val isCancelled: Boolean
        get() = job.isCancelled
}
