package ru.ddd.llmproxy.presentation.controller

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for exposing Prometheus metrics.
 *
 * Note: Spring Boot Actuator already exposes /actuator/prometheus,
 * but this provides an additional /metrics endpoint for convenience.
 */
@RestController
class MetricsController {

    @Autowired(required = false)
    private var prometheusMeterRegistry: PrometheusMeterRegistry? = null

    /**
     * Returns metrics in Prometheus text format.
     */
    @GetMapping("/metrics", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun metrics(): ResponseEntity<String> {
        val scrape = prometheusMeterRegistry?.scrape() ?: "# Prometheus registry not available"
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(scrape)
    }
}
