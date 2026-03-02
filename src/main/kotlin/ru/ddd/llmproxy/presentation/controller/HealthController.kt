package ru.ddd.llmproxy.presentation.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for health check endpoint.
 *
 * Provides a simple health check for load balancers and monitoring.
 */
@RestController
class HealthController(
    @Value("\${spring.profiles.active:development}")
    private val activeProfile: String
) {

    /**
     * Health check endpoint.
     *
     * Returns basic health status and environment.
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        val env = when (activeProfile) {
            "prod", "production" -> "production"
            "staging" -> "staging"
            else -> "development"
        }

        return ResponseEntity.ok(
            HealthResponse(
                status = "ok",
                env = env
            )
        )
    }

    /**
     * Health response data class.
     */
    data class HealthResponse(
        val status: String,
        val env: String
    )
}
