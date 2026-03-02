package ru.ddd.llmproxy.domain.model

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

/**
 * Entity representing a request in the priority queue.
 *
 * @param id Unique identifier for the request
 * @param priority Priority level for this request
 * @param payload The actual request payload
 * @param metrics Metrics tracking for this request
 * @param deferred CompletableDeferred for async result delivery
 */
data class QueuedRequest<T, R>(
    val id: String = UUID.randomUUID().toString(),
    val priority: Priority,
    val payload: T,
    val metrics: QueueMetrics,
    val deferred: CompletableDeferred<R> = CompletableDeferred()
) : Comparable<QueuedRequest<T, R>> {

    /**
     * Compares by priority level (lower level = higher priority = processed first).
     */
    override fun compareTo(other: QueuedRequest<T, R>): Int {
        return this.priority.level.compareTo(other.priority.level)
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
         * Creates a new QueuedRequest with generated ID and metrics.
         */
        fun <T, R> create(
            payload: T,
            priority: Priority,
            endpoint: String
        ): QueuedRequest<T, R> {
            val id = UUID.randomUUID().toString()
            return QueuedRequest(
                id = id,
                priority = priority,
                payload = payload,
                metrics = QueueMetrics.create(id, priority, endpoint)
            )
        }
    }
}
