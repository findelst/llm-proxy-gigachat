package ru.ddd.llmproxy.domain.model

import java.time.Instant

/**
 * Domain event recording when a request is preempted.
 *
 * Preemption occurs when a higher priority request needs execution slots
 * that are occupied by lower priority requests.
 *
 * @param preemptedRequestId ID of the request that was preempted
 * @param preemptedPriority Priority of the preempted request
 * @param preemptedByPriority Priority that caused the preemption
 * @param timestamp When the preemption occurred
 * @param reason Human-readable reason for preemption
 * @param elapsedMs How long the preempted request had been running
 */
data class PreemptionEvent(
    val preemptedRequestId: String,
    val preemptedPriority: Priority,
    val preemptedByPriority: Priority,
    val timestamp: Instant = Instant.now(),
    val reason: String,
    val elapsedMs: Long
) {
    init {
        require(preemptedPriority.level > preemptedByPriority.level) {
            "Can only preempt lower priorities: ${preemptedPriority.value} cannot be preempted by ${preemptedByPriority.value}"
        }
    }

    /**
     * Human-readable description of this event.
     */
    val description: String
        get() = "Request $preemptedRequestId (${preemptedPriority.value}) preempted by " +
                "${preemptedByPriority.value} after ${elapsedMs}ms: $reason"

    companion object {
        /**
         * Creates a preemption event from a running request.
         */
        fun from(
            request: RunningRequest,
            preemptedBy: Priority,
            reason: String = "Preempted by higher priority request"
        ): PreemptionEvent = PreemptionEvent(
            preemptedRequestId = request.requestId,
            preemptedPriority = request.priority,
            preemptedByPriority = preemptedBy,
            reason = reason,
            elapsedMs = request.elapsedTimeMs
        )
    }
}
