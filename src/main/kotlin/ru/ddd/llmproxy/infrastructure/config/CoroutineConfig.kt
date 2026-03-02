package ru.ddd.llmproxy.infrastructure.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Kotlin coroutines.
 */
@Configuration
class CoroutineConfig {

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
}
