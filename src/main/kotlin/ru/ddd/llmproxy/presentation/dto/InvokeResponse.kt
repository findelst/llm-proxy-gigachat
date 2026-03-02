package ru.ddd.llmproxy.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import dev.langchain4j.model.chat.response.ChatResponse

/**
 * Response wrapper for /v1/chat/invoke endpoint.
 *
 * Includes all chat response fields plus metrics in the body.
 */
data class InvokeResponse(
    val id: String?,
    val choices: List<Choice>,
    val usage: Usage?,
    val model: String?,
    @JsonProperty("request_id")
    val requestId: String,
    val metrics: Metrics
) {
    data class Choice(
        val index: Int = 0,
        val message: Message,
        @JsonProperty("finish_reason")
        val finishReason: String? = "stop"
    )

    data class Message(
        val role: String,
        val content: String?
    )

    data class Usage(
        @JsonProperty("prompt_tokens")
        val promptTokens: Int,
        @JsonProperty("completion_tokens")
        val completionTokens: Int,
        @JsonProperty("total_tokens")
        val totalTokens: Int
    )

    data class Metrics(
        @JsonProperty("queue_wait_ms")
        val queueWaitMs: Long?,
        @JsonProperty("provider_latency_ms")
        val providerLatencyMs: Long?,
        val priority: String,
        val endpoint: String
    )

    companion object {
        /**
         * Creates InvokeResponse from langchain4j ChatResponse with metrics.
         */
        fun from(response: ChatResponse, requestId: String, metrics: Metrics): InvokeResponse {
            val aiMessage = response.aiMessage()
            return InvokeResponse(
                id = response.metadata().id(),
                model = response.metadata().modelName(),
                choices = listOf(
                    Choice(
                        message = Message(
                            role = "assistant",
                            content = aiMessage?.text()
                        )
                    )
                ),
                usage = response.tokenUsage()?.let { usage ->
                    Usage(
                        promptTokens = usage.inputTokenCount(),
                        completionTokens = usage.outputTokenCount(),
                        totalTokens = usage.totalTokenCount()
                    )
                },
                requestId = requestId,
                metrics = metrics
            )
        }
    }
}
