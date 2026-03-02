package ru.ddd.llmproxy.unit.presentation.controller

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
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.hamcrest.Matchers as Matchers
import ru.ddd.llmproxy.integration.TestConfig
import chat.giga.model.completion.CompletionResponse as ChatCompletionResponse

/**
 * Unit test for ChatController.
 *
 * Tests REST endpoints using Spring Boot test context with mock provider.
 * Uses async dispatch for suspend function endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig::class)
class ChatControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `completions endpoint should return success response`() {
        // Arrange
        val request = objectMapper.writeValueAsString(createGigaChatRequest("Hello"))

        // Act & Assert - async dispatch for suspend function
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().exists("x-llm-proxy-request-id"))
            .andExpect(header().exists("x-llm-proxy-priority"))
            .andExpect(jsonPath("$.model").exists())
            .andExpect(jsonPath("$.choices").isArray())
            .andExpect(jsonPath("$.choices[0].message.content").value(org.hamcrest.Matchers.notNullValue()))
    }

    @Test
    fun `completions endpoint should include metrics headers`() {
        // Arrange
        val request = objectMapper.writeValueAsString(createGigaChatRequest("Test"))

        // Act & Assert - async dispatch for suspend function
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().exists("x-llm-proxy-priority"))
            .andExpect(header().exists("x-llm-proxy-queue-wait-ms"))
            .andExpect(header().exists("x-llm-proxy-provider-latency-ms"))
    }

    @Test
    fun `completions endpoint should use provided request id`() {
        // Arrange
        val requestId = "my-custom-request-id"
        val requestJson = objectMapper.writeValueAsString(createGigaChatRequest("Hello"))

        // Act & Assert - async dispatch for suspend function
        val mvcResult = mockMvc.perform(
            post("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("x-request-id", requestId)
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(header().string("x-request-id", requestId))
    }

    @Test
    fun `completions endpoint should validate empty messages`() {
        // Arrange - use raw JSON to test validation
        val invalidJson = """{"messages":[]}"""

        // Act & Assert - async dispatch for suspend function
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
    fun `completions endpoint should validate null message content`() {
        // Arrange - use raw JSON to test validation
        val invalidJson = """{"messages":[{"role":"user"}]}"""

        // Act & Assert - async dispatch for suspend function
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
    fun `completions endpoint should validate empty message content`() {
        // Arrange - use raw JSON to test validation
        val invalidJson = """{"messages":[{"role":"user","content":""}]}"""

        // Act & Assert - async dispatch for suspend function
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
    fun `invoke endpoint should return InvokeResponse`() {
        // Arrange
        val requestJson = objectMapper.writeValueAsString(createGigaChatRequest("Test"))

        // Act & Assert - async dispatch for suspend function
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
            .andExpect(jsonPath("$.metrics.endpoint").value("/v1/chat/invoke"))
            .andExpect(jsonPath("$.choices").exists())
    }

    @Test
    fun `invoke endpoint should include metrics in response body`() {
        // Arrange
        val requestJson = objectMapper.writeValueAsString(createGigaChatRequest("Test"))

        // Act & Assert - async dispatch for suspend function
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
            .andExpect(jsonPath("$.metrics.provider_latency_ms").isNumber())
    }

    @Test
    fun `invoke endpoint should return error for invalid request`() {
        // Arrange - use raw JSON to test validation
        val invalidJson = """{"messages":[]}"""

        // Act & Assert - async dispatch for suspend function
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

    // Helper methods

    private fun createGigaChatRequest(content: String): GigaChatCompletionRequest {
        return GigaChatCompletionRequest.builder()
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content(content)
                        .build()
                )
            )
            .build()
    }
}
