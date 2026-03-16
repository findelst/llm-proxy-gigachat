package ru.ddd.llmproxy.presentation.controller

import chat.giga.model.completion.CompletionRequest as GigaChatCompletionRequest
import chat.giga.model.completion.CompletionResponse as GigaChatCompletionResponse
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ddd.llmproxy.application.adapter.GigaChatRequestAdapter
import ru.ddd.llmproxy.application.adapter.GigaChatResponseAdapter
import ru.ddd.llmproxy.application.service.ChatApplicationService
import ru.ddd.llmproxy.domain.model.InvalidRequestError
import ru.ddd.llmproxy.domain.model.Priority
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Controller for chat completion endpoints.
 *
 * Endpoints:
 * - POST /v1/chat/completions - Standard chat completion (GigaChat-compatible)
 * - POST /v1/chat/invoke - Chat completion with metrics in response body
 */
@RestController
@RequestMapping("/v1/chat")
class ChatController(
    private val chatService: ChatApplicationService
) {

    /**
     * Chat completion endpoint using GigaChat native request/response format.
     *
     * Returns response with metrics in headers.
     */
    @PostMapping("/completions")
    suspend fun completions(
        @RequestBody request: GigaChatCompletionRequest,
        @RequestHeader("X-Priority", required = false) headerPriority: String?,
        @RequestHeader("x-request-id", required = false) explicitRequestId: String?
    ): ResponseEntity<GigaChatCompletionResponse> {
        val requestId = explicitRequestId ?: UUID.randomUUID().toString()

        log.info { "Chat completion request: $requestId, priority=$headerPriority" }

        // Validate request
        validateRequest(request)

        // Convert GigaChat native request to langchain4j format
        val chatRequest = GigaChatRequestAdapter.toChatRequest(request)

        val result = chatService.completions(
            request = chatRequest,
            headerPriority = headerPriority,
            bodyPriority = null,
            requestId = requestId,
            endpoint = "/v1/chat/completions"
        )

        // Convert langchain4j response to GigaChat native format
        val response = GigaChatResponseAdapter.toCompletionResponse(result.response)

        // Resolve priority for headers (from the service result or header)
        val priority = headerPriority?.let { Priority.resolveOrNull(it) } ?: Priority.P2

        return ResponseEntity.ok()
            .header("x-llm-proxy-request-id", requestId)
            .header("x-llm-proxy-priority", priority.value)
            .apply {
                result.queueWaitMs?.let {
                    header("x-llm-proxy-queue-wait-ms", it.toString())
                }
                result.providerLatencyMs?.let {
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
     * Note: Using InvokeResponse DTO for metrics since GigaChat native CompletionResponse
     * doesn't support custom fields.
     */
    @PostMapping("/invoke")
    suspend fun invoke(
        @RequestBody request: GigaChatCompletionRequest,
        @RequestHeader("X-Priority", required = false) headerPriority: String?,
        @RequestHeader("x-request-id", required = false) explicitRequestId: String?
    ): ResponseEntity<ru.ddd.llmproxy.presentation.dto.InvokeResponse> {
        val requestId = explicitRequestId ?: UUID.randomUUID().toString()

        log.info { "Chat invoke request: $requestId, priority=$headerPriority" }

        // Validate request
        validateRequest(request)

        // Convert GigaChat native request to langchain4j format
        val chatRequest = GigaChatRequestAdapter.toChatRequest(request)

        val result = chatService.completions(
            request = chatRequest,
            headerPriority = headerPriority,
            bodyPriority = null,
            requestId = requestId,
            endpoint = "/v1/chat/invoke"
        )

        // Resolve priority for response
        val priority = headerPriority?.let { Priority.resolveOrNull(it) } ?: Priority.P2

        // Wrap with metrics in InvokeResponse
        val metrics = ru.ddd.llmproxy.presentation.dto.InvokeResponse.Metrics(
            queueWaitMs = result.queueWaitMs,
            providerLatencyMs = result.providerLatencyMs,
            priority = priority.value,
            endpoint = "/v1/chat/invoke"
        )

        val invokeResponse = ru.ddd.llmproxy.presentation.dto.InvokeResponse.from(
            result.response,
            requestId,
            metrics
        )

        return ResponseEntity.ok()
            .header("x-llm-proxy-request-id", requestId)
            .header("x-llm-proxy-priority", priority.value)
            .header("x-request-id", requestId)
            .body(invokeResponse)
    }

    /**
     * Validates the GigaChat chat request.
     */
    private fun validateRequest(request: GigaChatCompletionRequest) {
        if (request.messages().isEmpty()) {
            throw InvalidRequestError("messages field is required and must not be empty")
        }

        if (request.messages().any { it.content().isNullOrBlank() }) {
            throw InvalidRequestError("Message content must not be null or empty")
        }

        // Note: GigaChat's stream parameter is handled by the native library
        // We don't need to explicitly ignore it as it's part of the CompletionRequest
    }
}
