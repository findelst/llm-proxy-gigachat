package ru.ddd.llmproxy.presentation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ChatRequestParameters

/**
 * DTO for incoming chat completion requests (OpenAI-compatible format).
 */
data class ChatCompletionRequest(
    val model: String? = null,
    val messages: List<Message> = emptyList(),
    val temperature: Double? = null,
    @JsonProperty("top_p")
    val topP: Double? = null,
    @JsonProperty("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val stream: Boolean = false,
    val priority: String? = null
) {
    data class Message(
        val role: String,
        val content: String?
    )

    /**
     * Converts to langchain4j ChatRequest.
     */
    fun toChatRequest(defaultModel: String = "GigaChat"): ChatRequest {
        val chatMessages = messages.map { msg ->
            when (msg.role.lowercase()) {
                "system" -> SystemMessage.from(msg.content ?: "")
                "user" -> UserMessage.from(msg.content ?: "")
                "assistant" -> AiMessage.from(msg.content ?: "")
                else -> UserMessage.from(msg.content ?: "")
            }
        }

        return ChatRequest.builder()
            .messages(chatMessages)
            .parameters(
                ChatRequestParameters.builder()
                    .modelName(model ?: defaultModel)
                    .temperature(temperature)
                    .topP(topP)
                    .maxOutputTokens(maxTokens)
                    .build()
            )
            .build()
    }
}
