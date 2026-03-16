package ru.ddd.llmproxy.unit.application.service

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.ChatResponseMetadata
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.output.TokenUsage
import ru.ddd.llmproxy.application.service.ChatApplicationService
import ru.ddd.llmproxy.application.port.ChatProviderPort
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.CacheKey
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.QueuedRequest
import ru.ddd.llmproxy.domain.repository.CacheRepository
import ru.ddd.llmproxy.domain.service.QueueService
import ru.ddd.llmproxy.infrastructure.cache.CacheKeyGenerator
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import ru.ddd.llmproxy.application.service.PriorityResolver

/**
 * Unit test for ChatApplicationService.
 *
 * Tests service logic with mocked dependencies.
 */
class ChatApplicationServiceTest {

    private lateinit var chatProvider: ChatProviderPort<ChatRequest>
    private lateinit var queueService: QueueService<ChatRequest, ChatResponse>
    private lateinit var cacheRepository: CacheRepository<ChatResponse>
    private lateinit var cacheKeyGenerator: CacheKeyGenerator
    private lateinit var priorityResolver: PriorityResolver
    private lateinit var metricsPort: MetricsPort
    private lateinit var properties: LlmProxyProperties
    private lateinit var service: ChatApplicationService

    private lateinit var request: ChatRequest
    private lateinit var expectedResponse: ChatResponse

    @BeforeEach
    fun setup() {
        chatProvider = mockk()
        queueService = mockk()
        cacheRepository = mockk()
        cacheKeyGenerator = ru.ddd.llmproxy.infrastructure.cache.CacheKeyGenerator()
        priorityResolver = mockk()
        metricsPort = mockk()
        properties = mockk()

        service = ChatApplicationService(
            chatProvider = chatProvider,
            queueService = queueService,
            cacheRepository = cacheRepository,
            cacheKeyGenerator = cacheKeyGenerator,
            priorityResolver = priorityResolver,
            metricsPort = metricsPort,
            properties = properties
        )

        request = createMockChatRequest()
        expectedResponse = createMockChatResponse()

        // Setup default mocks
        every { properties.cache.enabled } returns false
        every { priorityResolver.resolve(any(), any()) } returns Priority.P2
        every { chatProvider.baseUrl() } returns "http://test.com"
        coEvery { chatProvider.generate(any()) } returns expectedResponse
        coEvery { queueService.enqueue(any<QueuedRequest<ChatRequest, ChatResponse>>()) } returns expectedResponse
        every { metricsPort.incrementInFlight(any()) } just Runs
        every { metricsPort.decrementInFlight(any()) } just Runs
        every { metricsPort.recordRequest(any(), any(), any()) } just Runs
        every { metricsPort.recordRequest(any(), any(), any(), any()) } just Runs
        every { metricsPort.recordQueueWait(any(), any(), any()) } just Runs
        every { metricsPort.recordProviderLatency(any(), any(), any()) } just Runs
        every { metricsPort.recordCacheHit() } just Runs
        every { metricsPort.recordCacheMiss() } just Runs
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should process request and return response when cache disabled`() = runTest {
        // Arrange - defaults set in setup

        // Act
        val result = service.completions(
            request = request,
            headerPriority = null,
            bodyPriority = null,
            requestId = "test-request-id",
            endpoint = "/v1/chat/completions"
        )

        // Assert
        assertEquals(expectedResponse, result.response)
        assertFalse(result.cacheHit)
        // ChatResult now has flat fields instead of metrics object
        assertNotNull(result.queuedAt)
        assertNull(result.startedAt) // null because cache hit
    }

    @Test
    fun `should return cached response when cache enabled and hit`() = runTest {
        // Arrange
        every { properties.cache.enabled } returns true
        coEvery { cacheRepository.get(any()) } returns expectedResponse
        every { metricsPort.recordCacheHit() } just Runs
        every { metricsPort.recordRequest(any(), any(), "success") } just Runs

        // Act
        val result = service.completions(
            request = request,
            headerPriority = null,
            bodyPriority = null,
            requestId = "test-request-id",
            endpoint = "/v1/chat/completions"
        )

        // Assert
        assertEquals(expectedResponse, result.response)
        assertTrue(result.cacheHit)
        coVerify(atLeast = 1) { cacheRepository.get(any()) }
        coVerify(atLeast = 0) { chatProvider.generate(any()) }
        verify(exactly = 1) { metricsPort.recordCacheHit() }
    }

    @Test
    fun `should record metrics on successful request`() = runTest {
        // Arrange
        every { properties.cache.enabled } returns false
        every { metricsPort.recordRequest(any(), any(), "success") } just Runs
        every { metricsPort.recordQueueWait(any(), any(), any()) } just Runs
        every { metricsPort.recordProviderLatency(any(), any(), any()) } just Runs

        // Act
        service.completions(
            request = request,
            headerPriority = null,
            bodyPriority = null,
            requestId = "test-request-id",
            endpoint = "/v1/chat/completions"
        )

        // Assert - metrics should have been called
        verify(atLeast = 1) { metricsPort.recordRequest(any(), any(), "success") }
        verify(atLeast = 1) { metricsPort.recordQueueWait(any(), any(), any()) }
    }

    // Helper methods

    private fun createMockChatRequest(): ChatRequest {
        return mockk(relaxed = true) {
            every { messages() } returns emptyList()
        }
    }

    private fun createMockChatResponse(): ChatResponse {
        val metadata = ChatResponseMetadata.builder()
            .id("test-id")
            .modelName("GigaChat")
            .tokenUsage(TokenUsage(10, 5, 15))
            .build()

        val aiMessage = AiMessage.builder()
            .text("Test response")
            .build()

        return ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()
    }
}
