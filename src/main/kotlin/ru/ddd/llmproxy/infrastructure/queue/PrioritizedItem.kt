package ru.ddd.llmproxy.infrastructure.queue

/**
 * A wrapper that adds priority and sequence information to items
 * for use in a priority queue.
 *
 * Items are ordered first by priority (lower value = higher priority),
 * then by sequence number for FIFO ordering within the same priority.
 *
 * @param T The type of item being prioritized
 * @param item The actual item to be processed
 * @param priority The priority level (lower = higher priority)
 * @param sequenceNumber The sequence number for FIFO ordering within priority
 */
data class PrioritizedItem<T>(
    val item: T,
    val priority: Int,
    val sequenceNumber: Long
) : Comparable<PrioritizedItem<T>> {

    override fun compareTo(other: PrioritizedItem<T>): Int {
        val priorityCompare = this.priority.compareTo(other.priority)
        return if (priorityCompare != 0) {
            priorityCompare
        } else {
            this.sequenceNumber.compareTo(other.sequenceNumber)
        }
    }
}
