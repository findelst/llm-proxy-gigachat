package ru.ddd.llmproxy.unit.infrastructure.queue

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.QueueMetrics
import ru.ddd.llmproxy.domain.model.QueueTimeoutError
import ru.ddd.llmproxy.domain.model.QueuedRequest
import ru.ddd.llmproxy.domain.model.RetryConfig
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import ru.ddd.llmproxy.infrastructure.metrics.PrometheusMetrics
import ru.ddd.llmproxy.infrastructure.queue.PriorityChannel
import java.time.Instant

/**
 * Tests for queue timeout functionality.
 *
 * Verifies:
 * - Expired requests are removed from queue
 * - QueueTimeoutException is thrown for timed out requests
 * - Timeout metrics are recorded
 * - Timeout can be disabled (timeoutMinutes=0)
 */
class QueueTimeoutTest {

    private lateinit var channel: PriorityChannel<QueuedRequest<String, String>>
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metrics: PrometheusMetrics

    @BeforeEach
    fun setUp() {
        channel = PriorityChannel(capacity = 10)
        meterRegistry = SimpleMeterRegistry()
        metrics = PrometheusMetrics(meterRegistry)
    }

    // ==================== removeExpired Tests ====================

    @Test
    fun `should remove expired items from queue`() = runTest {
        // Create an "old" request (queued 11 minutes ago)
        val oldRequest = createRequest("old", Priority.P3, Instant.now().minusMillis(11 * 60 * 1000))
        val newRequest = createRequest("new", Priority.P3, Instant.now())

        channel.trySend(oldRequest, Priority.P3.level, 1)
        channel.trySend(newRequest, Priority.P3.level, 2)

        assertEquals(2, channel.size)

        // Remove items older than 10 minutes
        val maxAgeMs = 10 * 60 * 1000L // 10 minutes
        val expiredItems = mutableListOf<ru.ddd.llmproxy.infrastructure.queue.PrioritizedItem<QueuedRequest<String, String>>>()

        val removedCount = channel.removeExpired(
            maxAgeMs = maxAgeMs,
            getAgeMs = { request ->
                val queuedAt = request.metrics.queuedAt
                Instant.now().toEpochMilli() - queuedAt.toEpochMilli()
            },
            onExpired = { expiredItems.add(it) }
        )

        assertEquals(1, removedCount)
        assertEquals(1, expiredItems.size)
        assertEquals("old", expiredItems[0].item.payload)
        assertEquals(1, channel.size)
        assertEquals("new", channel.receive().item.payload)
    }

    @Test
    fun `should not remove items within timeout`() = runTest {
        // Create requests that are within timeout
        val request1 = createRequest("recent1", Priority.P2, Instant.now().minusMillis(5 * 60 * 1000))
        val request2 = createRequest("recent2", Priority.P2, Instant.now().minusMillis(3 * 60 * 1000))

        channel.trySend(request1, Priority.P2.level, 1)
        channel.trySend(request2, Priority.P2.level, 2)

        // Remove items older than 10 minutes
        val maxAgeMs = 10 * 60 * 1000L
        val removedCount = channel.removeExpired(
            maxAgeMs = maxAgeMs,
            getAgeMs = { request ->
                Instant.now().toEpochMilli() - request.metrics.queuedAt.toEpochMilli()
            },
            onExpired = { }
        )

        assertEquals(0, removedCount)
        assertEquals(2, channel.size)
    }

    @Test
    fun `should handle empty queue gracefully`() = runTest {
        val maxAgeMs = 10 * 60 * 1000L
        val removedCount = channel.removeExpired(
            maxAgeMs = maxAgeMs,
            getAgeMs = { Instant.now().toEpochMilli() - it.metrics.queuedAt.toEpochMilli() },
            onExpired = { fail("Should not call onExpired for empty queue") }
        )

        assertEquals(0, removedCount)
    }

    @Test
    fun `should remove multiple expired items`() = runTest {
        // Create multiple old requests
        val old1 = createRequest("old1", Priority.P1, Instant.now().minusMillis(15 * 60 * 1000))
        val old2 = createRequest("old2", Priority.P2, Instant.now().minusMillis(12 * 60 * 1000))
        val new = createRequest("new", Priority.P3, Instant.now())

        channel.trySend(old1, Priority.P1.level, 1)
        channel.trySend(old2, Priority.P2.level, 2)
        channel.trySend(new, Priority.P3.level, 3)

        val maxAgeMs = 10 * 60 * 1000L
        val removedCount = channel.removeExpired(
            maxAgeMs = maxAgeMs,
            getAgeMs = { Instant.now().toEpochMilli() - it.metrics.queuedAt.toEpochMilli() },
            onExpired = { }
        )

        assertEquals(2, removedCount)
        assertEquals(1, channel.size)
    }

    // ==================== Timeout Metrics Tests ====================

    @Test
    fun `should record timeout metrics`() {
        metrics.recordQueueTimeout(Priority.P1)
        metrics.recordQueueTimeout(Priority.P2)
        metrics.recordQueueTimeout(Priority.P1)

        val timeoutCounter = meterRegistry.find(PrometheusMetrics.QUEUE_TIMEOUT).counters()
        assertEquals(3.0, timeoutCounter.sumOf { it.count() }, 0.001)
    }

    @Test
    fun `should record timeout metrics by priority`() {
        metrics.recordQueueTimeout(Priority.P1)
        metrics.recordQueueTimeout(Priority.P1)
        metrics.recordQueueTimeout(Priority.P3)

        val p1Counter = meterRegistry.find(PrometheusMetrics.QUEUE_TIMEOUT)
            .tag("priority", Priority.P1.value)
            .counter()
        val p3Counter = meterRegistry.find(PrometheusMetrics.QUEUE_TIMEOUT)
            .tag("priority", Priority.P3.value)
            .counter()

        assertEquals(2.0, p1Counter?.count() ?: 0.0, 0.001)
        assertEquals(1.0, p3Counter?.count() ?: 0.0, 0.001)
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `should create config with default timeout values`() {
        val props = LlmProxyProperties()
        assertEquals(10L, props.queue.timeoutMinutes)
        assertEquals(60000L, props.queue.timeoutCheckIntervalMs)
    }

    @Test
    fun `should allow custom timeout values`() {
        val props = LlmProxyProperties(
            queue = LlmProxyProperties.QueueConfig(
                timeoutMinutes = 5,
                timeoutCheckIntervalMs = 30000
            )
        )
        assertEquals(5L, props.queue.timeoutMinutes)
        assertEquals(30000L, props.queue.timeoutCheckIntervalMs)
    }

    @Test
    fun `should allow zero timeout to disable cleanup`() {
        val props = LlmProxyProperties(
            queue = LlmProxyProperties.QueueConfig(
                timeoutMinutes = 0,
                timeoutCheckIntervalMs = 60000
            )
        )
        assertEquals(0L, props.queue.timeoutMinutes)
    }

    @Test
    fun `should reject negative timeout minutes`() {
        assertThrows(IllegalArgumentException::class.java) {
            LlmProxyProperties.QueueConfig(timeoutMinutes = -1)
        }
    }

    @Test
    fun `should reject zero or negative check interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            LlmProxyProperties.QueueConfig(timeoutCheckIntervalMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LlmProxyProperties.QueueConfig(timeoutCheckIntervalMs = -1000)
        }
    }

    // ==================== QueueTimeoutException Tests ====================

    @Test
    fun `should create QueueTimeoutException with all fields`() {
        val exception = QueueTimeoutError(
            message = "Request timed out",
            priority = Priority.P2,
            waitTimeMs = 65000L
        )

        assertEquals("queue_timeout", exception.code)
        assertEquals(408, exception.httpStatus)
        assertEquals(Priority.P2, exception.priority)
        assertEquals(65000L, exception.waitTimeMs)
    }

    @Test
    fun `should create QueueTimeoutException with defaults`() {
        val exception = QueueTimeoutError()

        assertEquals("queue_timeout", exception.code)
        assertEquals(408, exception.httpStatus)
        assertNull(exception.priority)
        assertNull(exception.waitTimeMs)
    }

    // ==================== Integration-style Tests ====================

    @Test
    fun `should complete expired request exceptionally`() = runTest {
        val oldRequest = createRequest("old", Priority.P1, Instant.now().minusMillis(11 * 60 * 1000))
        channel.trySend(oldRequest, Priority.P1.level, 1)

        var completedExceptionally = false
        var caughtException: QueueTimeoutError? = null

        // Remove and complete with exception
        channel.removeExpired(
            maxAgeMs = 10 * 60 * 1000L,
            getAgeMs = { Instant.now().toEpochMilli() - it.metrics.queuedAt.toEpochMilli() },
            onExpired = { item ->
                val request = item.item
                request.completeExceptionally(
                    QueueTimeoutError(
                        message = "Request timed out after waiting too long",
                        priority = request.priority,
                        waitTimeMs = Instant.now().toEpochMilli() - request.metrics.queuedAt.toEpochMilli()
                    )
                )
            }
        )

        // Verify the deferred is completed exceptionally
        val result = runCatching { oldRequest.deferred.await() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is QueueTimeoutError)
        caughtException = result.exceptionOrNull() as QueueTimeoutError
        assertEquals(Priority.P1, caughtException.priority)
    }

    // ==================== Helper Methods ====================

    private fun createRequest(
        payload: String,
        priority: Priority,
        queuedAt: Instant = Instant.now()
    ): QueuedRequest<String, String> {
        return QueuedRequest(
            id = "test-${System.nanoTime()}",
            priority = priority,
            payload = payload,
            metrics = QueueMetrics(
                requestId = "test",
                priority = priority,
                endpoint = "/test",
                queuedAt = queuedAt
            ),
            deferred = CompletableDeferred()
        )
    }
}
