package ru.ddd.llmproxy.integration

import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.ChatResponseMetadata
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.output.TokenUsage
import mu.KotlinLogging
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import ru.ddd.llmproxy.application.port.ChatProviderPort
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

/**
 * Test configuration with mock GigaChat provider.
 */
@TestConfiguration
class TestConfig {

    /**
     * Mock GigaChat provider that simulates responses with configurable delay.
     */
    @Bean
    @Primary
    fun mockGigaChatProvider(): MockGigaChatProvider = MockGigaChatProvider()
}

/**
 * Mock implementation of ChatProviderPort for testing.
 *
 * Features:
 * - Configurable response delay
 * - Tracks all requests for verification
 * - Simulates different response scenarios
 */
class MockGigaChatProvider(
    private var delayMs: Long = 100
) : ChatProviderPort<ChatRequest> {

    private val requestCounter = AtomicInteger(0)
    private val requestsByPriority = mutableMapOf<String, MutableList<RequestRecord>>()
    private val completedRequests = mutableListOf<RequestRecord>()

    data class RequestRecord(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
        val priority: String? = null,
        val question: String? = null
    )

    override suspend fun generate(request: ChatRequest): ChatResponse {
        val record = RequestRecord()
        requestCounter.incrementAndGet()

        log.debug { "MockGigaChat processing request ${record.id}" }

        // Simulate processing delay
        kotlinx.coroutines.delay(delayMs)

        // Extract question from messages
        val lastMessage = request.messages().lastOrNull()
        val question = lastMessage?.toString()?.take(100)

        // Build mock response
        val response = ChatResponse.builder()
            .metadata(
                ChatResponseMetadata.builder()
                    .id("chatcmpl-${record.id.take(8)}")
                    .modelName("GigaChat-Mock")
                    .tokenUsage(TokenUsage(100, 50, 150))
                    .build()
            )
            .aiMessage(
                AiMessage.builder()
                    .text("Mock response for: $question")
                    .build()
            )
            .build()

        synchronized(completedRequests) {
            completedRequests.add(record.copy(completedAt = System.currentTimeMillis()))
        }

        log.debug { "MockGigaChat completed request ${record.id}" }

        return response
    }

    override fun providerName(): String = "MockGigaChat"

    override fun baseUrl(): String = "http://mock-gigachat.local"

    override suspend fun isHealthy(): Boolean = true

    /**
     * Sets the simulated delay for responses.
     */
    fun setDelay(ms: Long) {
        delayMs = ms
    }

    /**
     * Returns total number of requests processed.
     */
    fun getRequestCount(): Int = requestCounter.get()

    /**
     * Returns all completed requests.
     */
    fun getCompletedRequests(): List<RequestRecord> = synchronized(completedRequests) {
        completedRequests.toList()
    }

    /**
     * Records a request with priority for tracking.
     */
    fun recordRequestWithPriority(priority: String, question: String? = null) {
        synchronized(requestsByPriority) {
            requestsByPriority.getOrPut(priority) { mutableListOf() }
                .add(RequestRecord(priority = priority, question = question))
        }
    }

    /**
     * Returns requests count by priority.
     */
    fun getRequestsByPriority(priority: String): Int = synchronized(requestsByPriority) {
        requestsByPriority[priority]?.size ?: 0
    }

    /**
     * Resets all counters and records.
     */
    fun reset() {
        requestCounter.set(0)
        requestsByPriority.clear()
        completedRequests.clear()
    }
}
