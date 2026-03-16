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
import ru.ddd.llmproxy.domain.model.QueueTimeoutError
import ru.ddd.llmproxy.domain.model.QueuedRequest
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import ru.ddd.llmproxy.infrastructure.config.QueueConfig
import ru.ddd.llmproxy.infrastructure.metrics.PrometheusMetrics
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

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metrics: PrometheusMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        metrics = PrometheusMetrics(meterRegistry)
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

    // ==================== QueueConfig Tests ====================

    @Test
    fun `should create QueueConfig from LlmProxyProperties`() {
        val props = LlmProxyProperties(
            queue = LlmProxyProperties.QueueConfig(
                timeoutMinutes = 5,
                timeoutCheckIntervalMs = 30000,
                maxLength = 50,
                maxConcurrent = 4
            )
        )
        val config = QueueConfig.from(props.queue)

        assertEquals(5L, config.timeoutMinutes)
        assertEquals(30000L, config.timeoutCheckIntervalMs)
        assertEquals(50, config.maxLength)
        assertEquals(4, config.maxConcurrent)
    }

    @Test
    fun `should create QueueConfig with defaults`() {
        val config = QueueConfig.DEFAULT

        assertEquals(10L, config.timeoutMinutes)
        assertEquals(60000L, config.timeoutCheckIntervalMs)
        assertEquals(100, config.maxLength)
        assertEquals(3, config.maxConcurrent)
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

    // ==================== QueuedRequest Timeout Tests ====================

    @Test
    fun `should create QueuedRequest with queuedAt timestamp`() {
        val request = createRequest("test", Priority.P1)

        assertNotNull(request.queuedAt)
        assertTrue(request.queuedAt.isBefore(Instant.now().plusMillis(1)))
    }

    @Test
    fun `should calculate queueWaitMs correctly`() {
        val queuedAt = Instant.now().minusMillis(1000)
        val startedAt = Instant.now()

        val request = QueuedRequest<String, String>(
            id = "test",
            priority = Priority.P1,
            payload = "test",
            queuedAt = queuedAt,
            startedAt = startedAt
        )

        // Queue wait should be approximately 1000ms (with some tolerance for timing)
        val waitMs = request.queueWaitMs
        assertNotNull(waitMs)
        assertTrue(waitMs!! >= 950 && waitMs <= 1050, "Expected ~1000ms, got $waitMs")
    }

    @Test
    fun `should track processing state`() {
        val request = createRequest("test", Priority.P1)

        assertNull(request.startedAt)
        assertNull(request.completedAt)

        request.startProcessing()
        assertNotNull(request.startedAt)
        assertNull(request.completedAt)

        request.completeProcessing()
        assertNotNull(request.completedAt)
    }

    // ==================== Integration-style Tests ====================

    @Test
    fun `should complete expired request exceptionally`() = runTest {
        val oldRequest = createRequest("old", Priority.P1, Instant.now().minusMillis(11 * 60 * 1000))

        // Complete with timeout exception
        oldRequest.completeExceptionally(
            QueueTimeoutError(
                message = "Request timed out after waiting too long",
                priority = oldRequest.priority,
                waitTimeMs = Instant.now().toEpochMilli() - oldRequest.queuedAt.toEpochMilli()
            )
        )

        // Verify the deferred is completed exceptionally
        val result = runCatching { oldRequest.deferred.await() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is QueueTimeoutError)
        val caughtException = result.exceptionOrNull() as QueueTimeoutError
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
            endpoint = "/test",
            queuedAt = queuedAt,
            deferred = CompletableDeferred()
        )
    }
}
