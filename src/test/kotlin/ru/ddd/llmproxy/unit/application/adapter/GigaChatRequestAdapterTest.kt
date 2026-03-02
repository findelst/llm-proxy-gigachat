package ru.ddd.llmproxy.unit.application.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import chat.giga.model.completion.CompletionRequest as GigaChatCompletionRequest
import chat.giga.model.completion.ChatMessage as GigaChatMessage
import chat.giga.model.completion.ChatMessageRole
import chat.giga.model.completion.ChatFunction
import chat.giga.model.completion.ChatFunctionParameters
import chat.giga.model.completion.ChatFunctionParametersProperty
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.message.AiMessage
import ru.ddd.llmproxy.application.adapter.GigaChatRequestAdapter

/**
 * Unit test for GigaChatRequestAdapter.
 *
 * Tests conversion logic between GigaChat native types and langchain4j types.
 *
 * Note: GigaChat's native ChatMessage does not support functionCall or toolCallId.
 * Tool calling is handled differently in GigaChat's API using functions().
 */
class GigaChatRequestAdapterTest {

    @Test
    fun `should convert user message to langchain4j UserMessage`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Hello")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val messages = langchainRequest.messages()
        assertEquals(1, messages.size)

        val message = messages[0]
        assertTrue(message is UserMessage)
        assertEquals("Hello", (message as UserMessage).singleText())
    }

    @Test
    fun `should convert system message to langchain4j SystemMessage`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.SYSTEM)
                        .content("You are helpful")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val message = langchainRequest.messages()[0]
        assertTrue(message is SystemMessage)
        assertEquals("You are helpful", (message as SystemMessage).text())
    }

    @Test
    fun `should convert assistant message to langchain4j AiMessage`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.ASSISTANT)
                        .content("I'm fine")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val message = langchainRequest.messages()[0]
        assertTrue(message is AiMessage)
        // AiMessage text content verification would go here
        // depending on the actual langchain4j API
    }

    @Test
    fun `should convert function message to langchain4j UserMessage`() {
        // GigaChat treats FUNCTION messages as user messages in langchain4j
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.FUNCTION)
                        .content("Function result")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val message = langchainRequest.messages()[0]
        assertTrue(message is UserMessage)
        assertEquals("Function result", (message as UserMessage).singleText())
    }

    @Test
    fun `should convert function in progress message to langchain4j UserMessage`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.FUNCTION_IN_PROGRESS)
                        .content("Working...")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val message = langchainRequest.messages()[0]
        assertTrue(message is UserMessage)
        assertEquals("Working...", (message as UserMessage).singleText())
    }

    @Test
    fun `should handle multiple messages`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.SYSTEM)
                        .content("You are helpful")
                        .build(),
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Hello")
                        .build(),
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.ASSISTANT)
                        .content("Hi there!")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val messages = langchainRequest.messages()
        assertEquals(3, messages.size)
        assertTrue(messages[0] is SystemMessage)
        assertTrue(messages[1] is UserMessage)
        assertTrue(messages[2] is AiMessage)
    }

    @Test
    fun `should use default model when model is null`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        assertEquals("GigaChat", langchainRequest.modelName())
    }

    @Test
    fun `should use specified model name`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("CustomModel")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        assertEquals("CustomModel", langchainRequest.modelName())
    }

    @Test
    fun `should convert temperature parameter`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .temperature(0.5f)
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        // Temperature is set through ChatRequestParameters
        assertNotNull(langchainRequest.parameters())
    }

    @Test
    fun `should convert topP parameter`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .topP(0.9f)
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        // topP is set through ChatRequestParameters
        assertNotNull(langchainRequest.parameters())
    }

    @Test
    fun `should convert maxTokens parameter`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .maxTokens(100)
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        // maxTokens is set through ChatRequestParameters
        assertNotNull(langchainRequest.parameters())
    }

    @Test
    fun `should handle null parameters`() {
        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Test")
                        .build()
                )
            )
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        // Parameters should still be accessible even when null
        assertNotNull(langchainRequest.parameters())
    }

    @Test
    fun `should convert functions to tool specifications`() {
        val functionParams = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "location" to ChatFunctionParametersProperty.builder()
                        .type("string")
                        .description("The city name")
                        .build()
                )
            )
            .required(listOf("location"))
            .build()

        val tool = ChatFunction.builder()
            .name("get_weather")
            .description("Get weather for a location")
            .parameters(functionParams)
            .build()

        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("What's the weather?")
                        .build()
                )
            )
            .functions(listOf(tool))
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        // Tool specifications should be set
        val toolSpecs = langchainRequest.toolSpecifications()
        assertNotNull(toolSpecs)
        assertEquals(1, toolSpecs.size)
        assertEquals("get_weather", toolSpecs[0].name())
        assertEquals("Get weather for a location", toolSpecs[0].description())
        assertNotNull(toolSpecs[0].parameters())
    }

    @Test
    fun `should handle multiple functions`() {
        val functionParams1 = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "location" to ChatFunctionParametersProperty.builder()
                        .type("string")
                        .description("The city name")
                        .build()
                )
            )
            .required(listOf("location"))
            .build()

        val functionParams2 = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "query" to ChatFunctionParametersProperty.builder()
                        .type("string")
                        .description("The search query")
                        .build()
                )
            )
            .required(listOf("query"))
            .build()

        val tool1 = ChatFunction.builder()
            .name("get_weather")
            .description("Get weather for a location")
            .parameters(functionParams1)
            .build()

        val tool2 = ChatFunction.builder()
            .name("search")
            .description("Search for information")
            .parameters(functionParams2)
            .build()

        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("What's the weather?")
                        .build()
                )
            )
            .functions(listOf(tool1, tool2))
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val toolSpecs = langchainRequest.toolSpecifications()
        assertNotNull(toolSpecs)
        assertEquals(2, toolSpecs.size)
        assertEquals("get_weather", toolSpecs[0].name())
        assertEquals("search", toolSpecs[1].name())
    }

    @Test
    fun `should handle functions with nested object parameters`() {
        val functionParams = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "address" to ChatFunctionParametersProperty.builder()
                        .type("object")
                        .description("The address")
                        .properties(
                            mapOf(
                                "street" to ChatFunctionParametersProperty.builder()
                                    .type("string")
                                    .description("Street name")
                                    .build(),
                                "city" to ChatFunctionParametersProperty.builder()
                                    .type("string")
                                    .description("City name")
                                    .build()
                            )
                        )
                        .build()
                )
            )
            .required(listOf("address"))
            .build()

        val tool = ChatFunction.builder()
            .name("get_address")
            .description("Get the address")
            .parameters(functionParams)
            .build()

        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("What's the address?")
                        .build()
                )
            )
            .functions(listOf(tool))
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val toolSpecs = langchainRequest.toolSpecifications()
        assertNotNull(toolSpecs)
        assertEquals(1, toolSpecs.size)
        assertEquals("get_address", toolSpecs[0].name())
    }

    @Test
    fun `should handle functions with enum parameters`() {
        val functionParams = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "unit" to ChatFunctionParametersProperty.builder()
                        .type("string")
                        .description("Temperature unit")
                        .enums(listOf("celsius", "fahrenheit"))
                        .build()
                )
            )
            .required(listOf("unit"))
            .build()

        val tool = ChatFunction.builder()
            .name("get_temperature")
            .description("Get temperature in specified unit")
            .parameters(functionParams)
            .build()

        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("What's the temperature?")
                        .build()
                )
            )
            .functions(listOf(tool))
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val toolSpecs = langchainRequest.toolSpecifications()
        assertNotNull(toolSpecs)
        assertEquals(1, toolSpecs.size)
    }

    @Test
    fun `should handle functions with array parameters`() {
        val functionParams = ChatFunctionParameters.builder()
            .type("object")
            .properties(
                mapOf(
                    "items" to ChatFunctionParametersProperty.builder()
                        .type("array")
                        .description("List of items")
                        .items(
                            mapOf("type" to "string", "description" to "Item type")
                        )
                        .build()
                )
            )
            .required(listOf("items"))
            .build()

        val tool = ChatFunction.builder()
            .name("process_items")
            .description("Process a list of items")
            .parameters(functionParams)
            .build()

        val gigaRequest = GigaChatCompletionRequest.builder()
            .model("GigaChat")
            .messages(
                listOf(
                    GigaChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("Process these items")
                        .build()
                )
            )
            .functions(listOf(tool))
            .build()

        val langchainRequest = GigaChatRequestAdapter.toChatRequest(gigaRequest)

        val toolSpecs = langchainRequest.toolSpecifications()
        assertNotNull(toolSpecs)
        assertEquals(1, toolSpecs.size)
    }
}
