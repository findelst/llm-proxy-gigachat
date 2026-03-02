package ru.ddd.llmproxy.presentation.controller

import dev.langchain4j.model.chat.response.ChatResponse
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ddd.llmproxy.application.service.ChatApplicationService
import ru.ddd.llmproxy.domain.model.InvalidRequestError
import ru.ddd.llmproxy.presentation.dto.ChatCompletionRequest
import ru.ddd.llmproxy.presentation.dto.ChatCompletionResponse
import ru.ddd.llmproxy.presentation.dto.InvokeResponse
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Controller for chat completion endpoints.
 *
 * Endpoints:
 * - POST /v1/chat/completions - Standard chat completion (OpenAI-compatible)
 * - POST /v1/chat/invoke - Chat completion with metrics in response body
 */
@RestController
@RequestMapping("/v1/chat")
class ChatController(
    private val chatService: ChatApplicationService
) {

    /**
     * Chat completion endpoint.
     *
     * Returns response with metrics in headers.
     */
    @PostMapping("/completions")
    suspend fun completions(
        @RequestBody request: ChatCompletionRequest,
        @RequestHeader("X-Priority", required = false) headerPriority: String?,
        @RequestHeader("x-request-id", required = false) explicitRequestId: String?
    ): ResponseEntity<ChatCompletionResponse> {
        val requestId = explicitRequestId ?: UUID.randomUUID().toString()

        log.info { "Chat completion request: $requestId, priority=$headerPriority" }

        // Validate request
        validateRequest(request)

        // Convert to langchain4j ChatRequest
        val chatRequest = request.toChatRequest()

        val result = chatService.completions(
            request = chatRequest,
            headerPriority = headerPriority,
            bodyPriority = request.priority,
            requestId = requestId,
            endpoint = "/v1/chat/completions"
        )

        val response = ChatCompletionResponse.from(result.response)

        return ResponseEntity.ok()
            .header("x-llm-proxy-request-id", requestId)
            .header("x-llm-proxy-priority", result.metrics.priority.value)
            .apply {
                result.metrics.queueWaitMs?.let {
                    header("x-llm-proxy-queue-wait-ms", it.toString())
                }
                result.metrics.providerLatencyMs?.let {
                    header("x-llm-proxy-provider-latency-ms", it.toString())
                }
            }
            .header("x-request-id", requestId)
            .body(response)
    }

    /**
     * Chat invoke endpoint with metrics in response body.
     *
     * Same as completions but includes metrics in the response JSON.
     */
    @PostMapping("/invoke")
    suspend fun invoke(
        @RequestBody request: ChatCompletionRequest,
        @RequestHeader("X-Priority", required = false) headerPriority: String?,
        @RequestHeader("x-request-id", required = false) explicitRequestId: String?
    ): ResponseEntity<InvokeResponse> {
        val requestId = explicitRequestId ?: UUID.randomUUID().toString()

        log.info { "Chat invoke request: $requestId, priority=$headerPriority" }

        // Validate request
        validateRequest(request)

        // Convert to langchain4j ChatRequest
        val chatRequest = request.toChatRequest()

        val result = chatService.completions(
            request = chatRequest,
            headerPriority = headerPriority,
            bodyPriority = request.priority,
            requestId = requestId,
            endpoint = "/v1/chat/invoke"
        )

        val metrics = InvokeResponse.Metrics(
            queueWaitMs = result.metrics.queueWaitMs,
            providerLatencyMs = result.metrics.providerLatencyMs,
            priority = result.metrics.priority.value,
            endpoint = result.metrics.endpoint
        )

        val invokeResponse = InvokeResponse.from(result.response, requestId, metrics)

        return ResponseEntity.ok()
            .header("x-llm-proxy-request-id", requestId)
            .header("x-llm-proxy-priority", result.metrics.priority.value)
            .header("x-request-id", requestId)
            .body(invokeResponse)
    }

    /**
     * Validates the chat request.
     */
    private fun validateRequest(request: ChatCompletionRequest) {
        if (request.messages.isEmpty()) {
            throw InvalidRequestError("messages field is required and must not be empty")
        }

        if (request.messages.any { it.content.isNullOrBlank() }) {
            throw InvalidRequestError("Message content must not be null or empty")
        }

        if (request.stream) {
            throw InvalidRequestError("Streaming is not supported")
        }
    }
}
