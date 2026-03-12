package ru.ddd.llmproxy.infrastructure.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

/**
 * Component managing slot allocation and preemption for priority-based concurrency.
 *
 * Features:
 * - Global concurrency limit via Semaphore
 * - Per-priority thread limits (P1 max 2, P3 max 1)
 * - Preemption of lower priority requests by P1
 * - Metrics recording for monitoring
 */
@Component
class ConcurrencyController(
    private val config: ConcurrencyConfig,
    private val metricsPort: MetricsPort
) {
    // Global semaphore for max concurrent requests
    private val globalSemaphore = Semaphore(config.maxConcurrent)

    // Per-priority counters for tracking active requests
    private val p1Counter = AtomicInteger(0)
    private val p2Counter = AtomicInteger(0)
    private val p3Counter = AtomicInteger(0)

    // All execution slots
    private val slots = (1..config.maxConcurrent).map { ExecutionSlot(it) }.toList()

    // Map of running requests by ID for quick lookup
    private val runningRequests = ConcurrentHashMap<String, RunningRequest>()

    /**
     * Current concurrency statistics.
     */
    val stats: ConcurrencyStats
        get() = ConcurrencyStats.from(slots, config)

    /**
     * Attempts to acquire a slot for a request.
     *
     * @param requestId Unique identifier for the request
     * @param priority Priority of the request
     * @param job The coroutine job for the request
     * @param queuedAt When the request was queued
     * @return true if slot was acquired, false if limits exceeded
     */
    fun tryAcquire(
        requestId: String,
        priority: Priority,
        job: Job,
        queuedAt: Instant
    ): Boolean {
        // Check per-priority limits first
        if (!canAcceptPriority(priority)) {
            log.debug { "Cannot acquire slot for $requestId: ${priority.value} limit reached" }
            return false
        }

        // Try to acquire global semaphore (non-blocking)
        if (!globalSemaphore.tryAcquire()) {
            log.debug { "Cannot acquire slot for $requestId: global limit reached" }
            return false
        }

        // Find an available slot
        val slot = slots.find { !it.isOccupied }
        if (slot == null) {
            globalSemaphore.release()
            log.warn { "No available slot found despite semaphore acquisition" }
            return false
        }

        // Create running request and allocate slot
        val runningRequest = RunningRequest(
            requestId = requestId,
            priority = priority,
            job = job,
            queuedAt = queuedAt
        )

        slot.allocate(runningRequest)
        runningRequests[requestId] = runningRequest
        incrementCounter(priority)

        // Update metrics
        updateMetrics()

        log.debug { "Acquired slot for request $requestId with priority ${priority.value}" }
        return true
    }

    /**
     * Releases a slot after request completion.
     *
     * @param requestId The request to release
     */
    fun release(requestId: String) {
        val runningRequest = runningRequests.remove(requestId)
        if (runningRequest != null) {
            // Find and release the slot
            val slot = slots.find { it.currentRequest?.requestId == requestId }
            slot?.release()

            // Release global semaphore
            globalSemaphore.release()

            // Decrement counter
            decrementCounter(runningRequest.priority)

            // Update metrics
            updateMetrics()

            log.debug { "Released slot for request $requestId" }
        }
    }

    /**
     * Preempts running requests to make room for a higher priority request.
     *
     * @param forPriority The priority that needs slots
     * @param slotsNeeded Number of slots to free
     * @return List of preempted request IDs
     */
    fun preemptSlots(forPriority: Priority, slotsNeeded: Int): List<String> {
        if (!config.canPreempt(forPriority)) {
            log.warn { "Preemption not allowed for priority ${forPriority.value}" }
            return emptyList()
        }

        val preemptable = slots
            .filter { it.canBePreemptedBy(forPriority, config) }
            .sortedWith(compareBy(
                { -(it.currentRequest?.priority?.level ?: Int.MIN_VALUE) }, // Higher level (P3) first
                { it.currentRequest?.startTime ?: Instant.MAX }
            ))
            .take(slotsNeeded)

        val preemptedIds = mutableListOf<String>()

        for (slot in preemptable) {
            val request = slot.currentRequest ?: continue

            val elapsedMs = Duration.between(request.startTime, Instant.now()).toMillis()

            log.info {
                "Preempting request ${request.requestId} (${request.priority.value}) " +
                "for ${forPriority.value} after ${elapsedMs}ms"
            }

            // Record preemption event
            metricsPort.recordPreemption(request.priority, forPriority)

            // Cancel the request
            request.job.cancel(CancellationException("Preempted by ${forPriority.value}"))

            preemptedIds.add(request.requestId)
        }

        if (preemptedIds.isNotEmpty()) {
            log.info { "Preempted ${preemptedIds.size} requests for ${forPriority.value}" }
        }

        return preemptedIds
    }

    /**
     * Gets a running request by ID.
     */
    fun getRunningRequest(requestId: String): RunningRequest? =
        runningRequests[requestId]

    /**
     * Checks if a priority can accept new requests.
     */
    private fun canAcceptPriority(priority: Priority): Boolean {
        return when (priority) {
            Priority.P1 -> p1Counter.get() < config.p1MaxThreads
            Priority.P2 -> true // P2 has no per-priority limit
            Priority.P3 -> p3Counter.get() < config.p3MaxThreads
        }
    }

    /**
     * Increments the counter for a priority.
     */
    private fun incrementCounter(priority: Priority) {
        when (priority) {
            Priority.P1 -> p1Counter.incrementAndGet()
            Priority.P2 -> p2Counter.incrementAndGet()
            Priority.P3 -> p3Counter.incrementAndGet()
        }
    }

    /**
     * Decrements the counter for a priority.
     */
    private fun decrementCounter(priority: Priority) {
        when (priority) {
            Priority.P1 -> p1Counter.decrementAndGet()
            Priority.P2 -> p2Counter.decrementAndGet()
            Priority.P3 -> p3Counter.decrementAndGet()
        }
    }

    /**
     * Updates metrics gauges with current concurrency stats.
     */
    private fun updateMetrics() {
        val currentStats = stats
        metricsPort.setConcurrentRequests(Priority.P1, currentStats.p1Running)
        metricsPort.setConcurrentRequests(Priority.P2, currentStats.p2Running)
        metricsPort.setConcurrentRequests(Priority.P3, currentStats.p3Running)
        metricsPort.setAvailableSlots(currentStats.availableSlots)
    }

    /**
     * Returns current count for a priority.
     */
    fun getCount(priority: Priority): Int = when (priority) {
        Priority.P1 -> p1Counter.get()
        Priority.P2 -> p2Counter.get()
        Priority.P3 -> p3Counter.get()
    }

    /**
     * Returns all currently running requests.
     */
    fun getAllRunning(): List<RunningRequest> =
        runningRequests.values.toList()
}
