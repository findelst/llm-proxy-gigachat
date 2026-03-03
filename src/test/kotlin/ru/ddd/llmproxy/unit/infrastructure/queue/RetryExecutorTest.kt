package ru.ddd.llmproxy.unit.infrastructure.queue

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.domain.model.ProviderError
import ru.ddd.llmproxy.domain.model.RetryConfig
import ru.ddd.llmproxy.infrastructure.queue.RetryExecutor

/**
 * Tests for RetryExecutor.
 *
 * Verifies:
 * - Retry logic with exponential backoff
 * - Non-retryable error handling
 * - Max attempts limit
 * - Disabled retry behavior
 */
class RetryExecutorTest {

    private lateinit var config: RetryConfig
    private lateinit var retryExecutor: RetryExecutor

    @BeforeEach
    fun setUp() {
        config = RetryConfig(
            enabled = true,
            maxAttempts = 3,
            initialDelayMs = 10, // Fast for tests
            maxDelayMs = 100,
            backoffMultiplier = 2.0,
            retryableStatusCodes = setOf(429, 500, 502, 503, 504)
        )
        retryExecutor = RetryExecutor(config, metricsPort = null)
    }

    // ==================== Success Cases ====================

    @Test
    fun `should return result on first success`() = runTest {
        var callCount = 0

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                "success"
            },
            priority = Priority.P1
        )

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `should retry and succeed on second attempt`() = runTest {
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount == 1) {
                    throw ProviderError("Temporary error", providerStatus = 500)
                }
                "success"
            },
            priority = Priority.P1,
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        )

        assertEquals("success", result)
        assertEquals(2, callCount)
        assertEquals(listOf(1), retryAttempts)
    }

    @Test
    fun `should retry and succeed on third attempt`() = runTest {
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount < 3) {
                    throw ProviderError("Temporary error", providerStatus = 503)
                }
                "success"
            },
            priority = Priority.P2,
            onRetry = { attempt, _ -> retryAttempts.add(attempt) }
        )

        assertEquals("success", result)
        assertEquals(3, callCount)
        assertEquals(listOf(1, 2), retryAttempts)
    }

    // ==================== Failure Cases ====================

    @Test
    fun `should fail after max attempts exhausted`() = runTest {
        var callCount = 0

        try {
            retryExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw ProviderError("Persistent error", providerStatus = 500)
                },
                priority = Priority.P1
            )
            fail("Should have thrown ProviderError")
        } catch (e: ProviderError) {
            assertEquals("Persistent error", e.message)
            assertEquals(3, callCount) // Initial + 2 retries
        }
    }

    @Test
    fun `should not retry non-retryable status codes`() = runTest {
        var callCount = 0

        try {
            retryExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw ProviderError("Bad request", providerStatus = 400)
                },
                priority = Priority.P1
            )
            fail("Should have thrown ProviderError")
        } catch (e: ProviderError) {
            assertEquals("Bad request", e.message)
            assertEquals(400, e.providerStatus)
            assertEquals(1, callCount) // No retries
        }
    }

    @Test
    fun `should not retry 401 unauthorized`() = runTest {
        var callCount = 0

        try {
            retryExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw ProviderError("Unauthorized", providerStatus = 401)
                },
                priority = Priority.P1
            )
            fail("Should have thrown ProviderError")
        } catch (e: ProviderError) {
            assertEquals(401, e.providerStatus)
            assertEquals(1, callCount) // No retries
        }
    }

    // ==================== Retryable Status Codes ====================

    @Test
    fun `should retry 429 too many requests`() = runTest {
        var callCount = 0

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount == 1) {
                    throw ProviderError("Rate limited", providerStatus = 429)
                }
                "success"
            },
            priority = Priority.P1
        )

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `should retry 502 bad gateway`() = runTest {
        var callCount = 0

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount < 3) {
                    throw ProviderError("Bad gateway", providerStatus = 502)
                }
                "success"
            },
            priority = Priority.P1
        )

        assertEquals("success", result)
        assertEquals(3, callCount)
    }

    @Test
    fun `should retry 504 gateway timeout`() = runTest {
        var callCount = 0

        val result = retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount == 1) {
                    throw ProviderError("Gateway timeout", providerStatus = 504)
                }
                "success"
            },
            priority = Priority.P1
        )

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    // ==================== Disabled Retry ====================

    @Test
    fun `should not retry when disabled`() = runTest {
        val disabledConfig = RetryConfig.DISABLED
        val disabledExecutor = RetryExecutor(disabledConfig, metricsPort = null)
        var callCount = 0

        try {
            disabledExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw ProviderError("Error", providerStatus = 500)
                },
                priority = Priority.P1
            )
            fail("Should have thrown ProviderError")
        } catch (e: ProviderError) {
            assertEquals(1, callCount) // No retries when disabled
        }
    }

    @Test
    fun `should return immediately on success when disabled`() = runTest {
        val disabledConfig = RetryConfig.DISABLED
        val disabledExecutor = RetryExecutor(disabledConfig, metricsPort = null)
        var callCount = 0

        val result = disabledExecutor.executeWithRetry(
            operation = {
                callCount++
                "success"
            },
            priority = Priority.P1
        )

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    // ==================== Non-ProviderError Exceptions ====================

    @Test
    fun `should not retry non-ProviderError exceptions`() = runTest {
        var callCount = 0

        try {
            retryExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw IllegalArgumentException("Invalid argument")
                },
                priority = Priority.P1
            )
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid argument", e.message)
            assertEquals(1, callCount) // No retries
        }
    }

    @Test
    fun `should not retry NullPointerException`() = runTest {
        var callCount = 0

        try {
            retryExecutor.executeWithRetry<String>(
                operation = {
                    callCount++
                    throw NullPointerException()
                },
                priority = Priority.P1
            )
            fail("Should have thrown NullPointerException")
        } catch (e: NullPointerException) {
            assertEquals(1, callCount) // No retries
        }
    }

    // ==================== Callback Tests ====================

    @Test
    fun `should call onRetry callback for each retry`() = runTest {
        var callCount = 0
        val retryAttempts = mutableListOf<Int>()
        val retryErrors = mutableListOf<Throwable>()

        retryExecutor.executeWithRetry(
            operation = {
                callCount++
                if (callCount < 3) {
                    throw ProviderError("Error ${callCount}", providerStatus = 500)
                }
                "success"
            },
            priority = Priority.P1,
            onRetry = { attempt, error ->
                retryAttempts.add(attempt)
                retryErrors.add(error)
            }
        )

        assertEquals(2, retryAttempts.size)
        assertEquals(listOf(1, 2), retryAttempts)
        assertTrue(retryErrors.all { it is ProviderError })
    }

    // ==================== Configuration Validation ====================

    @Test
    fun `should validate maxAttempts at least 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryConfig(maxAttempts = 0)
        }
    }

    @Test
    fun `should validate initialDelayMs positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryConfig(initialDelayMs = 0)
        }
    }

    @Test
    fun `should validate maxDelayMs not less than initialDelayMs`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryConfig(initialDelayMs = 100, maxDelayMs = 50)
        }
    }

    @Test
    fun `should validate backoffMultiplier at least 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryConfig(backoffMultiplier = 0.5)
        }
    }

    @Test
    fun `isRetryable should return true for configured status codes`() {
        val config = RetryConfig(retryableStatusCodes = setOf(429, 500, 502, 503, 504))

        assertTrue(config.isRetryable(429))
        assertTrue(config.isRetryable(500))
        assertTrue(config.isRetryable(502))
        assertTrue(config.isRetryable(503))
        assertTrue(config.isRetryable(504))
    }

    @Test
    fun `isRetryable should return false for non-configured status codes`() {
        val config = RetryConfig(retryableStatusCodes = setOf(429, 500, 502, 503, 504))

        assertFalse(config.isRetryable(400))
        assertFalse(config.isRetryable(401))
        assertFalse(config.isRetryable(403))
        assertFalse(config.isRetryable(404))
    }
}
