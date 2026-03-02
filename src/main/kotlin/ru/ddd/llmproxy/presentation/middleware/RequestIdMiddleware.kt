package ru.ddd.llmproxy.presentation.middleware

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Middleware that generates or echoes a request ID for tracing.
 *
 * Request ID resolution:
 * 1. Use x-request-id header if present
 * 2. Generate a new UUID otherwise
 *
 * The request ID is added to:
 * - Response headers (x-request-id, x-llm-proxy-request-id)
 * - MDC for logging correlation
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdMiddleware : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = resolveRequestId(request)

        // Add to MDC for logging
        MDC.put("x-request-id", requestId)

        // Set response headers
        response.setHeader("x-request-id", requestId)
        response.setHeader("x-llm-proxy-request-id", requestId)

        // Wrap request to expose request ID to controllers
        val wrappedRequest = RequestIdRequestWrapper(request, requestId)

        try {
            log.debug { "Processing request $requestId: ${request.method} ${request.requestURI}" }
            filterChain.doFilter(wrappedRequest, response)
        } finally {
            MDC.remove("x-request-id")
            log.debug { "Completed request $requestId" }
        }
    }

    /**
     * Resolves the request ID from header or generates a new one.
     */
    private fun resolveRequestId(request: HttpServletRequest): String {
        return request.getHeader("x-request-id")
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
    }

    /**
     * Request wrapper that exposes the request ID.
     */
    class RequestIdRequestWrapper(
        request: HttpServletRequest,
        private val proxyRequestId: String
    ) : jakarta.servlet.http.HttpServletRequestWrapper(request) {

        fun getProxyRequestId(): String = proxyRequestId
    }
}
