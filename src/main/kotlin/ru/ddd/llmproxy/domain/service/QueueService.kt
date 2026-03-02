package ru.ddd.llmproxy.domain.service

import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.QueuedRequest

/**
 * Service interface for managing a priority-based request queue.
 *
 * This interface defines the contract for queue implementations that:
 * - Maintain FIFO order within each priority level
 * - Enforce maximum concurrency limits per priority
 * - Support async request processing with coroutines
 */
interface QueueService<T, R> {

    /**
     * Enqueues a request for processing.
     *
     * @param request The request to enqueue
     * @return Result containing the response or queue overflow error
     * @throws QueueOverflowException if the queue is at maximum capacity
     */
    suspend fun enqueue(request: QueuedRequest<T, R>): R

    /**
     * Attempts to enqueue a request without blocking.
     *
     * @param request The request to enqueue
     * @return Result.success with response if enqueued, Result.failure if queue is full
     */
    suspend fun tryEnqueue(request: QueuedRequest<T, R>): Result<R>

    /**
     * Returns the current queue length for a specific priority.
     *
     * @param priority The priority level to check
     * @return Number of requests waiting in queue for this priority
     */
    fun queueLength(priority: Priority): Int

    /**
     * Returns the total queue length across all priorities.
     *
     * @return Total number of requests waiting in queue
     */
    fun totalQueueLength(): Int

    /**
     * Returns the number of requests currently being processed for a priority.
     *
     * @param priority The priority level to check
     * @return Number of in-flight requests for this priority
     */
    fun inFlight(priority: Priority): Int

    /**
     * Returns whether the queue can accept more requests.
     *
     * @return true if queue has capacity, false if full
     */
    fun hasCapacity(): Boolean

    /**
     * Returns the maximum queue capacity.
     *
     * @return Maximum number of requests the queue can hold
     */
    fun maxCapacity(): Int

    /**
     * Shuts down the queue service gracefully.
     * Waits for in-flight requests to complete.
     */
    suspend fun shutdown()
}

/**
 * Exception thrown when the queue is at maximum capacity.
 */
class QueueOverflowException(
    message: String = "Queue is full. Try again later.",
    val priority: Priority? = null
) : RuntimeException(message)

/**
 * Exception thrown when a request validation fails.
 */
class InvalidRequestException(
    message: String,
    val errorCode: String = "invalid_request"
) : RuntimeException(message)

/**
 * Exception thrown when the LLM provider returns an error.
 */
class ProviderException(
    message: String,
    val statusCode: Int = 500,
    cause: Throwable? = null
) : RuntimeException(message, cause)
