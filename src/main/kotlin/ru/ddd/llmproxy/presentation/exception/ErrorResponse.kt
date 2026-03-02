package ru.ddd.llmproxy.presentation.exception

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Standard error response format for LLM Proxy API.
 *
 * All error responses follow this consistent structure for easy handling by clients.
 */
data class ErrorResponse(
    val error: ErrorDetail
) {
    data class ErrorDetail(
        val code: String,
        @JsonProperty("http_status")
        val httpStatus: Int,
        val message: String,
        @JsonProperty("request_id")
        val requestId: String
    ) {
        companion object {
            /**
             * Creates an error detail for invalid request errors.
             */
            fun invalidRequest(message: String, requestId: String): ErrorDetail =
                ErrorDetail(
                    code = "invalid_request",
                    httpStatus = 400,
                    message = message,
                    requestId = requestId
                )

            /**
             * Creates an error detail for queue overflow errors.
             */
            fun queueOverflow(requestId: String): ErrorDetail =
                ErrorDetail(
                    code = "queue_overflow",
                    httpStatus = 429,
                    message = "Queue is full. Try again later.",
                    requestId = requestId
                )

            /**
             * Creates an error detail for provider errors.
             */
            fun providerError(message: String, requestId: String, httpStatus: Int = 500): ErrorDetail =
                ErrorDetail(
                    code = "provider_error",
                    httpStatus = httpStatus,
                    message = message,
                    requestId = requestId
                )

            /**
             * Creates an error detail for internal server errors.
             */
            fun internal(requestId: String, message: String = "An unexpected error occurred"): ErrorDetail =
                ErrorDetail(
                    code = "internal",
                    httpStatus = 500,
                    message = message,
                    requestId = requestId
                )
        }
    }

    companion object {
        /**
         * Creates a complete error response.
         */
        fun of(code: String, httpStatus: Int, message: String, requestId: String): ErrorResponse =
            ErrorResponse(
                ErrorDetail(
                    code = code,
                    httpStatus = httpStatus,
                    message = message,
                    requestId = requestId
                )
            )

        /**
         * Creates an invalid request error response.
         */
        fun invalidRequest(message: String, requestId: String): ErrorResponse =
            ErrorResponse(ErrorDetail.invalidRequest(message, requestId))

        /**
         * Creates a queue overflow error response.
         */
        fun queueOverflow(requestId: String): ErrorResponse =
            ErrorResponse(ErrorDetail.queueOverflow(requestId))

        /**
         * Creates a provider error response.
         */
        fun providerError(message: String, requestId: String, httpStatus: Int = 500): ErrorResponse =
            ErrorResponse(ErrorDetail.providerError(message, requestId, httpStatus))

        /**
         * Creates an internal server error response.
         */
        fun internal(requestId: String, message: String = "An unexpected error occurred"): ErrorResponse =
            ErrorResponse(ErrorDetail.internal(requestId, message))
    }
}
