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
 * Integration test for /v1/chat/invoke endpoint.
 *
 * Tests the complete flow from HTTP request to InvokeResponse with mock GigaChat provider.
 * Uses async dispatch for suspend function endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig::class)
class ChatInvokeEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should return InvokeResponse with all fields`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test request")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.request_id").exists())
            .andExpect(jsonPath("$.metrics").exists())
            .andExpect(jsonPath("$.metrics.queue_wait_ms").exists())
            .andExpect(jsonPath("$.metrics.provider_latency_ms").exists())
            .andExpect(jsonPath("$.metrics.priority").exists())
            .andExpect(jsonPath("$.metrics.endpoint").exists())
            .andExpect(jsonPath("$.choices").exists())
            .andExpect(jsonPath("$.model").exists())
    }

    @Test
    fun `should include queue wait metric in response`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metrics.queue_wait_ms").isNumber())
    }

    @Test
    fun `should include provider latency metric in response`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metrics.provider_latency_ms").isNumber())
    }

    @Test
    fun `should include priority in metrics`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metrics.priority").exists())
    }

    @Test
    fun `should include endpoint in metrics`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.metrics.endpoint").value("/v1/chat/invoke"))
    }

    @Test
    fun `should include usage information when available`() = runTest {
        // Arrange
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usage").exists())
            .andExpect(jsonPath("$.usage.prompt_tokens").exists())
            .andExpect(jsonPath("$.usage.completion_tokens").exists())
            .andExpect(jsonPath("$.usage.total_tokens").exists())
    }

    @Test
    fun `should handle streaming flag in request`() = runTest {
        // Arrange - Note: streaming is handled by GigaChat native library
        val request = GigaChatCompletionRequest.builder()
            .messages(listOf(createUserMessage("Test")))
            .stream(false)
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            // Response should be complete (non-streaming) even with stream=false
            .andExpect(jsonPath("$.choices[0].message.content").exists())
    }

    @Test
    fun `should include request ID in response`() = runTest {
        // Arrange
        val customRequestId = "custom-invoke-id"
        val requestJson = createCompletionRequest("Test")

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("x-request-id", customRequestId)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.request_id").value(customRequestId))
    }

    @Test
    fun `should return 400 for invalid request`() = runTest {
        // Arrange
        val invalidJson = """{"messages":[]}"""

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should handle multiple messages conversation`() = runTest {
        // Arrange
        val request = GigaChatCompletionRequest.builder()
            .messages(
                listOf(
                    createUserMessage("Hello"),
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.ASSISTANT)
                        .content("Hi!")
                        .build(),
                    createUserMessage("Goodbye")
                )
            )
            .build()
        val requestJson = objectMapper.writeValueAsString(request)

        // Act & Assert
        val mvcResult = mockMvc.perform(
            post("/v1/chat/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.choices[0].message.content").exists())
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
