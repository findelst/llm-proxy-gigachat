package ru.ddd.llmproxy.application.service

import org.springframework.stereotype.Service
import ru.ddd.llmproxy.domain.model.Priority
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties

/**
 * Service for resolving request priority from various sources.
 *
 * Priority resolution order:
 * 1. X-Priority header
 * 2. priority field in request body
 * 3. Default priority from configuration
 */
@Service
class PriorityResolver(
    private val properties: LlmProxyProperties
) {
    /**
     * Resolves priority from header and body values.
     *
     * @param headerPriority Value from X-Priority header (may be null)
     * @param bodyPriority Value from request body priority field (may be null)
     * @return Resolved Priority
     * @throws IllegalArgumentException if priority value is invalid
     */
    fun resolve(headerPriority: String?, bodyPriority: String?): Priority {
        // Header takes precedence over body
        val priorityValue = headerPriority ?: bodyPriority

        return Priority.resolve(priorityValue, getDefaultPriority())
    }

    /**
     * Safely resolves priority, returning default on any error.
     *
     * @param headerPriority Value from X-Priority header (may be null)
     * @param bodyPriority Value from request body priority field (may be null)
     * @return Resolved Priority (never throws)
     */
    fun resolveSafe(headerPriority: String?, bodyPriority: String?): Priority {
        return try {
            resolve(headerPriority, bodyPriority)
        } catch (e: IllegalArgumentException) {
            getDefaultPriority()
        }
    }

    /**
     * Returns the default priority from configuration.
     */
    fun getDefaultPriority(): Priority {
        return Priority.resolve(properties.queue.defaultPriority)
    }
}
