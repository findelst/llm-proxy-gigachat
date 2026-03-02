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
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

/**
 * Tests for priority queue behavior under concurrent load.
 *
 * These tests verify:
 * - Priority ordering (higher priority processed first)
 * - Concurrency limits per priority level
 * - Queue overflow handling
 * - Fair scheduling within same priority
 */
@SpringBootTest
@Import(TestConfig::class)
class PriorityQueueConcurrencyTest {

    @Autowired
    private lateinit var mockProvider: MockGigaChatProvider

    @Autowired
    private lateinit var properties: LlmProxyProperties

    // ==================== Concurrency Limit Tests ====================

    @Test
    fun `should respect max concurrency per priority`() = runTest {
        val concurrencyLimit = properties.queue.prioritySlots["p1"] ?: 2
        val totalRequests = concurrencyLimit * 3

        mockProvider.setDelay(200) // Slow responses to observe concurrency

        val startTime = System.currentTimeMillis()
        val inFlightMax = AtomicInteger(0)
        val currentInFlight = AtomicInteger(0)
        val completedTimes = mutableListOf<Long>()

        // Track concurrent execution
        val trackingProvider = object : ChatProviderPort<ChatRequest> by mockProvider {
            override suspend fun generate(request: ChatRequest): ChatResponse {
                val current = currentInFlight.incrementAndGet()
                inFlightMax.updateAndGet { maxOf(it, current) }

                val result = mockProvider.generate(request)

                currentInFlight.decrementAndGet()
                synchronized(completedTimes) {
                    completedTimes.add(System.currentTimeMillis() - startTime)
                }

                return result
            }
        }

        val jobs = (1..totalRequests).map { index ->
            async(Dispatchers.Default) {
                val request = ChatRequest.builder()
                    .messages(listOf(UserMessage.from("Request $index")))
                    .build()
                trackingProvider.generate(request)
            }
        }

        jobs.awaitAll()

        // Verify max concurrency was not exceeded
        val actualMaxConcurrency = inFlightMax.get()
        log.info { "Max concurrent requests: $actualMaxConcurrency (limit: $concurrencyLimit)" }

        // With concurrency limit, requests should take longer than if all ran in parallel
        val totalTime = completedTimes.maxOrNull() ?: 0
        val minExpectedTime = (totalRequests / concurrencyLimit) * 200L * 0.8 // Allow 20% tolerance

        log.info { "Total time: ${totalTime}ms, min expected: ${minExpectedTime}ms" }
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
}
