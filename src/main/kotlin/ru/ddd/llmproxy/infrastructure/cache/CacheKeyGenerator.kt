package ru.ddd.llmproxy.infrastructure.cache

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.request.ChatRequest
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.domain.model.CacheKey
import java.security.MessageDigest

/**
 * Generator for deterministic cache keys from requests.
 *
 * Uses SHA-256 hash of a canonical JSON representation.
 */
@Component
class CacheKeyGenerator(
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
) {
    /**
     * Generates a cache key from a request and provider URL.
     *
     * @param request The request to hash
     * @param providerUrl The provider base URL for cache isolation
     * @return A deterministic CacheKey
     */
    fun <T> generate(request: T, providerUrl: String): CacheKey {
        val json = when (request) {
            is ChatRequest -> serializeChatRequest(request)
            else -> objectMapper.writeValueAsString(request)
        }
        val hash = sha256Hash(json)
        return CacheKey(hash = hash, providerUrl = providerUrl)
    }

    /**
     * Serializes a langchain4j ChatRequest to JSON.
     */
    private fun serializeChatRequest(request: ChatRequest): String {
        val messages = request.messages().map { msg ->
            mapOf(
                "role" to extractRole(msg),
                "content" to extractContent(msg)
            )
        }

        val map = mutableMapOf<String, Any?>(
            "messages" to messages
        )

        request.parameters()?.let { params ->
            params.modelName()?.let { map["model"] = it }
            params.temperature()?.let { map["temperature"] = it }
            params.topP()?.let { map["top_p"] = it }
            params.maxOutputTokens()?.let { map["max_tokens"] = it }
        }

        return objectMapper.writeValueAsString(map)
    }

    /**
     * Extracts the role from a ChatMessage.
     */
    private fun extractRole(message: ChatMessage): String {
        return when (message) {
            is dev.langchain4j.data.message.SystemMessage -> "system"
            is dev.langchain4j.data.message.UserMessage -> "user"
            is dev.langchain4j.data.message.AiMessage -> "assistant"
            is dev.langchain4j.data.message.ToolExecutionResultMessage -> "tool"
            else -> "unknown"
        }
    }

    /**
     * Extracts the content from a ChatMessage.
     */
    private fun extractContent(message: ChatMessage): String? {
        return when (message) {
            is dev.langchain4j.data.message.SystemMessage -> message.text()
            is dev.langchain4j.data.message.UserMessage -> message.singleText()
            is dev.langchain4j.data.message.AiMessage -> message.text()
            is dev.langchain4j.data.message.ToolExecutionResultMessage -> message.text()
            else -> message.toString()
        }
    }

    /**
     * Generates a cache key from a JSON string and provider URL.
     *
     * @param json The JSON string to hash
     * @param providerUrl The provider base URL for cache isolation
     * @return A deterministic CacheKey
     */
    fun generateFromJson(json: String, providerUrl: String): CacheKey {
        val hash = sha256Hash(json)
        return CacheKey(hash = hash, providerUrl = providerUrl)
    }

    /**
     * Computes SHA-256 hash of a string.
     *
     * @param input The string to hash
     * @return Hex string of the SHA-256 digest
     */
    private fun sha256Hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
