package ru.ddd.llmproxy.infrastructure.queue

import kotlinx.coroutines.delay
import mu.KotlinLogging
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.ProviderError
import ru.ddd.llmproxy.domain.model.RetryConfig
import kotlin.math.min
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * Executes operations with retry logic and exponential backoff.
 *
 * @param config Retry configuration
 * @param metricsPort Metrics port for recording retry events
 */
class RetryExecutor(
    private val config: RetryConfig,
    private val metricsPort: MetricsPort? = null
) {
    /**
     * Executes the given operation with retry logic.
     *
     * @param operation The operation to execute
     * @param priority The priority level for metrics
     * @param onRetry Callback invoked before each retry attempt with attempt number and error
     * @return The result of the operation
     * @throws ProviderError if all retries are exhausted
     * @throws Exception for non-retryable errors
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        priority: Priority,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null
    ): T {
        if (!config.enabled) {
            return operation()
        }

        var lastError: Throwable? = null
        var currentDelay = config.initialDelayMs

        repeat(config.maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: ProviderError) {
                lastError = e

                if (!config.isRetryable(e.providerStatus)) {
                    log.warn { "Non-retryable error (status ${e.providerStatus}), not retrying" }
                    throw e
                }

                if (attempt < config.maxAttempts - 1) {
                    val delayMs = calculateDelay(currentDelay)
                    log.warn {
                        "Retry attempt ${attempt + 1}/${config.maxAttempts} after ${delayMs}ms " +
                                "due to error: ${e.message}"
                    }

                    metricsPort?.recordRetryAttempt(priority, attempt + 1)
                    onRetry?.invoke(attempt + 1, e)

                    delay(delayMs)
                    currentDelay = min(
                        (currentDelay * config.backoffMultiplier).toLong(),
                        config.maxDelayMs
                    )
                }
            } catch (e: Exception) {
                // Non-ProviderError exceptions are not retried
                log.debug { "Non-retryable exception type: ${e::class.simpleName}" }
                throw e
            }
        }

        // All retries exhausted
        val finalError = lastError!!
        log.error { "All ${config.maxAttempts} retry attempts exhausted" }
        metricsPort?.recordRetryFailure(priority, (finalError as? ProviderError)?.providerStatus?.toString() ?: "unknown")

        throw finalError
    }

    /**
     * Calculates the delay with jitter to avoid thundering herd.
     *
     * @param baseDelay The base delay in milliseconds
     * @return The delay with jitter applied
     */
    private fun calculateDelay(baseDelay: Long): Long {
        // Add jitter: +/- 20% of base delay
        val jitterRange = (baseDelay * 0.2).toLong()
        val jitter = if (jitterRange > 0) {
            Random.nextLong(-jitterRange, jitterRange + 1)
        } else {
            0L
        }
        return (baseDelay + jitter).coerceAtLeast(0)
    }
}
