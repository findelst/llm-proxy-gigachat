package ru.ddd.llmproxy.domain.model

/**
 * Value object representing a request priority level.
 * Lower level value means higher priority.
 */
enum class Priority(val level: Int, val value: String) {
    P1(1, "p1"),
    P2(2, "p2"),
    P3(3, "p3");

    companion object {
        /**
         * Resolves priority from string input.
         *
         * @param input Priority string (p1, p2, p3, highest, lowest, p0)
         * @param default Default priority if input is null
         * @return Resolved Priority
         * @throws IllegalArgumentException if input is invalid
         */
        fun resolve(input: String?, default: Priority = P2): Priority {
            if (input.isNullOrBlank()) return default

            return when (input.lowercase().trim()) {
                "p1", "highest", "p0" -> P1
                "p2" -> P2
                "p3", "lowest" -> P3
                else -> throw IllegalArgumentException("Invalid priority value: $input")
            }
        }

        /**
         * Safely resolves priority, returning default on invalid input.
         */
        fun resolveOrNull(input: String?, default: Priority = P2): Priority {
            return try {
                resolve(input, default)
            } catch (e: IllegalArgumentException) {
                default
            }
        }

        /**
         * Returns the highest priority (P1).
         */
        fun highest(): Priority = P1

        /**
         * Returns the lowest priority (P3).
         */
        fun lowest(): Priority = P3

        /**
         * All priorities sorted by level (highest first).
         */
        fun sortedByLevel(): List<Priority> = entries.sortedBy { it.level }
    }
}
