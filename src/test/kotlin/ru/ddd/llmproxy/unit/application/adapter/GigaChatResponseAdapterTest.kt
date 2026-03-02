package ru.ddd.llmproxy.unit.application.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.ChatResponseMetadata
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.output.TokenUsage
import dev.langchain4j.model.output.FinishReason
import ru.ddd.llmproxy.application.adapter.GigaChatResponseAdapter
import chat.giga.model.completion.CompletionResponse as GigaChatCompletionResponse
import chat.giga.model.completion.ChatMessageRole
import chat.giga.model.completion.ChoiceFinishReason

/**
 * Unit test for GigaChatResponseAdapter.
 *
 * Tests conversion logic between langchain4j ChatResponse and GigaChat's native CompletionResponse.
 */
class GigaChatResponseAdapterTest {

    @Test
    fun `should convert basic chat response`() {
        val metadata = ChatResponseMetadata.builder()
            .id("test-id")
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.from("Hello, world!")

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals("chat.completion", gigaResponse.`object`())
        assertEquals("GigaChat", gigaResponse.model())
        assertEquals(1, gigaResponse.choices().size)

        val choice = gigaResponse.choices()[0]
        assertEquals(0, choice.index())
        assertEquals(ChatMessageRole.ASSISTANT, choice.message().role())
        assertEquals("Hello, world!", choice.message().content())
        assertEquals(ChoiceFinishReason.STOP, choice.finishReason())
    }

    @Test
    fun `should convert response with usage information`() {
        val metadata = ChatResponseMetadata.builder()
            .id("test-id")
            .modelName("GigaChat")
            .tokenUsage(TokenUsage(100, 50, 150))
            .build()

        val aiMessage = AiMessage.builder().text("Test response").build()

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertNotNull(gigaResponse.usage())
        assertEquals(100, gigaResponse.usage()!!.promptTokens())
        assertEquals(50, gigaResponse.usage()!!.completionTokens())
        assertEquals(150, gigaResponse.usage()!!.totalTokens())
    }

    @Test
    fun `should handle response without usage information`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.from("Test response")

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertNull(gigaResponse.usage())
    }


    @Test
    fun `should handle null finish reason`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.builder().text("Response").build()

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .finishReason(null)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals(ChoiceFinishReason.STOP, gigaResponse.choices()[0].finishReason())
    }

    @Test
    fun `should extract content from ai message`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.from("Test content here")

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals("Test content here", gigaResponse.choices()[0].message().content())
    }

    @Test
    fun `should use model name from response`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat-Pro")
            .build()

        val aiMessage = AiMessage.builder().text("Response").build()

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals("GigaChat-Pro", gigaResponse.model())
    }

    @Test
    fun `should set created timestamp`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.builder().text("Response").build()

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val beforeTimestamp = System.currentTimeMillis() / 1000
        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)
        val afterTimestamp = System.currentTimeMillis() / 1000

        assertTrue(gigaResponse.created() >= beforeTimestamp)
        assertTrue(gigaResponse.created() <= afterTimestamp)
    }

    @Test
    fun `should set correct object type`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.builder().text("Response").build()

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals("chat.completion", gigaResponse.`object`())
    }

    @Test
    fun `should handle empty content`() {
        val metadata = ChatResponseMetadata.builder()
            .modelName("GigaChat")
            .build()

        val aiMessage = AiMessage.from("")

        val langchainResponse = ChatResponse.builder()
            .metadata(metadata)
            .aiMessage(aiMessage)
            .build()

        val gigaResponse = GigaChatResponseAdapter.toCompletionResponse(langchainResponse)

        assertEquals("", gigaResponse.choices()[0].message().content())
    }
}
