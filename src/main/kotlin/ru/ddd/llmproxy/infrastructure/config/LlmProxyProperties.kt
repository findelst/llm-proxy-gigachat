package ru.ddd.llmproxy.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.ddd.llmproxy.domain.model.RetryConfig
import java.time.Duration

/**
 * Configuration properties for LLM Proxy.
 *
 * Configuration prefix: llm.proxy
 */
@ConfigurationProperties(prefix = "llm.proxy")
data class LlmProxyProperties(
    val api: ApiConfig = ApiConfig(),
    val model: ModelConfig = ModelConfig(),
    val queue: QueueConfig = QueueConfig(),
    val cache: CacheConfig = CacheConfig()
) {
    data class ApiConfig(
        val baseUrl: String = "https://gigachat.devices.sberbank.ru/v1",
        val key: String = "",
        val timeout: Duration = Duration.ofMinutes(5)
    )

    data class ModelConfig(
        val defaultName: String = "GigaChat",
        val override: String? = null,
        val temperature: Double = 0.0,
        val topP: Double = 1.0,
        val maxTokens: Int = 50000
    ) {
        /**
         * Returns the effective model name, using override if set.
         */
        fun effectiveModelName(): String = override?.takeIf { it.isNotBlank() } ?: defaultName
    }

    data class QueueConfig(
        val defaultPriority: String = "p1",
        val maxLength: Int = 100,
        val retry: RetryConfig = RetryConfig(),
        val timeoutMinutes: Long = 10,
        val timeoutCheckIntervalMs: Long = 60000,
        // Concurrency control settings
        val maxConcurrent: Int = 3,
        val p1MaxThreads: Int = 2,
        val p3MaxThreads: Int = 1,
        val preemptionEnabled: Boolean = true
    ) {
        init {
            require(timeoutMinutes >= 0) { "timeoutMinutes must be >= 0 (0 = disabled)" }
            require(timeoutCheckIntervalMs > 0) { "timeoutCheckIntervalMs must be > 0" }
            require(maxConcurrent > 0) { "maxConcurrent must be > 0" }
            require(p1MaxThreads in 1..maxConcurrent) { "p1MaxThreads must be in 1..maxConcurrent" }
            require(p3MaxThreads in 1..maxConcurrent) { "p3MaxThreads must be in 1..maxConcurrent" }
        }
    }

    data class CacheConfig(
        val enabled: Boolean = true,
        val ttl: Duration = Duration.ofMinutes(10),
        val maxSize: Long = 1000
    )
}
