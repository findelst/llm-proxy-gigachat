package ru.ddd.llmproxy.infrastructure.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.Priority

private val log = KotlinLogging.logger {}

/**
 * Prometheus-based implementation of MetricsPort.
 *
 * Exposes metrics in Prometheus format via Spring Boot Actuator.
 */
@Component
class PrometheusMetrics(
    private val meterRegistry: MeterRegistry
) : MetricsPort {

    // Counters
    private val requestCounters = mutableMapOf<String, Counter>()
    private val overflowCounters = mutableMapOf<String, Counter>()

    // Histograms for latencies
    private val queueWaitTimers = mutableMapOf<String, Timer>()
    private val providerLatencyTimers = mutableMapOf<String, Timer>()

    // Gauges for queue state
    private val queueLengthGauges = mutableMapOf<String, Number>()
    private val inFlightGauges = mutableMapOf<String, Number>()

    // Cache metrics
    private var cacheHits: Long = 0
    private var cacheMisses: Long = 0

    companion object {
        const val REQUESTS_TOTAL = "llm_proxy_requests_total"
        const val QUEUE_OVERFLOW = "llm_proxy_queue_overflow_total"
        const val QUEUE_WAIT = "llm_proxy_queue_wait_seconds"
        const val PROVIDER_LATENCY = "llm_proxy_provider_latency_seconds"
        const val QUEUE_LENGTH = "llm_proxy_queue_length"
        const val IN_FLIGHT = "llm_proxy_in_flight"
        const val CACHE_HITS = "llm_proxy_cache_hits_total"
        const val CACHE_MISSES = "llm_proxy_cache_misses_total"
    }

    override fun recordRequest(
        endpoint: String,
        priority: Priority,
        status: String,
        errorCode: String?
    ) {
        val key = "$endpoint:${priority.value}:$status:${errorCode ?: "none"}"

        val counter = requestCounters.getOrPut(key) {
            Counter.builder(REQUESTS_TOTAL)
                .description("Total number of LLM proxy requests")
                .tag("endpoint", endpoint)
                .tag("priority", priority.value)
                .tag("status", status)
                .tag("error_code", errorCode ?: "none")
                .register(meterRegistry)
        }

        counter.increment()
        log.trace { "Recorded request: endpoint=$endpoint, priority=${priority.value}, status=$status" }
    }

    override fun recordQueueOverflow(priority: Priority) {
        val key = priority.value

        val counter = overflowCounters.getOrPut(key) {
            Counter.builder(QUEUE_OVERFLOW)
                .description("Total number of queue overflow events")
                .tag("priority", priority.value)
                .register(meterRegistry)
        }

        counter.increment()
        log.warn { "Queue overflow for priority: ${priority.value}" }
    }

    override fun recordQueueWait(endpoint: String, priority: Priority, waitMs: Long) {
        val key = "$endpoint:${priority.value}"

        val timer = queueWaitTimers.getOrPut(key) {
            Timer.builder(QUEUE_WAIT)
                .description("Time spent waiting in queue")
                .tag("endpoint", endpoint)
                .tag("priority", priority.value)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
        }

        timer.record(java.time.Duration.ofMillis(waitMs))
        log.trace { "Recorded queue wait: ${waitMs}ms for priority ${priority.value}" }
    }

    override fun recordProviderLatency(endpoint: String, priority: Priority, latencyMs: Long) {
        val key = "$endpoint:${priority.value}"

        val timer = providerLatencyTimers.getOrPut(key) {
            Timer.builder(PROVIDER_LATENCY)
                .description("Time spent waiting for provider response")
                .tag("endpoint", endpoint)
                .tag("priority", priority.value)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
        }

        timer.record(java.time.Duration.ofMillis(latencyMs))
        log.trace { "Recorded provider latency: ${latencyMs}ms for priority ${priority.value}" }
    }

    override fun setQueueLength(priority: Priority, length: Int) {
        val key = priority.value
        queueLengthGauges[key] = length

        // Re-register gauge with updated value
        Gauge.builder(QUEUE_LENGTH) { queueLengthGauges[key]?.toDouble() ?: 0.0 }
            .description("Current queue length")
            .tag("priority", priority.value)
            .register(meterRegistry)
    }

    override fun setInFlight(priority: Priority, count: Int) {
        val key = priority.value
        inFlightGauges[key] = count

        // Re-register gauge with updated value
        Gauge.builder(IN_FLIGHT) { inFlightGauges[key]?.toDouble() ?: 0.0 }
            .description("Current in-flight requests")
            .tag("priority", priority.value)
            .register(meterRegistry)
    }

    override fun incrementInFlight(priority: Priority) {
        val key = priority.value
        val current = inFlightGauges[key]?.toInt() ?: 0
        inFlightGauges[key] = current + 1
    }

    override fun decrementInFlight(priority: Priority) {
        val key = priority.value
        val current = inFlightGauges[key]?.toInt() ?: 0
        inFlightGauges[key] = (current - 1).coerceAtLeast(0)
    }

    override fun recordCacheHit() {
        cacheHits++
        Counter.builder(CACHE_HITS)
            .description("Total number of cache hits")
            .register(meterRegistry)
            .increment()
    }

    override fun recordCacheMiss() {
        cacheMisses++
        Counter.builder(CACHE_MISSES)
            .description("Total number of cache misses")
            .register(meterRegistry)
            .increment()
    }
}
