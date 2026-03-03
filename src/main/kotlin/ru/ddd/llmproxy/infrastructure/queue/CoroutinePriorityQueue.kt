package ru.ddd.llmproxy.infrastructure.queue

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
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
    @Qualifier("ioDispatcher") private val dispatcher: CoroutineDispatcher
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
        // Log warning if deprecated priority-slots configuration is present
        val prioritySlots = properties.queue.prioritySlots
        if (prioritySlots.isNotEmpty()) {
            log.warn {
                "Configuration 'priority-slots' is deprecated and will be ignored. " +
                        "Per-priority concurrency limits have been removed. " +
                        "Queue now uses strict FIFO ordering within each priority level."
            }
        }

        priorityChannel = PriorityChannel(properties.queue.maxLength)
        retryExecutor = RetryExecutor(properties.queue.retry, metricsPort)

        log.info {
            "Initialized FIFO priority queue with max-length=${properties.queue.maxLength}, " +
                    "retry enabled=${properties.queue.retry.enabled}"
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
     * Processes a single request with retry support.
     */
    private suspend fun processRequest(
        request: QueuedRequest<T, R>,
        processor: suspend (T) -> R,
        priority: Priority
    ) {
        updateQueueLength(priority, -1)
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
        } catch (e: Exception) {
            request.completeExceptionally(e)
            log.error(e) { "Error processing request ${request.id}" }
        } finally {
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

    @PreDestroy
    fun destroy() {
        isShutdown = true
        priorityChannel.close()
        scope.cancel()
        log.info { "Priority queue destroyed" }
    }
}
