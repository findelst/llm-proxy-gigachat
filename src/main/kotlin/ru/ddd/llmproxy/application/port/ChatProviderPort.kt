package ru.ddd.llmproxy.application.port

import dev.langchain4j.model.chat.response.ChatResponse

/**
 * Port interface for LLM chat providers.
 *
 * This interface abstracts the LLM provider implementation,
 * allowing easy switching between different providers (GigaChat, OpenAI, etc.)
 */
interface ChatProviderPort<Req> {

    /**
     * Sends a chat completion request to the LLM provider.
     *
     * @param request The chat completion request
     * @return The chat response from the provider
     * @throws ProviderException if the provider returns an error
     */
    suspend fun generate(request: Req): ChatResponse

    /**
     * Returns the name of this provider.
     *
     * @return Provider name (e.g., "GigaChat", "OpenAI")
     */
    fun providerName(): String

    /**
     * Returns the base URL of this provider.
     *
     * @return Provider base URL
     */
    fun baseUrl(): String

    /**
     * Checks if the provider is available and healthy.
     *
     * @return true if provider is healthy, false otherwise
     */
    suspend fun isHealthy(): Boolean
}
