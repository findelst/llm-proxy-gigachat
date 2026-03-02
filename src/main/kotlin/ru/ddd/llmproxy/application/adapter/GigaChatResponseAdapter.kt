package ru.ddd.llmproxy.application.adapter

import chat.giga.model.completion.Choice
import chat.giga.model.completion.ChoiceFinishReason
import chat.giga.model.completion.ChoiceMessage
import chat.giga.model.completion.CompletionResponse as GigaChatCompletionResponse
import chat.giga.model.completion.Usage as GigaChatUsage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.FinishReason
import java.time.Instant

/**
 * Adapter for converting langchain4j's ChatResponse to GigaChat's native CompletionResponse.
 *
 * This enables the proxy to return responses in GigaChat's native model class format.
 *
 * Note: GigaChat's native CompletionResponse does not include an 'id' field.
 * Clients expecting OpenAI-compatible responses will need to handle this difference.
 */
object GigaChatResponseAdapter {

    /**
     * Converts langchain4j ChatResponse to GigaChat's native CompletionResponse.
     *
     * @param response The langchain4j chat response
     * @return GigaChat's native CompletionResponse
     */
    fun toCompletionResponse(response: ChatResponse): GigaChatCompletionResponse {
        val finishReason = mapFinishReason(response.finishReason())

        val choice = Choice.builder()
            .index(0)
            .message(
                ChoiceMessage.builder()
                    .role(chat.giga.model.completion.ChatMessageRole.ASSISTANT)
                    .content(extractContent(response))
                    .build()
            )
            .finishReason(finishReason)
            .build()

        val builder = GigaChatCompletionResponse.builder()
            .`object`("chat.completion")
            .created(Instant.now().epochSecond.toInt())
            .model(response.modelName())
            .choices(listOf(choice))

        // Add usage if available
        val usage = response.tokenUsage()
        if (usage != null) {
            builder.usage(
                GigaChatUsage.builder()
                    .promptTokens(usage.inputTokenCount())
                    .completionTokens(usage.outputTokenCount())
                    .totalTokens(usage.inputTokenCount() + usage.outputTokenCount())
                    .build()
            )
        }

        return builder.build()
    }

    /**
     * Maps langchain4j finish reason to GigaChat's ChoiceFinishReason.
     */
    private fun mapFinishReason(reason: FinishReason?): ChoiceFinishReason {
        return when (reason) {
            FinishReason.STOP -> ChoiceFinishReason.STOP
            FinishReason.LENGTH -> ChoiceFinishReason.LENGTH
            else -> ChoiceFinishReason.STOP
        }
    }

    /**
     * Extracts text content from the response.
     */
    private fun extractContent(response: ChatResponse): String? {
        return response.aiMessage()?.text()
    }
}
