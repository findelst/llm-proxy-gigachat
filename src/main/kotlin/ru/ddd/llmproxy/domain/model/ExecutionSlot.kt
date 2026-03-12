package ru.ddd.llmproxy.domain.model

/**
 * Entity representing an execution slot with its current allocation.
 *
 * ExecutionSlots are the basic unit of runtime concurrency control. Each slot can hold
 * at most one running request at a time. This is distinct from PrioritySlot which
 * represents configuration limits.
 *
 * @param slotId Unique identifier for this slot
 * @param currentRequest The request currently occupying this slot, if any
 */
data class ExecutionSlot(
    val slotId: Int,
    var currentRequest: RunningRequest? = null
) {
    /**
     * Whether this slot is currently occupied by a request.
     */
    val isOccupied: Boolean
        get() = currentRequest != null

    /**
     * The priority of the current request, or null if slot is free.
     */
    val currentPriority: Priority?
        get() = currentRequest?.priority

    /**
     * Allocates this slot to a request.
     *
     * @param request The request to allocate to this slot
     * @throws IllegalStateException if slot is already occupied
     */
    fun allocate(request: RunningRequest) {
        require(!isOccupied) { "Slot $slotId is already occupied" }
        currentRequest = request
    }

    /**
     * Releases this slot and returns the previously running request.
     *
     * @return The request that was occupying this slot, or null if it was free
     */
    fun release(): RunningRequest? {
        val request = currentRequest
        currentRequest = null
        return request
    }

    /**
     * Whether this slot can be preempted by the given priority.
     * A slot can be preempted if:
     * - It is occupied
     * - The current priority is lower than the requesting priority
     * - The requesting priority has preemption enabled
     */
    fun canBePreemptedBy(requestingPriority: Priority, config: ConcurrencyConfig): Boolean {
        val current = currentRequest ?: return false
        return config.canPreempt(requestingPriority) &&
               current.priority.level > requestingPriority.level
    }
}
