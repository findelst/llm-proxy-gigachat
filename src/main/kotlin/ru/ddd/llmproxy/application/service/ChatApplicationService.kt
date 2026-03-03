package ru.ddd.llmproxy.application.service

import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.ddd.llmproxy.application.port.ChatProviderPort
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.CacheKey
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.QueuedRequest
import ru.ddd.llmproxy.domain.model.QueueMetrics
import ru.ddd.llmproxy.domain.repository.CacheRepository
import ru.ddd.llmproxy.domain.service.QueueService
import ru.ddd.llmproxy.infrastructure.cache.CacheKeyGenerator
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties

private val log = KotlinLogging.logger {}

/**
 * Application service for chat completion operations.
 *
 * Orchestrates:
 * - Cache lookup and storage
 * - Priority resolution
 * - Provider calls
 * - Metrics recording
 */
@Service
class ChatApplicationService(
    private val chatProvider: ChatProviderPort<ChatRequest>,
    private val queueService: QueueService<ChatRequest, ChatResponse>,
    private val cacheRepository: CacheRepository<ChatResponse>,
    private val cacheKeyGenerator: CacheKeyGenerator,
    private val priorityResolver: PriorityResolver,
    private val metricsPort: MetricsPort,
    private val properties: LlmProxyProperties
) {
    /**
     * Processes a chat completion request.
     *
     * @param request The chat request
     * @param headerPriority Priority from X-Priority header
     * @param bodyPriority Priority from request body
     * @param requestId Request ID for tracing
     * @param endpoint The API endpoint being called
     * @return ChatResponse with metrics headers
     */
    suspend fun completions(
        request: ChatRequest,
        headerPriority: String?,
        bodyPriority: String?,
        requestId: String,
        endpoint: String
    ): ChatResult {
        val priority = priorityResolver.resolve(headerPriority, bodyPriority)
        log.debug { "Processing request $requestId with priority $priority" }

        val queueMetrics = QueueMetrics.create(requestId, priority, endpoint)

        // Try cache first
        if (properties.cache.enabled) {
            val cacheKey = cacheKeyGenerator.generate(request, chatProvider.baseUrl())
            val cached = cacheRepository.get(cacheKey)
            if (cached != null) {
                log.info { "Cache hit for request $requestId" }
                metricsPort.recordCacheHit()
                metricsPort.recordRequest(endpoint, priority, "success")
                return ChatResult(
                    response = cached,
                    metrics = queueMetrics.startProcessing().completeProcessing(),
                    cacheHit = true
                )
            }
            metricsPort.recordCacheMiss()
        }

        // Create queued request and process via priority queue
        val queuedRequest = QueuedRequest.create<ChatRequest, ChatResponse>(
            payload = request,
            priority = priority,
            endpoint = endpoint
        )

        // Track queue wait time - queueMetrics already has queuedAt from creation
        val response = queueService.enqueue(queuedRequest)

        // Calculate queue wait time from queued request metrics
        val completedMetrics = queuedRequest.metrics.completeProcessing()
        val resultQueueWaitMs = completedMetrics.queueWaitMs
            ?: java.time.Duration.between(queueMetrics.queuedAt, java.time.Instant.now()).toMillis()

        // Record metrics
        metricsPort.recordRequest(endpoint, priority, "success")
        metricsPort.recordQueueWait(endpoint, priority, resultQueueWaitMs)
        completedMetrics.providerLatencyMs?.let {
            metricsPort.recordProviderLatency(endpoint, priority, it)
        }

        log.info {
            "Request $requestId completed via queue: " +
                    "queueWait=${resultQueueWaitMs}ms, " +
                    "providerLatency=${completedMetrics.providerLatencyMs}ms"
        }

        val result = ChatResult(
            response = response,
            metrics = completedMetrics,
            cacheHit = false
        )

        // Cache the result
        if (properties.cache.enabled && result.cacheHit.not()) {
            val cacheKey = cacheKeyGenerator.generate(request, chatProvider.baseUrl())
            cacheRepository.set(cacheKey, result.response)
            log.debug { "Cached response for request $requestId" }
        }

        return result
    }

    /**
     * Result of a chat completion request.
     */
    data class ChatResult(
        val response: ChatResponse,
        val metrics: QueueMetrics,
        val cacheHit: Boolean
    )
}
