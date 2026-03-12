package ru.ddd.llmproxy.infrastructure.queue

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.*
import ru.ddd.llmproxy.domain.service.QueueService
import ru.ddd.llmproxy.domain.service.QueueOverflowException
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

private val log = KotlinLogging.logger {}

/**
 * Coroutine-based priority queue implementation.
 *
 * Features:
 * - Global priority ordering using single PriorityChannel
 * - FIFO ordering within each priority level
 * - Overflow protection with configurable max length
 * - Automatic retry with exponential backoff
 * - Metrics integration
 */
@Component
class CoroutinePriorityQueue<T : Any, R : Any>(
    private val properties: LlmProxyProperties,
    private val metricsPort: MetricsPort,
    @Qualifier("ioDispatcher") private val dispatcher: CoroutineDispatcher,
    private val concurrencyController: ConcurrencyController
) : QueueService<T, R> {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Single priority channel for global ordering
    private val priorityChannel: PriorityChannel<QueuedRequest<T, R>>

    // Sequence counter for FIFO within priority
    private val sequenceCounter = AtomicLong(0)

    // Retry executor
    private val retryExecutor: RetryExecutor

    // Queue state tracking
    private val _queueLengths = MutableStateFlow<Map<Priority, Int>>(emptyMap())
    val queueLengths: StateFlow<Map<Priority, Int>> = _queueLengths.asStateFlow()

    private val _inFlightCounts = MutableStateFlow<Map<Priority, Int>>(emptyMap())
    val inFlightCounts: StateFlow<Map<Priority, Int>> = _inFlightCounts.asStateFlow()

    @Volatile
    private var isShutdown = false

    init {
        priorityChannel = PriorityChannel(properties.queue.maxLength)
        retryExecutor = RetryExecutor(properties.queue.retry, metricsPort)

        // Start timeout cleanup coroutine
        startTimeoutCleanup()

        log.info {
            "Initialized FIFO priority queue with max-length=${properties.queue.maxLength}, " +
                    "retry enabled=${properties.queue.retry.enabled}, " +
                    "timeout-minutes=${properties.queue.timeoutMinutes}"
        }
    }

    override suspend fun enqueue(request: QueuedRequest<T, R>): R {
        if (isShutdown) {
            throw QueueOverflowException("Queue is shutting down")
        }

        // Get sequence number for FIFO ordering within priority
        val sequenceNumber = sequenceCounter.getAndIncrement()

        // Try to enqueue with priority
        val result = priorityChannel.trySend(
            request,
            request.priority.level,
            sequenceNumber
        )

        if (result.isFailure) {
            metricsPort.recordQueueOverflow(request.priority)
            throw QueueOverflowError(priority = request.priority)
        }

        updateQueueLength(request.priority, 1)

        log.debug { "Enqueued request ${request.id} with priority ${request.priority}, seq=$sequenceNumber" }

        // Wait for result
        return request.deferred.await()
    }

    override suspend fun tryEnqueue(request: QueuedRequest<T, R>): Result<R> {
        return try {
            Result.success(enqueue(request))
        } catch (e: QueueOverflowException) {
            Result.failure(e)
        } catch (e: QueueOverflowError) {
            Result.failure(e)
        }
    }

    override fun queueLength(priority: Priority): Int {
        return _queueLengths.value[priority] ?: 0
    }

    override fun totalQueueLength(): Int {
        return _queueLengths.value.values.sum()
    }

    override fun inFlight(priority: Priority): Int {
        return _inFlightCounts.value[priority] ?: 0
    }

    override fun hasCapacity(): Boolean {
        return totalQueueLength() < properties.queue.maxLength
    }

    override fun maxCapacity(): Int = properties.queue.maxLength

    override suspend fun shutdown() {
        isShutdown = true
        priorityChannel.close()
        log.info { "Priority queue shutdown initiated" }
    }

    /**
     * Starts processing requests from the priority channel.
     * Should be called during application startup.
     */
    fun startProcessing(processor: suspend (T) -> R) {
        // Single consumer that processes items by priority
        scope.launch {
            while (!priorityChannel.isClosedForReceive) {
                try {
                    val prioritizedItem = priorityChannel.receive()
                    val request = prioritizedItem.item

                    launch {
                        processRequest(request, processor, request.priority)
                    }
                } catch (e: Exception) {
                    if (priorityChannel.isClosedForReceive && priorityChannel.isEmpty) {
                        break
                    }
                    log.error(e) { "Error receiving from priority channel" }
                }
            }
        }

        log.info { "Started priority channel processing" }
    }

    /**
     * Processes a single request with retry support and concurrency control.
     */
    private suspend fun processRequest(
        request: QueuedRequest<T, R>,
        processor: suspend (T) -> R,
        priority: Priority
    ) {
        updateQueueLength(priority, -1)

        // Try to acquire a slot from ConcurrencyController
        val job = coroutineContext.job
        val acquired = concurrencyController.tryAcquire(
            requestId = request.id,
            priority = priority,
            job = job,
            queuedAt = request.metrics.queuedAt
        )

        if (!acquired) {
            // Handle based on priority
            when (priority) {
                Priority.P1 -> {
                    // P1 should preempt if needed
                    if (properties.queue.preemptionEnabled) {
                        val slotsNeeded = minOf(
                            properties.queue.p1MaxThreads,
                            properties.queue.maxConcurrent - concurrencyController.stats.totalRunning
                        ).coerceAtLeast(1)

                        val preempted = concurrencyController.preemptSlots(priority, slotsNeeded)
                        if (preempted.isNotEmpty()) {
                            log.info { "Preempted ${preempted.size} requests for P1" }
                        }

                        // Try again after preemption
                        if (!concurrencyController.tryAcquire(request.id, priority, job, request.metrics.queuedAt)) {
                            request.completeExceptionally(
                                QueueOverflowError("Queue is full. Try again later.", priority)
                            )
                            return
                        }
                    } else {
                        request.completeExceptionally(
                            QueueOverflowError("Queue is full. Try again later.", priority)
                        )
                        return
                    }
                }
                Priority.P3 -> {
                    // P3 is throttled
                    metricsPort.recordP3Throttled()
                    request.completeExceptionally(
                        P3ThrottledError(
                            message = "Low priority requests are limited to 1 concurrent execution",
                            queuePosition = queueLength(priority),
                            estimatedWaitSeconds = 30
                        )
                    )
                    return
                }
                Priority.P2 -> {
                    // P2 should always be able to acquire if within global limits
                    request.completeExceptionally(
                        QueueOverflowError("Queue is full. Try again later.", priority)
                    )
                    return
                }
            }
        }

        updateInFlight(priority, 1)
        metricsPort.setInFlight(priority, inFlight(priority))
        metricsPort.setQueueLength(priority, queueLength(priority))

        try {
            // Mark processing start for queue wait time calculation
            request.metrics = request.metrics.startProcessing()

            log.debug { "Processing request ${request.id} with priority $priority" }

            val result = if (properties.queue.retry.enabled) {
                // Execute with retry
                var retryCount = 0
                retryExecutor.executeWithRetry(
                    operation = { processor(request.payload) },
                    priority = priority,
                    onRetry = { attempt, _ ->
                        retryCount = attempt
                        request.metrics = request.metrics.recordRetry()
                    }
                ).also {
                    if (retryCount > 0) {
                        metricsPort.recordRetrySuccess(retryCount + 1)
                    }
                }
            } else {
                // Execute without retry
                processor(request.payload)
            }

            // Mark processing complete for latency calculation
            request.metrics = request.metrics.completeProcessing()

            request.complete(result)
        } catch (e: CancellationException) {
            // Request was preempted
            log.info { "Request ${request.id} was preempted: ${e.message}" }
            request.completeExceptionally(
                PreemptionError(
                    priority = priority,
                    preemptedBy = Priority.P1,
                    elapsedMs = java.time.Duration.between(
                        request.metrics.startedAt ?: Instant.now(),
                        Instant.now()
                    ).toMillis()
                )
            )
        } catch (e: Exception) {
            request.completeExceptionally(e)
            log.error(e) { "Error processing request ${request.id}" }
        } finally {
            concurrencyController.release(request.id)
            updateInFlight(priority, -1)
            metricsPort.setInFlight(priority, inFlight(priority))
        }
    }

    private fun updateQueueLength(priority: Priority, delta: Int) {
        _queueLengths.value = _queueLengths.value.toMutableMap().apply {
            this[priority] = (this[priority] ?: 0) + delta
        }
        metricsPort.setQueueLength(priority, _queueLengths.value[priority] ?: 0)
    }

    private fun updateInFlight(priority: Priority, delta: Int) {
        _inFlightCounts.value = _inFlightCounts.value.toMutableMap().apply {
            this[priority] = (this[priority] ?: 0) + delta
        }
    }

    /**
     * Starts the periodic timeout cleanup coroutine.
     * Removes requests that have been waiting longer than the configured timeout.
     */
    private fun startTimeoutCleanup() {
        val timeoutMinutes = properties.queue.timeoutMinutes
        val checkIntervalMs = properties.queue.timeoutCheckIntervalMs

        // Skip cleanup if timeout is disabled (0)
        if (timeoutMinutes <= 0) {
            log.info { "Queue timeout cleanup disabled (timeout-minutes=0)" }
            return
        }

        val maxAgeMs = timeoutMinutes * 60 * 1000

        scope.launch {
            log.info { "Started queue timeout cleanup with interval=${checkIntervalMs}ms, max-age=${maxAgeMs}ms" }

            while (!isShutdown) {
                try {
                    delay(checkIntervalMs)
                    cleanupExpiredRequests(maxAgeMs)
                } catch (e: Exception) {
                    if (!isShutdown) {
                        log.error(e) { "Error during timeout cleanup" }
                    }
                }
            }
        }
    }

    /**
     * Cleans up expired requests from the queue.
     */
    private suspend fun cleanupExpiredRequests(maxAgeMs: Long) {
        val now = Instant.now()

        val removedCount = priorityChannel.removeExpired(
            maxAgeMs = maxAgeMs,
            getAgeMs = { request ->
                val queuedAt = request.metrics.queuedAt
                now.toEpochMilli() - queuedAt.toEpochMilli()
            },
            onExpired = { prioritizedItem ->
                val request = prioritizedItem.item
                val waitTimeMs = now.toEpochMilli() - request.metrics.queuedAt.toEpochMilli()

                // Update queue length
                updateQueueLength(request.priority, -1)

                // Record metrics
                metricsPort.recordQueueTimeout(request.priority)

                // Complete the request with timeout exception
                request.completeExceptionally(
                    QueueTimeoutError(
                        message = "Request timed out after waiting ${waitTimeMs / 1000} seconds in queue",
                        priority = request.priority,
                        waitTimeMs = waitTimeMs
                    )
                )

                log.warn {
                    "Request ${request.id} timed out after ${waitTimeMs}ms in queue " +
                            "(priority=${request.priority.value})"
                }
            }
        )

        if (removedCount > 0) {
            log.info { "Removed $removedCount expired requests from queue" }
        }
    }

    @PreDestroy
    fun destroy() {
        isShutdown = true
        priorityChannel.close()
        scope.cancel()
        log.info { "Priority queue destroyed" }
    }
}
