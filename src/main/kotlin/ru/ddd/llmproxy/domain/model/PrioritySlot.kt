package ru.ddd.llmproxy.domain.model

/**
 * Configuration value object representing a priority slot with concurrency limit.
 *
 * @param name Name of the priority (e.g., "p1", "p2", "p3")
 * @param maxConcurrency Maximum number of concurrent requests for this priority
 */
data class PrioritySlot(
    val name: String,
    val maxConcurrency: Int
) {
    init {
        require(name.isNotBlank()) { "Priority slot name must not be blank" }
        require(maxConcurrency > 0) { "Max concurrency must be positive" }
    }

    /**
     * Resolves the Priority enum for this slot.
     */
    fun toPriority(): Priority = Priority.resolve(name)

    companion object {
        /**
         * Creates PrioritySlot from a map entry.
         */
        fun fromEntry(entry: Map.Entry<String, Int>): PrioritySlot =
            PrioritySlot(name = entry.key, maxConcurrency = entry.value)

        /**
         * Creates default priority slots configuration.
         */
        fun defaults(): List<PrioritySlot> = listOf(
            PrioritySlot("p1", 2),
            PrioritySlot("p2", 1),
            PrioritySlot("p3", 1)
        )
    }
}
