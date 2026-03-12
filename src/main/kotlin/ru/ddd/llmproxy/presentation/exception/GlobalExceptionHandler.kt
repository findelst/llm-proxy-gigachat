package ru.ddd.llmproxy.presentation.exception

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import ru.ddd.llmproxy.domain.model.InvalidRequestError
import ru.ddd.llmproxy.domain.model.LlmProxyException
import ru.ddd.llmproxy.domain.model.ProviderError
import ru.ddd.llmproxy.domain.model.QueueOverflowError
import ru.ddd.llmproxy.domain.model.QueueTimeoutError
import ru.ddd.llmproxy.domain.model.PreemptionError
import ru.ddd.llmproxy.domain.model.P3ThrottledError
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Global exception handler for LLM Proxy.
 *
 * Converts all exceptions to standardized error responses.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * Handles LLM Proxy specific exceptions.
     */
    @ExceptionHandler(LlmProxyException::class)
    fun handleLlmProxyException(ex: LlmProxyException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "LLM Proxy error: ${ex.code} - ${ex.message} (requestId=$requestId)" }

        return ResponseEntity
            .status(ex.httpStatus)
            .body(ErrorResponse.of(
                code = ex.code,
                httpStatus = ex.httpStatus,
                message = ex.message ?: "Unknown error",
                requestId = requestId
            ))
    }

    /**
     * Handles invalid request errors.
     */
    @ExceptionHandler(InvalidRequestError::class)
    fun handleInvalidRequestError(ex: InvalidRequestError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "Invalid request: ${ex.message} (requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.invalidRequest(ex.message ?: "Invalid request", requestId))
    }

    /**
     * Handles queue overflow errors.
     */
    @ExceptionHandler(QueueOverflowError::class)
    fun handleQueueOverflowError(ex: QueueOverflowError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "Queue overflow (priority=${ex.priority}, requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.queueOverflow(requestId))
    }

    /**
     * Handles queue timeout errors.
     */
    @ExceptionHandler(QueueTimeoutError::class)
    fun handleQueueTimeoutError(ex: QueueTimeoutError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "Queue timeout (priority=${ex.priority}, waitTimeMs=${ex.waitTimeMs}, requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(ErrorResponse.queueTimeout(
                requestId = requestId,
                priority = ex.priority?.value,
                waitTimeMs = ex.waitTimeMs
            ))
    }

    /**
     * Handles preemption errors (503 Service Unavailable).
     */
    @ExceptionHandler(PreemptionError::class)
    fun handlePreemptionError(ex: PreemptionError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.info {
            "Request preempted: ${ex.priority.value} preempted by ${ex.preemptedBy.value} " +
            "after ${ex.elapsedMs}ms (requestId=$requestId)"
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.preemption(
                requestId = requestId,
                priority = ex.priority.value,
                preemptedBy = ex.preemptedBy.value,
                elapsedMs = ex.elapsedMs,
                queuePosition = ex.queuePosition
            ))
    }

    /**
     * Handles P3 throttling errors (429 Too Many Requests).
     */
    @ExceptionHandler(P3ThrottledError::class)
    fun handleP3ThrottledError(ex: P3ThrottledError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.info {
            "P3 request throttled: queuePosition=${ex.queuePosition}, " +
            "estimatedWaitSeconds=${ex.estimatedWaitSeconds} (requestId=$requestId)"
        }

        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.p3Throttled(
                requestId = requestId,
                queuePosition = ex.queuePosition,
                estimatedWaitSeconds = ex.estimatedWaitSeconds
            ))
    }

    /**
     * Handles provider errors.
     */
    @ExceptionHandler(ProviderError::class)
    fun handleProviderError(ex: ProviderError, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.error { "Provider error: ${ex.message} (status=${ex.providerStatus}, requestId=$requestId)" }

        return ResponseEntity
            .status(ex.httpStatus)
            .body(ErrorResponse.providerError(ex.message ?: "Provider error", requestId, ex.httpStatus))
    }

    /**
     * Handles JSON parsing errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "Invalid JSON: ${ex.message} (requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.invalidRequest("Invalid JSON payload", requestId))
    }

    /**
     * Handles validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)
        val errors = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }

        log.warn { "Validation error: $errors (requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.invalidRequest(errors, requestId))
    }

    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.warn { "Invalid argument: ${ex.message} (requestId=$requestId)" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.invalidRequest(ex.message ?: "Invalid argument", requestId))
    }

    /**
     * Handles all other unexpected exceptions.
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        val requestId = getRequestId(request)

        log.error(ex) { "Unexpected error (requestId=$requestId): ${ex.message}" }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.internal(requestId, ex.message ?: "Unknown error"))
    }

    /**
     * Extracts or generates a request ID from the request context.
     */
    private fun getRequestId(request: WebRequest): String {
        // Try to get from request header (set by RequestIdMiddleware)
        return request.getHeader("x-request-id")
            ?: request.getHeader("x-llm-proxy-request-id")
            ?: UUID.randomUUID().toString()
    }
}
