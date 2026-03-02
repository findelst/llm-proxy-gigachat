package ru.ddd.llmproxy.infrastructure.gigachat

import chat.giga.client.auth.AuthClient
import chat.giga.client.auth.AuthClientBuilder
import chat.giga.langchain4j.GigaChatChatModel
import chat.giga.langchain4j.GigaChatChatRequestParameters
import chat.giga.model.Scope
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.ChatProviderPort
import ru.ddd.llmproxy.domain.model.ProviderError
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties

private val log = KotlinLogging.logger {}

/**
 * GigaChat implementation of ChatProviderPort.
 *
 * Uses langchain4j-gigachat library for GigaChat API integration.
 */
@Component
@ConditionalOnMissingBean(ChatProviderPort::class)
class GigaChatProvider(
    private val properties: LlmProxyProperties
) : ChatProviderPort<ChatRequest> {

    private val chatModel: ChatModel by lazy { createChatModel() }

    override suspend fun generate(request: ChatRequest): ChatResponse {
        log.debug { "Sending request to GigaChat: ${request.messages().size} messages" }

        return try {
            withContext(Dispatchers.IO) {
                val response = chatModel.chat(request)
                log.debug { "Received response from GigaChat" }
                response
            }
        } catch (e: Exception) {
            log.error(e) { "GigaChat API error" }
            throw ProviderError(
                message = "GigaChat API error: ${e.message}",
                providerStatus = 500,
                cause = e
            )
        }
    }

    override fun providerName(): String = "GigaChat"

    override fun baseUrl(): String = properties.api.baseUrl

    override suspend fun isHealthy(): Boolean {
        return try {
            properties.api.key.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates and configures the GigaChat chat model.
     */
    private fun createChatModel(): ChatModel {
        log.info { "Initializing GigaChat model with base URL: ${properties.api.baseUrl}" }

        val modelName = properties.model.effectiveModelName()

        return GigaChatChatModel.builder()
            .defaultChatRequestParameters(
                GigaChatChatRequestParameters.builder()
                    .modelName(modelName)
                    .build()
            )
            .authClient(
                AuthClient.builder()
                    .withOAuth(
                        AuthClientBuilder.OAuthBuilder.builder()
                            .scope(Scope.GIGACHAT_API_PERS)
                            .authKey(properties.api.key)
                            .build()
                    )
                    .build()
            )
            .logRequests(true)
            .logResponses(true)
            .build()
    }
}
