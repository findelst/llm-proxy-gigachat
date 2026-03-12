package ru.ddd.llmproxy.infrastructure.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.ddd.llmproxy.domain.model.ConcurrencyConfig

/**
 * Configuration for Kotlin coroutines.
 */
@Configuration
class CoroutineConfig(
    private val properties: LlmProxyProperties
) {

    /**
     * IO dispatcher for blocking operations.
     * Limited parallelism to prevent resource exhaustion.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Bean
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(3)

    /**
     * Default dispatcher for CPU-bound operations.
     */
    @Bean
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Provides ConcurrencyConfig as a Spring bean.
     */
    @Bean
    fun concurrencyConfig(): ConcurrencyConfig =
        ConcurrencyConfig.from(properties.queue)
}
