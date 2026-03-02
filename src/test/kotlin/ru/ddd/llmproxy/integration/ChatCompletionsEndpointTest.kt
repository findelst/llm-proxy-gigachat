package ru.ddd.llmproxy.integration

import chat.giga.model.completion.CompletionRequest as GigaChatCompletionRequest
import chat.giga.model.completion.ChatMessage as GigaChatMessage
import chat.giga.model.completion.ChatMessageRole
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.hamcrest.Matchers as Matchers

/**
 * Integration test for /v1/chat/completions endpoint.
 *
 * Tests the complete flow from HTTP request to response with mock GigaChat provider.
 * Uses async dispatch for suspend function endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig::class)
class ChatCompletionsEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should return valid response for GigaChat native request`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Hello, GigaChat!")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.object").value("chat.completion"))
            .andExpect(jsonPath("$.model").exists())
            .andExpect(jsonPath("$.created").exists())
            .andExpect(jsonPath("$.choices").isArray())
            .andExpect(jsonPath("$.choices[0].index").value(0))
            .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
            .andExpect(jsonPath("$.choices[0].message.content").value(Matchers.containsString("Mock response")))
    }

    @Test
    fun `should handle request with temperature parameter`() = runTest {
        // Arrange
        val request = GigaChatCompletionRequest.builder()
            .messages(listOf(createUserMessage("Test")))
            .temperature(0.7f)
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
    }

    @Test
    fun `should handle request with maxTokens parameter`() = runTest {
        // Arrange
        val request = GigaChatCompletionRequest.builder()
            .messages(listOf(createUserMessage("Test")))
            .maxTokens(500)
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
    }

    @Test
    fun `should handle request with topP parameter`() = runTest {
        // Arrange
        val request = GigaChatCompletionRequest.builder()
            .messages(listOf(createUserMessage("Test")))
            .topP(0.9f)
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
    }

    @Test
    fun `should return 400 for empty messages`() = runTest {
        // Arrange
        val invalidJson = """{"messages":[]}"""

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 for null message content`() = runTest {
        // Arrange
        val invalidJson = """{"messages":[{"role":"user"}]}"""

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 for empty message content`() = runTest {
        // Arrange
        val invalidJson = """{"messages":[{"role":"user","content":""}]}"""

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should handle multiple messages in conversation`() = runTest {
        // Arrange
        val request = GigaChatCompletionRequest.builder()
            .messages(
                listOf(
                    createUserMessage("Hello"),
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.ASSISTANT)
                        .content("Hi there!")
                        .build(),
                    createUserMessage("How are you?")
                )
            )
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.choices[0].message.content").exists())
    }

    @Test
    fun `should include request ID header in response`() = runTest {
        // Arrange
        val customRequestId = "my-custom-id-123"
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("x-request-id", customRequestId)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string("x-request-id", customRequestId))
    }

    @Test
    fun `should include priority header in response`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().exists("x-llm-proxy-priority"))
    }

    @Test
    fun `should include metrics headers in response`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().exists("x-llm-proxy-queue-wait-ms"))
            .andExpect(header().exists("x-llm-proxy-provider-latency-ms"))
    }

    @Test
    fun `should return 400 for invalid JSON`() = runTest {
        // Arrange
        val invalidJson = """{"invalid":"json"}"""

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
    }

    // Helper methods

    private fun createCompletionRequest(content: String): String {
        val request = GigaChatCompletionRequest.builder()
            .messages(listOf(createUserMessage(content)))
            .build()
        return objectMapper.writeValueAsString(request)
    }

    private fun createUserMessage(content: String): GigaChatMessage {
        return GigaChatMessage.builder()
            .role(ChatMessageRole.USER)
            .content(content)
            .build()
    }
}
