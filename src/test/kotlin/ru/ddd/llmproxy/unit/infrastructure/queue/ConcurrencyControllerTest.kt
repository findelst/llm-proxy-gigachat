package ru.ddd.llmproxy.unit.infrastructure.queue

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.mockk.mockk
import io.mockk.verify
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.*
import ru.ddd.llmproxy.infrastructure.queue.ConcurrencyController

/**
 * Tests for ConcurrencyController.
 *
 * Verifies:
 * - Slot acquisition with per-priority limits
 * - Slot release and counter updates
 * - Preemption logic for P1 priority
 * - Stats reporting
 */
class ConcurrencyControllerTest {

    private val config = ConcurrencyConfig(
        maxConcurrent = 3,
        p1MaxThreads = 2,
        p3MaxThreads = 1,
        preemptionEnabled = true
    )

    private lateinit var metricsPort: MetricsPort
    private lateinit var controller: ConcurrencyController

    @BeforeEach
    fun setUp() {
        metricsPort = mockk(relaxed = true)
        controller = ConcurrencyController(config, metricsPort)
    }

    // ==================== tryAcquire Tests ====================

    @Test
    fun `should acquire slot when limits allow`() = runTest {
        val job = Job()

        val result = controller.tryAcquire(
            requestId = "test-1",
            priority = Priority.P2,
            job = job,
            queuedAt = java.time.Instant.now()
        )

        assertTrue(result)
        assertEquals(1, controller.getCount(Priority.P2))
    }

    @Test
    fun `should reject P3 when max 1 reached`() = runTest {
        // Given - one P3 already running
        controller.tryAcquire("p3-1", Priority.P3, Job(), java.time.Instant.now())

        // When - try second P3
        val result = controller.tryAcquire(
            requestId = "p3-2",
            priority = Priority.P3,
            job = Job(),
            queuedAt = java.time.Instant.now()
        )

        // Then
        assertFalse(result)
        assertEquals(1, controller.getCount(Priority.P3))
    }

    @Test
    fun `should reject P1 when max 2 reached`() = runTest {
        // Given - two P1 already running
        controller.tryAcquire("p1-1", Priority.P1, Job(), java.time.Instant.now())
        controller.tryAcquire("p1-2", Priority.P1, Job(), java.time.Instant.now())

        // When - try third P1
        val result = controller.tryAcquire(
            requestId = "p1-3",
            priority = Priority.P1,
            job = Job(),
            queuedAt = java.time.Instant.now()
        )

        // Then
        assertFalse(result)
        assertEquals(2, controller.getCount(Priority.P1))
    }

    @Test
    fun `should reject when global limit reached`() = runTest {
        // Given - all slots filled
        controller.tryAcquire("test-1", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("test-2", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("test-3", Priority.P2, Job(), java.time.Instant.now())

        // When - try fourth request
        val result = controller.tryAcquire(
            requestId = "test-4",
            priority = Priority.P2,
            job = Job(),
            queuedAt = java.time.Instant.now()
        )

        // Then
        assertFalse(result)
        assertEquals(3, controller.getCount(Priority.P2))
    }

    // ==================== release Tests ====================

    @Test
    fun `should release slot and decrement counters`() = runTest {
        // Given
        controller.tryAcquire("test-1", Priority.P2, Job(), java.time.Instant.now())

        // When
        controller.release("test-1")

        // Then
        assertEquals(0, controller.getCount(Priority.P2))
    }

    @Test
    fun `should allow new requests after release`() = runTest {
        // Given - all slots filled
        controller.tryAcquire("test-1", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("test-2", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("test-3", Priority.P2, Job(), java.time.Instant.now())

        // When - release one
        controller.release("test-1")

        // Then - can acquire again
        val result = controller.tryAcquire(
            requestId = "test-4",
            priority = Priority.P2,
            job = Job(),
            queuedAt = java.time.Instant.now()
        )
        assertTrue(result)
    }

    // ==================== preemptSlots Tests ====================

    @Test
    fun `should preempt P2 requests for P1`() = runTest {
        // Given - 2 P2 requests running
        controller.tryAcquire("p2-1", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("p2-2", Priority.P2, Job(), java.time.Instant.now())

        // When
        val preempted = controller.preemptSlots(Priority.P1, 2)

        // Then
        assertEquals(2, preempted.size)
        assertTrue(preempted.contains("p2-1"))
        assertTrue(preempted.contains("p2-2"))

        // Verify metrics recorded
        verify { metricsPort.recordPreemption(Priority.P2, Priority.P1) }
    }

    @Test
    fun `should not preempt P1 requests`() = runTest {
        // Given
        controller.tryAcquire("p1-1", Priority.P1, Job(), java.time.Instant.now())
        controller.tryAcquire("p2-1", Priority.P2, Job(), java.time.Instant.now())

        // When - try to preempt 1 slot
        val preempted = controller.preemptSlots(Priority.P1, 1)

        // Then - only P2 preempted
        assertEquals(1, preempted.size)
        assertEquals("p2-1", preempted.first())
    }

    @Test
    fun `should preempt P3 before P2`() = runTest {
        // Given
        controller.tryAcquire("p2-1", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("p3-1", Priority.P3, Job(), java.time.Instant.now())

        // When
        val preempted = controller.preemptSlots(Priority.P1, 1)

        // Then - P3 should be preempted first
        assertEquals(1, preempted.size)
        assertEquals("p3-1", preempted.first())
    }

    @Test
    fun `should return empty list when preemption disabled`() = runTest {
        // Given - controller with preemption disabled
        val noPreemptConfig = ConcurrencyConfig(
            maxConcurrent = 3,
            p1MaxThreads = 2,
            p3MaxThreads = 1,
            preemptionEnabled = false
        )

        val noPreemptController = ConcurrencyController(noPreemptConfig, metricsPort)
        noPreemptController.tryAcquire("p2-1", Priority.P2, Job(), java.time.Instant.now())

        // When
        val preempted = noPreemptController.preemptSlots(Priority.P1, 1)

        // Then
        assertTrue(preempted.isEmpty())
    }

    // ==================== stats Tests ====================

    @Test
    fun `should return correct concurrency stats`() = runTest {
        // Given
        controller.tryAcquire("p1-1", Priority.P1, Job(), java.time.Instant.now())
        controller.tryAcquire("p2-1", Priority.P2, Job(), java.time.Instant.now())
        controller.tryAcquire("p3-1", Priority.P3, Job(), java.time.Instant.now())

        // When
        val stats = controller.stats

        // Then
        assertEquals(3, stats.totalRunning)
        assertEquals(1, stats.p1Running)
        assertEquals(1, stats.p2Running)
        assertEquals(1, stats.p3Running)
        assertEquals(0, stats.availableSlots)
        assertTrue(stats.p1Available) // 1 < 2, so available
        assertFalse(stats.p3Available) // 1 = 1, so not available
    }

    @Test
    fun `should show P1 available when under max`() = runTest {
        // Given - only 1 P1
        controller.tryAcquire("p1-1", Priority.P1, Job(), java.time.Instant.now())

        // When
        val stats = controller.stats

        // Then - P1 available (1 < 2)
        assertTrue(stats.p1Available)
    }
}
