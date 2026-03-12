package ru.ddd.llmproxy.domain.model

/**
 * Read model providing a snapshot of current concurrency state.
 *
 * Used for monitoring and decision-making about slot allocation.
 *
 * @param totalRunning Total number of requests currently running
 * @param p1Running Number of P1 requests currently running
 * @param p2Running Number of P2 requests currently running
 * @param p3Running Number of P3 requests currently running
 * @param availableSlots Number of slots available for new requests
 * @param p1Available Whether P1 can accept more requests (under p1MaxThreads)
 * @param p3Available Whether P3 can accept more requests (under p3MaxThreads)
 */
data class ConcurrencyStats(
    val totalRunning: Int,
    val p1Running: Int,
    val p2Running: Int,
    val p3Running: Int,
    val availableSlots: Int,
    val p1Available: Boolean,
    val p3Available: Boolean
) {
    /**
     * Whether any slots are available for new requests.
     */
    val hasCapacity: Boolean
        get() = availableSlots > 0

    /**
     * Whether a specific priority can accept new requests.
     * P2 has no per-priority limit, so it just needs available slots.
     */
    fun canAccept(priority: Priority): Boolean = when (priority) {
        Priority.P1 -> p1Available && hasCapacity
        Priority.P2 -> hasCapacity
        Priority.P3 -> p3Available && hasCapacity
    }

    /**
     * Number of running requests for a specific priority.
     */
    fun runningFor(priority: Priority): Int = when (priority) {
        Priority.P1 -> p1Running
        Priority.P2 -> p2Running
        Priority.P3 -> p3Running
    }

    companion object {
        /**
         * Creates ConcurrencyStats from current slot state.
         */
        fun from(
            slots: List<ExecutionSlot>,
            config: ConcurrencyConfig
        ): ConcurrencyStats {
            val running = slots.mapNotNull { it.currentRequest }
            val byPriority = running.groupingBy { it.priority }.eachCount()

            val p1Running = byPriority[Priority.P1] ?: 0
            val p2Running = byPriority[Priority.P2] ?: 0
            val p3Running = byPriority[Priority.P3] ?: 0

            return ConcurrencyStats(
                totalRunning = running.size,
                p1Running = p1Running,
                p2Running = p2Running,
                p3Running = p3Running,
                availableSlots = config.maxConcurrent - running.size,
                p1Available = p1Running < config.p1MaxThreads,
                p3Available = p3Running < config.p3MaxThreads
            )
        }

        /**
         * Empty stats with no running requests.
         */
        fun empty(config: ConcurrencyConfig): ConcurrencyStats = ConcurrencyStats(
            totalRunning = 0,
            p1Running = 0,
            p2Running = 0,
            p3Running = 0,
            availableSlots = config.maxConcurrent,
            p1Available = true,
            p3Available = true
        )
    }
}
