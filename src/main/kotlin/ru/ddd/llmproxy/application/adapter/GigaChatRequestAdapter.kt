package ru.ddd.llmproxy.application.adapter

import chat.giga.model.completion.ChatMessage as GigaChatMessage
import chat.giga.model.completion.ChatMessageRole
import chat.giga.model.completion.CompletionRequest as GigaChatCompletionRequest
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.ChatRequestParameters
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement

/**
 * Adapter for converting GigaChat's native CompletionRequest to langchain4j's ChatRequest.
 *
 * This enables the proxy to accept GigaChat's native model classes while
 * maintaining compatibility with langchain4j-gigachat provider.
 *
 * Note: GigaChat's native ChatMessage does not include functionCall or toolCallId
 * fields. Tool calling uses the functions() method on CompletionRequest.
 */
object GigaChatRequestAdapter {

    /**
     * Converts GigaChat native CompletionRequest to langchain4j ChatRequest.
     *
     * @param request The native GigaChat completion request
     * @param defaultModel The default model name if not specified in request
     * @return langchain4j ChatRequest compatible with GigaChat provider
     */
    fun toChatRequest(request: GigaChatCompletionRequest, defaultModel: String = "GigaChat"): ChatRequest {
        val chatMessages = request.messages().map { msg ->
            convertGigaChatMessage(msg)
        }

        val parametersBuilder = ChatRequestParameters.builder()
            .modelName(request.model() ?: defaultModel)
            .temperature(request.temperature()?.toDouble())
            .topP(request.topP()?.toDouble())
            .maxOutputTokens(request.maxTokens())

        // Add tool specifications if functions are defined
        request.functions()?.let { functions ->
            val toolSpecs = functions.map { it.toToolSpecification() }
            if (toolSpecs.isNotEmpty()) {
                parametersBuilder.toolSpecifications(toolSpecs)
            }
        }

        return ChatRequest.builder()
            .messages(chatMessages)
            .parameters(parametersBuilder.build())
            .build()
    }

    /**
     * Converts a GigaChat native ChatMessage to langchain4j message type.
     */
    private fun convertGigaChatMessage(msg: GigaChatMessage): dev.langchain4j.data.message.ChatMessage {
        return when (msg.role()) {
            ChatMessageRole.SYSTEM -> SystemMessage.from(msg.content() ?: "")
            ChatMessageRole.USER -> UserMessage.from(msg.content() ?: "")
            ChatMessageRole.ASSISTANT -> AiMessage.from(msg.content() ?: "")
            ChatMessageRole.FUNCTION -> UserMessage.from(msg.content() ?: "") // Treat as user message
            ChatMessageRole.FUNCTION_IN_PROGRESS -> UserMessage.from(msg.content() ?: "")
        }
    }

    /**
     * Converts GigaChat native ChatFunction to langchain4j ToolSpecification.
     */
    private fun chat.giga.model.completion.ChatFunction.toToolSpecification(): ToolSpecification {
        val builder = ToolSpecification.builder()
            .name(this.name())
            .description(this.description() ?: "")

        this.parameters()?.let { params ->
            val jsonSchema = buildJsonObjectSchema(params)
            builder.parameters(jsonSchema)
        }

        return builder.build()
    }

    /**
     * Builds a langchain4j JsonObjectSchema from GigaChat ChatFunctionParameters.
     */
    private fun buildJsonObjectSchema(params: chat.giga.model.completion.ChatFunctionParameters): JsonObjectSchema {
        val builder = JsonObjectSchema.builder()

        params.properties()?.forEach { (name, prop) ->
            builder.addProperty(name, buildJsonSchemaElement(prop))
        }

        params.required()?.forEach { requiredProp ->
            builder.required(requiredProp)
        }

        return builder.build()
    }

    /**
     * Converts GigaChat ChatFunctionParametersProperty to langchain4j JsonSchemaElement.
     */
    private fun buildJsonSchemaElement(prop: chat.giga.model.completion.ChatFunctionParametersProperty): JsonSchemaElement {
        val propType = prop.type()
        val description = prop.description()

        return when (propType) {
            "string" -> {
                val enums = prop.enums()
                if (enums != null && enums.isNotEmpty()) {
                    dev.langchain4j.model.chat.request.json.JsonEnumSchema.builder()
                        .enumValues(enums)
                        .description(description)
                        .build()
                } else {
                    dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                        .description(description)
                        .build()
                }
            }
            "integer" -> dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder()
                .description(description)
                .build()
            "number" -> dev.langchain4j.model.chat.request.json.JsonNumberSchema.builder()
                .description(description)
                .build()
            "boolean" -> dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder()
                .description(description)
                .build()
            "array" -> {
                val itemsMap = prop.items()
                dev.langchain4j.model.chat.request.json.JsonArraySchema.builder()
                    .description(description)
                    .items(itemsMap?.let { buildJsonSchemaElementFromMap(it) })
                    .build()
            }
            "object" -> {
                val nestedBuilder = dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                    prop.properties()?.forEach { (name, nestedProp) ->
                        nestedBuilder.addProperty(name, buildJsonSchemaElement(nestedProp))
                    }
                    nestedBuilder.description(description)
                    .build()
            }
            else -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
                .description(description)
                .build()
        }
    }

    /**
     * Helper to build JsonSchemaElement from a Map (from items() property).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun buildJsonSchemaElementFromMap(map: Map<String, Any>): JsonSchemaElement {
        // For simple maps in items, convert to a generic string schema
        // This is a simplified approach - full implementation would require recursive conversion
        return dev.langchain4j.model.chat.request.json.JsonStringSchema.builder()
            .description("Map of values")
            .build()
    }
}
