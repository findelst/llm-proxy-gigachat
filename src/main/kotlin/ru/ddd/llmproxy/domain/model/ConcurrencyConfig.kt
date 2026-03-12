package ru.ddd.llmproxy.domain.model

/**
 * Configuration value object for concurrency limits and preemption behavior.
 *
 * Defines the constraints for concurrent request execution per priority level.
 */
data class ConcurrencyConfig(
    val maxConcurrent: Int = 3,      // Global max concurrent requests
    val p1MaxThreads: Int = 2,       // Max slots for P1 priority
    val p3MaxThreads: Int = 1,       // Max slots for P3 priority
    val preemptionEnabled: Boolean = true // Enable/disable preemption
) {
    init {
        require(maxConcurrent > 0) { "maxConcurrent must be > 0" }
        require(p1MaxThreads in 1..maxConcurrent) { "p1MaxThreads must be in 1..maxConcurrent" }
        require(p3MaxThreads in 1..maxConcurrent) { "p3MaxThreads must be in 1..maxConcurrent" }
    }

    /**
     * Returns max threads allowed for a priority.
     * P2 has no per-priority limit (uses remaining global capacity).
     */
    fun maxThreadsFor(priority: Priority): Int = when (priority) {
        Priority.P1 -> p1MaxThreads
        Priority.P2 -> maxConcurrent // P2 can use all slots
        Priority.P3 -> p3MaxThreads
    }

    /**
     * Returns whether this priority can preempt others.
     * Only P1 can preempt when preemption is enabled.
     */
    fun canPreempt(priority: Priority): Boolean =
        preemptionEnabled && priority == Priority.P1

    /**
     * Returns priorities that can be preempted by the given priority.
     */
    fun preemptibleBy(priority: Priority): Set<Priority> =
        if (canPreempt(priority)) {
            Priority.entries.filter { it.level > priority.level }.toSet()
        } else {
            emptySet()
        }

    companion object {
        /**
         * Creates ConcurrencyConfig from QueueConfig.
         */
        fun from(queueConfig: ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties.QueueConfig): ConcurrencyConfig =
            ConcurrencyConfig(
                maxConcurrent = queueConfig.maxConcurrent,
                p1MaxThreads = queueConfig.p1MaxThreads,
                p3MaxThreads = queueConfig.p3MaxThreads,
                preemptionEnabled = queueConfig.preemptionEnabled
            )
    }
}
