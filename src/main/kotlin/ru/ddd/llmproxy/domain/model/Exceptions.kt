package ru.ddd.llmproxy.domain.model

/**
 * Base exception for all LLM Proxy errors.
 */
sealed class LlmProxyException(
    val code: String,
    val httpStatus: Int,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception for invalid request errors (400 Bad Request).
 */
class InvalidRequestError(
    message: String,
    cause: Throwable? = null
) : LlmProxyException(
    code = "invalid_request",
    httpStatus = 400,
    message = message,
    cause = cause
)

/**
 * Exception for queue overflow errors (429 Too Many Requests).
 */
class QueueOverflowError(
    message: String = "Queue is full. Try again later.",
    val priority: Priority? = null
) : LlmProxyException(
    code = "queue_overflow",
    httpStatus = 429,
    message = message
)

/**
 * Exception for provider errors (500/502).
 */
class ProviderError(
    message: String,
    val providerStatus: Int = 500,
    cause: Throwable? = null
) : LlmProxyException(
    code = "provider_error",
    httpStatus = if (providerStatus in 500..599) providerStatus else 500,
    message = message,
    cause = cause
)

/**
 * Exception for internal server errors (500).
 */
class InternalError(
    message: String = "An unexpected error occurred",
    cause: Throwable? = null
) : LlmProxyException(
    code = "internal",
    httpStatus = 500,
    message = message,
    cause = cause
)

/**
 * Exception for queue timeout errors (408 Request Timeout).
 * Thrown when a request waits in queue longer than the configured timeout.
 */
class QueueTimeoutError(
    message: String = "Request timed out waiting in queue",
    val priority: Priority? = null,
    val waitTimeMs: Long? = null
) : LlmProxyException(
    code = "queue_timeout",
    httpStatus = 408,
    message = message
)

/**
 * Exception for preemption errors (503 Service Unavailable).
 * Thrown when a request is preempted by a higher priority request.
 */
class PreemptionError(
    message: String = "Request was preempted by higher priority",
    val priority: Priority,
    val preemptedBy: Priority,
    val elapsedMs: Long,
    val queuePosition: Int? = null
) : LlmProxyException(
    code = "request_preempted",
    httpStatus = 503,
    message = message
)

/**
 * Exception for P3 throttling (429 Too Many Requests).
 * Thrown when P3 request is blocked due to max concurrent limit.
 */
class P3ThrottledError(
    message: String = "Low priority requests are limited to 1 concurrent execution",
    val queuePosition: Int,
    val estimatedWaitSeconds: Long
) : LlmProxyException(
    code = "p3_throttled",
    httpStatus = 429,
    message = message
)
