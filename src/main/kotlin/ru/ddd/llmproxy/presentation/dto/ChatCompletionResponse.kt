package ru.ddd.llmproxy.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import dev.langchain4j.model.chat.response.ChatResponse

/**
 * DTO for chat completion response (OpenAI-compatible format).
 */
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<Choice>,
    val usage: Usage?,
    val model: String?,
    val created: Long = System.currentTimeMillis() / 1000
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

    companion object {
        /**
         * Converts from langchain4j ChatResponse.
         */
        fun from(response: ChatResponse): ChatCompletionResponse {
            val aiMessage = response.aiMessage()
            return ChatCompletionResponse(
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
                }
            )
        }
    }
}
