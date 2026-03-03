package ru.ddd.llmproxy.infrastructure.queue

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.*
import ru.ddd.llmproxy.domain.service.QueueService
import ru.ddd.llmproxy.domain.service.QueueOverflowException
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties

private val log = KotlinLogging.logger {}

/**
 * Coroutine-based priority queue implementation.
 *
 * Features:
 * - FIFO ordering within each priority level
 * - Bounded parallelism per priority using semaphores
 * - Overflow protection with configurable max length
 * - Metrics integration
 */
@Component
class CoroutinePriorityQueue<T : Any, R : Any>(
    private val properties: LlmProxyProperties,
    private val metricsPort: MetricsPort,
    @Qualifier("ioDispatcher") private val dispatcher: CoroutineDispatcher
) : QueueService<T, R> {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Per-priority channels and semaphores
    private val priorityChannels: Map<Priority, Channel<QueuedRequest<T, R>>>
    private val prioritySemaphores: Map<Priority, Semaphore>
    private val prioritySlots: List<PrioritySlot>

    // Queue state tracking
    private val _queueLengths = MutableStateFlow<Map<Priority, Int>>(emptyMap())
    val queueLengths: StateFlow<Map<Priority, Int>> = _queueLengths.asStateFlow()

    private val _inFlightCounts = MutableStateFlow<Map<Priority, Int>>(emptyMap())
    val inFlightCounts: StateFlow<Map<Priority, Int>> = _inFlightCounts.asStateFlow()

    @Volatile
    private var isShutdown = false

    init {
        prioritySlots = properties.queue.prioritySlots.entries
            .map { PrioritySlot.fromEntry(it) }
            .sortedBy { it.toPriority().level }

        priorityChannels = prioritySlots.associate { slot ->
            val priority = slot.toPriority()
            priority to Channel(properties.queue.maxLength)
        }

        prioritySemaphores = prioritySlots.associate { slot ->
            val priority = slot.toPriority()
            priority to Semaphore(slot.maxConcurrency)
        }

        log.info {
            "Initialized priority queue with slots: " +
                    prioritySlots.joinToString { "${it.name}=${it.maxConcurrency}" }
        }
    }

    override suspend fun enqueue(request: QueuedRequest<T, R>): R {
        if (isShutdown) {
            throw QueueOverflowException("Queue is shutting down")
        }

        val channel = priorityChannels[request.priority]
            ?: throw QueueOverflowException("Unknown priority: ${request.priority}")

        // Try to enqueue, throw overflow if full
        val result = channel.trySend(request)
        if (result.isFailure) {
            metricsPort.recordQueueOverflow(request.priority)
            throw QueueOverflowError(priority = request.priority)
        }

        updateQueueLength(request.priority, 1)

        log.debug { "Enqueued request ${request.id} with priority ${request.priority}" }

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
        priorityChannels.values.forEach { it.close() }
        log.info { "Priority queue shutdown initiated" }
    }

    /**
     * Starts processing requests from all priority channels.
     * Should be called during application startup.
     */
    fun startProcessing(processor: suspend (T) -> R) {
        prioritySlots.forEach { slot ->
            val priority = slot.toPriority()
            val channel = priorityChannels[priority]!!
            val semaphore = prioritySemaphores[priority]!!

            scope.launch {
                for (request in channel) {
                    launch {
                        processRequest(request, semaphore, processor, priority)
                    }
                }
            }
        }

        log.info { "Started processing for all priority levels" }
    }

    /**
     * Processes a single request with semaphore-controlled concurrency.
     */
    private suspend fun processRequest(
        request: QueuedRequest<T, R>,
        semaphore: Semaphore,
        processor: suspend (T) -> R,
        priority: Priority
    ) {
        updateQueueLength(priority, -1)

        semaphore.acquire()
        try {
            updateInFlight(priority, 1)
            metricsPort.setInFlight(priority, inFlight(priority))
            metricsPort.setQueueLength(priority, queueLength(priority))

            // Mark processing start for queue wait time calculation
            request.metrics = request.metrics.startProcessing()

            log.debug { "Processing request ${request.id} with priority $priority" }

            val result = processor(request.payload)

            // Mark processing complete for latency calculation
            request.metrics = request.metrics.completeProcessing()

            request.complete(result)
        } catch (e: Exception) {
            request.completeExceptionally(e)
            log.error(e) { "Error processing request ${request.id}" }
        } finally {
            semaphore.release()
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
        priorityChannels.values.forEach { it.close() }
        scope.cancel()
        log.info { "Priority queue destroyed" }
    }
}
