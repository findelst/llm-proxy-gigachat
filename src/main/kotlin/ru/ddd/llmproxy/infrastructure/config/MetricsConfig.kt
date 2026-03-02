package ru.ddd.llmproxy.infrastructure.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Micrometer metrics with Prometheus.
 */
@Configuration
class MetricsConfig {

    /**
     * Customizes the meter registry with common tags.
     */
    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags(
                "application", "llm-proxy"
            )
        }
    }
}
