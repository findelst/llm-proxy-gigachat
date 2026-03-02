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
