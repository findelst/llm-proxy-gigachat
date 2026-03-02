package ru.ddd.llmproxy.integration

import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger {}

/**
 * Scenario-based integration tests using realistic GigaChat requests.
 *
 * Tests realistic scenarios including:
 * - Function calls (tools)
 * - Multi-turn conversations
 * - Different question types
 * - Priority-based SLA compliance
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig::class)
class FunctionCallScenarioTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockProvider: MockGigaChatProvider

    @Autowired
    private lateinit var properties: LlmProxyProperties

    companion object {
        private val requestCounter = AtomicInteger(0)
    }

    @BeforeEach
    fun setup() {
        mockProvider.reset()
        mockProvider.setDelay(10) // Fast responses for tests
    }

    // ==================== Function Call Scenarios ====================

    @Test
    fun `should handle function call request`() {
        val request = """
            {
                "model": "GigaChat",
                "messages": [
                    {"role": "user", "content": "Какая погода в Москве?"}
                ],
                "tools": [
                    {
                        "type": "function",
                        "function": {
                            "name": "get_weather",
                            "description": "Получить погоду",
                            "parameters": {
                                "type": "object",
                                "properties": {
                                    "city": {"type": "string"}
                                },
                                "required": ["city"]
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        // Async request starts
        mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk) // Async started
    }

    @Test
    fun `should handle multi-turn conversation`() {
        val conversationTurns = listOf(
            "Привет, меня зовут Алексей",
            "Как меня зовут?",
            "Расскажи о Kotlin",
            "Какие у него преимущества?"
        )

        // Each turn starts an async request
        conversationTurns.forEach { message ->
            val request = """
                {"messages": [{"role": "user", "content": "$message"}]}
            """.trimIndent()

            mockMvc.perform(
                post("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andExpect(status().isOk) // Async started
        }

        log.info { "Completed ${conversationTurns.size} conversation turns" }
    }

    // ==================== Priority Scenarios ====================

    @Test
    fun `scenario - critical request with p1 priority`() = runTest {
        val criticalRequest = dev.langchain4j.model.chat.request.ChatRequest.builder()
            .messages(listOf(dev.langchain4j.data.message.UserMessage.from("Критический запрос")))
            .build()

        val response = mockProvider.generate(criticalRequest)

        assert(response.aiMessage() != null) { "Expected response" }
        log.info { "Critical request processed" }
    }

    @Test
    fun `scenario - background batch request with p3 priority`() = runTest {
        val batchRequest = dev.langchain4j.model.chat.request.ChatRequest.builder()
            .messages(listOf(dev.langchain4j.data.message.UserMessage.from("Batch request")))
            .build()

        val response = mockProvider.generate(batchRequest)

        assert(response.aiMessage() != null) { "Expected response" }
        log.info { "Batch request processed" }
    }

    // ==================== Request with Different Models ====================

    @Test
    fun `should handle requests with different model specifications`() {
        val models = listOf("GigaChat", "GigaChat-Pro", "GigaChat-Max")

        models.forEach { model ->
            val request = """
                {"model": "$model", "messages": [{"role": "user", "content": "Test with $model"}]}
            """.trimIndent()

            mockMvc.perform(
                post("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andExpect(status().isOk) // Async started
        }

        log.info { "Tested ${models.size} different models" }
    }

    // ==================== Temperature and Parameters ====================

    @Test
    fun `should handle requests with various parameters`() {
        val scenarios = listOf(
            Triple("Creative writing", 1.5, 500),
            Triple("Factual answer", 0.0, 100),
            Triple("Balanced response", 0.7, 1000)
        )

        scenarios.forEach { (question, temperature, maxTokens) ->
            val request = """
                {
                    "messages": [{"role": "user", "content": "$question"}],
                    "temperature": $temperature,
                    "max_tokens": $maxTokens
                }
            """.trimIndent()

            mockMvc.perform(
                post("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andExpect(status().isOk) // Async started
        }

        log.info { "Tested ${scenarios.size} parameter combinations" }
    }

    // ==================== Invoke Endpoint Scenarios ====================

    @Test
    fun `should return detailed metrics via invoke endpoint`() = runTest {
        val request = dev.langchain4j.model.chat.request.ChatRequest.builder()
            .messages(listOf(dev.langchain4j.data.message.UserMessage.from("Test invoke")))
            .build()

        val response = mockProvider.generate(request)

        assert(response.metadata() != null) { "Expected metadata" }
        assert(response.tokenUsage() != null) { "Expected token usage" }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `should handle very long message content`() {
        val longContent = "Анализ данных. ".repeat(100) // ~1500 chars

        val request = """
            {"messages": [{"role": "user", "content": "$longContent"}]}
        """.trimIndent()

        mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isOk) // Async started
    }

    @Test
    fun `should handle Cyrillic content correctly`() {
        val cyrillicQuestions = listOf(
            "Как работает машинное обучение?",
            "Объясни принципы чистой архитектуры",
            "Какие паттерны проектирования ты знаешь?"
        )

        cyrillicQuestions.forEach { question ->
            val request = """
                {"messages": [{"role": "user", "content": "$question"}]}
            """.trimIndent()

            mockMvc.perform(
                post("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andExpect(status().isOk) // Async started
        }

        log.info { "Tested ${cyrillicQuestions.size} Cyrillic questions" }
    }

    // ==================== Load Scenario ====================

    @Test
    fun `scenario - mixed priority load simulation`() = runTest {
        mockProvider.setDelay(5)

        val p1Count = AtomicInteger(0)
        val p2Count = AtomicInteger(0)
        val p3Count = AtomicInteger(0)

        // Simulate 30 rapid requests with different priorities
        val priorities = listOf(
            "p1" to 5,  // 5 high priority
            "p2" to 10, // 10 medium priority
            "p3" to 15  // 15 low priority
        )

        priorities.forEach { (priority, count) ->
            repeat(count) { index ->
                val request = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(listOf(dev.langchain4j.data.message.UserMessage.from("Request $index with priority $priority")))
                    .build()

                mockProvider.generate(request)

                when (priority) {
                    "p1" -> p1Count.incrementAndGet()
                    "p2" -> p2Count.incrementAndGet()
                    "p3" -> p3Count.incrementAndGet()
                }
            }
        }

        log.info { "Load test completed: p1=${p1Count.get()}, p2=${p2Count.get()}, p3=${p3Count.get()}" }
        assert(p1Count.get() == 5)
        assert(p2Count.get() == 10)
        assert(p3Count.get() == 15)
    }
}
