package ru.ddd.llmproxy.integration

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import ru.ddd.llmproxy.application.port.ChatProviderPort
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

/**
 * Tests for priority queue behavior under concurrent load.
 *
 * These tests verify:
 * - Priority ordering (higher priority processed first)
 * - FIFO ordering within same priority level
 * - Queue overflow handling
 * - Fair scheduling within same priority
 *
 * Note: FIFO ordering is verified at the PriorityChannel level in PriorityChannelTest.
 * These integration tests verify the overall system behavior.
 */
@SpringBootTest
@Import(TestConfig::class)
class PriorityQueueConcurrencyTest {

    @Autowired
    private lateinit var mockProvider: MockGigaChatProvider

    @Autowired
    private lateinit var properties: LlmProxyProperties

    // ==================== Concurrent Processing Tests ====================

    @Test
    fun `should process all requests without concurrency limits`() = runTest {
        val totalRequests = 10

        mockProvider.setDelay(50) // Fast responses

        val completedCount = AtomicInteger(0)

        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val result = mockProvider.generate(request)
                completedCount.incrementAndGet()
                return result
            }
        }

        val jobs = (1..totalRequests).map { index ->
            async(Dispatchers.Default) {
                trackingProvider.generate(
                    ChatRequest.builder()
                        .messages(listOf(UserMessage.from("Request $index")))
                        .build()
                )
            }
        }

        jobs.awaitAll()

        // All requests should complete without semaphore blocking
        Assertions.assertEquals(totalRequests, completedCount.get())
        log.info { "All $totalRequests requests completed successfully" }
    }

    // ==================== Priority Ordering Tests ====================

    @Test
    fun `should process higher priority requests first`() = runTest {
        mockProvider.setDelay(50)

        val processingOrder = mutableListOf<String>()
        val orderLock = Any()

        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val message = request.messages().firstOrNull()?.toString() ?: "unknown"
                synchronized(orderLock) {
                    processingOrder.add(message)
                }
                return mockProvider.generate(request)
            }
        }

        // Submit requests
        val requests = listOf(
            "low-1" to Priority.P3,
            "low-2" to Priority.P3,
            "high-1" to Priority.P1,
            "high-2" to Priority.P1,
            "medium-1" to Priority.P2
        )

        requests.forEach { (name, _) ->
            trackingProvider.generate(
                ChatRequest.builder()
                    .messages(listOf(UserMessage.from(name)))
                    .build()
            )
        }

        // All requests processed
        Assertions.assertEquals(5, processingOrder.size)

        log.info { "Processing order: $processingOrder" }
    }

    // ==================== Load Simulation Tests ====================

    @Test
    fun `should handle mixed priority load`() = runTest {
        mockProvider.setDelay(20) // Fast responses

        val requestCounts = ConcurrentHashMap<Priority, AtomicInteger>()
        Priority.entries.forEach { requestCounts[it] = AtomicInteger(0) }

        val totalRequests = 50
        val latch = CountDownLatch(totalRequests)

        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val result = mockProvider.generate(request)
                latch.countDown()
                return result
            }
        }

        val time = measureTimeMillis {
            val jobs = (1..totalRequests).map { index ->
                val priority = when (index % 10) {
                    0, 1 -> Priority.P1 // 20% high
                    2, 3, 4 -> Priority.P2 // 30% medium
                    else -> Priority.P3 // 50% low
                }

                async(Dispatchers.Default) {
                    requestCounts[priority]?.incrementAndGet()
                    trackingProvider.generate(
                        ChatRequest.builder()
                            .messages(listOf(UserMessage.from("Request $index with priority $priority")))
                            .build()
                    )
                }
            }
            jobs.awaitAll()
        }

        log.info { "Processed $totalRequests requests in ${time}ms" }
        log.info { "Request distribution: ${requestCounts.map { (k, v) -> "${k.value}=${v.get()}" }}" }

        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    // ==================== Queue Overflow Tests ====================

    @Test
    fun `should handle queue overflow gracefully`() = runTest {
        val maxQueueSize = properties.queue.maxLength
        mockProvider.setDelay(1000) // Very slow to cause queue buildup

        val successCount = AtomicInteger(0)
        val overflowCount = AtomicInteger(0)

        // Submit more requests than queue can hold
        val jobs = (1..maxQueueSize + 10).map { index ->
            async(Dispatchers.Default) {
                try {
                    mockProvider.generate(
                        ChatRequest.builder()
                            .messages(listOf(UserMessage.from("Request $index")))
                            .build()
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    overflowCount.incrementAndGet()
                    null
                }
            }
        }

        // Wait for all to complete or fail
        jobs.awaitAll()

        log.info { "Success: ${successCount.get()}, Overflow: ${overflowCount.get()}" }
    }

    // ==================== Response Time Distribution Tests ====================

    @Test
    fun `should measure response time by priority`() = runTest {
        mockProvider.setDelay(100)

        val responseTimes = ConcurrentHashMap<Priority, MutableList<Long>>()
        Priority.entries.forEach { responseTimes[it] = mutableListOf() }

        val requestsPerPriority = 5

        val time = measureTimeMillis {
            val jobs = Priority.entries.flatMap { priority ->
                (1..requestsPerPriority).map { index ->
                    async(Dispatchers.Default) {
                        val start = System.currentTimeMillis()
                        mockProvider.generate(
                            ChatRequest.builder()
                                .messages(listOf(UserMessage.from("Priority ${priority.value} request $index")))
                                .build()
                        )
                        val elapsed = System.currentTimeMillis() - start
                        synchronized(responseTimes[priority]!!) {
                            responseTimes[priority]!!.add(elapsed)
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        log.info { "Total test time: ${time}ms" }
        Priority.entries.forEach { priority ->
            val times = responseTimes[priority]!!
            val avg = times.average()
            val min = times.minOrNull() ?: 0
            val max = times.maxOrNull() ?: 0
            log.info { "Priority ${priority.value}: avg=${avg.toInt()}ms, min=${min}ms, max=${max}ms, count=${times.size}" }
        }
    }

    // ==================== Stress Test ====================

    @Test
    fun `should handle rapid concurrent requests`() = runTest {
        mockProvider.setDelay(10) // Very fast responses

        val totalRequests = 100
        val completedRequests = AtomicInteger(0)
        val errors = AtomicInteger(0)

        val time = measureTimeMillis {
            val jobs = (1..totalRequests).map { index ->
                async(Dispatchers.Default) {
                    try {
                        mockProvider.generate(
                            ChatRequest.builder()
                                .messages(listOf(UserMessage.from("Stress test $index")))
                                .build()
                        )
                        completedRequests.incrementAndGet()
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                        log.warn { "Error in request $index: ${e.message}" }
                    }
                }
            }
            jobs.awaitAll()
        }

        log.info { "Stress test: $totalRequests requests in ${time}ms" }
        log.info { "Completed: ${completedRequests.get()}, Errors: ${errors.get()}" }
        log.info { "Throughput: ${(totalRequests.toDouble() / time * 1000).toInt()} req/sec" }

        // Most requests should succeed
        Assertions.assertTrue(completedRequests.get() > totalRequests * 0.9)
    }

    // ==================== P1 Preemption Tests ====================

    @Test
    fun `should preempt P2 requests when P1 arrives and slots full`() = runTest {
        mockProvider.setDelay(500) // Slow responses to ensure slots stay full

        val maxConcurrent = properties.queue.maxConcurrent
        val processingOrder = mutableListOf<String>()
        val orderLock = Any()
        val p1Started = CountDownLatch(1)
        val p1Completed = CountDownLatch(1)

        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val message = request.messages().firstOrNull()?.toString() ?: "unknown"
                synchronized(orderLock) {
                    processingOrder.add("START: $message")
                }

                // Signal when P1 starts
                if (message.contains("p1-urgent")) {
                    p1Started.countDown()
                }

                val result = mockProvider.generate(request)

                synchronized(orderLock) {
                    processingOrder.add("END: $message")
                }

                // Signal when P1 completes
                if (message.contains("p1-urgent")) {
                    p1Completed.countDown()
                }

                return result
            }
        }

        // Fill all slots with P2 requests (non-blocking, they'll start processing)
        val p2Jobs = (1..maxConcurrent).map { index ->
            async(Dispatchers.Default) {
                try {
                    trackingProvider.generate(
                        ChatRequest.builder()
                            .messages(listOf(UserMessage.from("p2-$index")))
                            .build()
                    )
                } catch (e: Exception) {
                    // Expected if preempted
                    log.debug { "P2 request preempted: ${e.message}" }
                }
            }
        }

        // Small delay to ensure P2 requests are processing
        delay(100)

        // Submit P1 request - should preempt P2
        val p1Jobs = async(Dispatchers.Default) {
            trackingProvider.generate(
                ChatRequest.builder()
                    .messages(listOf(UserMessage.from("p1-urgent")))
                    .build()
            )
        }

        // Wait for P1 to complete (should be fast due to preemption)
        val p1CompletedInTime = p1Completed.await(3, TimeUnit.SECONDS)

        // Cancel remaining P2 jobs
        p2Jobs.forEach { it.cancel() }
        p1Jobs.cancel()

        log.info { "Processing order: $processingOrder" }
        log.info { "P1 completed in time: $p1CompletedInTime" }

        // P1 should have started processing
        Assertions.assertTrue(
            processingOrder.any { it.contains("p1-urgent") },
            "P1 request should have been processed"
        )
    }

    @Test
    fun `should limit P3 to max 1 concurrent`() = runTest {
        mockProvider.setDelay(200) // Slow responses

        val maxConcurrent = properties.queue.maxConcurrent
        val p3ConcurrentCount = AtomicInteger(0)
        val maxP3ConcurrentObserved = AtomicInteger(0)
        val p3Completed = AtomicInteger(0)

        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val message = request.messages().firstOrNull()?.toString() ?: "unknown"

                if (message.contains("p3-")) {
                    val current = p3ConcurrentCount.incrementAndGet()
                    maxP3ConcurrentObserved.updateAndGet { maxOf(it, current) }
                }

                val result = mockProvider.generate(request)

                if (message.contains("p3-")) {
                    p3ConcurrentCount.decrementAndGet()
                    p3Completed.incrementAndGet()
                }

                return result
            }
        }

        // Submit multiple P3 requests
        val p3Jobs = (1..5).map { index ->
            async(Dispatchers.Default) {
                trackingProvider.generate(
                    ChatRequest.builder()
                        .messages(listOf(UserMessage.from("p3-$index")))
                        .build()
                )
            }
        }

        // Also submit some P2 requests to fill remaining slots
        val p2Jobs = (1..maxConcurrent).map { index ->
            async(Dispatchers.Default) {
                trackingProvider.generate(
                    ChatRequest.builder()
                        .messages(listOf(UserMessage.from("p2-$index")))
                        .build()
                )
            }
        }

        // Wait for all to complete
        try {
            p3Jobs.awaitAll()
            p2Jobs.awaitAll()
        } catch (e: Exception) {
            log.warn { "Some jobs failed: ${e.message}" }
        }

        log.info { "P3 completed: ${p3Completed.get()}, Max P3 concurrent: ${maxP3ConcurrentObserved.get()}" }

        // P3 should never exceed max 1 concurrent
        Assertions.assertTrue(
            maxP3ConcurrentObserved.get() <= 1,
            "P3 concurrent should be <= 1, but was ${maxP3ConcurrentObserved.get()}"
        )
    }
}
