# Data Model: Priority Proxy Service

**Feature**: 007-priority-proxy-service
**Date**: 2026-03-12

## Domain Entities

### 1. ConcurrencyConfig (Value Object)

Configuration for concurrency limits and preemption behavior.

```kotlin
data class ConcurrencyConfig(
    val maxConcurrent: Int = 3,      // Global max concurrent requests
    val p1MaxThreads: Int = 2,       // Max slots for P1 priority
    val p3MaxThreads: Int = 1,       // Max slots for P3 priority
    val preemptionEnabled: Boolean = true // Enable/disable preemption
) {
    init {
        require(maxConcurrent > 0) { "maxConcurrent must be > 0" }
        require(p1MaxThreads in 1..maxConcurrent) { "p1MaxThreads must be 1..maxConcurrent" }
        require(p3MaxThreads in 1..maxConcurrent) { "p3MaxThreads must be 1..maxConcurrent" }
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
}
```

### 2. PrioritySlot (Entity)

Represents an execution slot with its current allocation.

```kotlin
data class PrioritySlot(
    val slotId: Int,
    var currentRequest: RunningRequest? = null
) {
    val isOccupied: Boolean get() = currentRequest != null
    val currentPriority: Priority? get() = currentRequest?.priority

    fun allocate(request: RunningRequest) {
        require(!isOccupied) { "Slot $slotId is already occupied" }
        currentRequest = request
    }

    fun release(): RunningRequest? {
        val request = currentRequest
        currentRequest = null
        return request
    }
}
```

### 3. RunningRequest (Entity)

Represents a request currently being processed.

```kotlin
data class RunningRequest(
    val requestId: String,
    val priority: Priority,
    val job: Job,
    val startTime: Instant = Instant.now(),
    val queuedAt: Instant
) {
    val elapsedTimeMs: Long
        get() = Duration.between(startTime, Instant.now()).toMillis()

    val queueWaitMs: Long
        get() = Duration.between(queuedAt, startTime).toMillis()

    fun cancel(reason: String) {
        job.cancel(CancellationException(reason))
    }

    val isActive: Boolean
        get() = job.isActive
}
```

### 4. PreemptionEvent (Domain Event)

Records when a request is preempted.

```kotlin
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
            "Can only preempt lower priorities"
        }
    }
}
```

### 5. ConcurrencyStats (Read Model)

Snapshot of current concurrency state.

```kotlin
data class ConcurrencyStats(
    val totalRunning: Int,
    val p1Running: Int,
    val p2Running: Int,
    val p3Running: Int,
    val availableSlots: Int,
    val p1Available: Boolean,
    val p3Available: Boolean
) {
    companion object {
        fun from(
            slots: List<PrioritySlot>,
            config: ConcurrencyConfig
        ): ConcurrencyStats {
            val running = slots.mapNotNull { it.currentRequest }
            val byPriority = running.groupingBy { it.priority }.eachCount()

            return ConcurrencyStats(
                totalRunning = running.size,
                p1Running = byPriority[Priority.P1] ?: 0,
                p2Running = byPriority[Priority.P2] ?: 0,
                p3Running = byPriority[Priority.P3] ?: 0,
                availableSlots = config.maxConcurrent - running.size,
                p1Available = (byPriority[Priority.P1] ?: 0) < config.p1MaxThreads,
                p3Available = (byPriority[Priority.P3] ?: 0) < config.p3MaxThreads
            )
        }
    }
}
```

## Relationships

```
┌─────────────────────┐
│  ConcurrencyConfig  │
│  (Value Object)     │
└──────────┬──────────┘
           │ configures
           ▼
┌─────────────────────┐     contains     ┌─────────────────┐
│ ConcurrencyController│──────────────▶│  PrioritySlot   │
│    (Service)        │                 │   (Entity)      │
└──────────┬──────────┘                 └────────┬────────┘
           │ manages                             │ holds
           ▼                                     ▼
┌─────────────────────┐                 ┌─────────────────┐
│   RunningRequest    │                 │  RunningRequest │
│     (Entity)        │                 │    (Entity)     │
└─────────────────────┘                 └─────────────────┘
           │
           │ may trigger
           ▼
┌─────────────────────┐
│  PreemptionEvent    │
│   (Domain Event)    │
└─────────────────────┘
```

## State Transitions

### Request Lifecycle

```
┌──────────┐  enqueue   ┌───────────┐  dispatch  ┌──────────┐
│  ARRIVED │──────────▶│  QUEUED   │──────────▶│ RUNNING  │
└──────────┘            └─────┬─────┘            └────┬─────┘
                              │                       │
                              │ timeout               │ complete
                              ▼                       ▼
                        ┌──────────┐           ┌───────────┐
                        │ TIMEOUT  │           │ COMPLETED │
                        └──────────┘           └───────────┘
                                                      │
                        ┌──────────┐                  │
                        │ PREEMPTED │◀─────────────────┘ preemption
                        └──────────┘
```

### Slot Lifecycle

```
┌──────────┐  allocate  ┌───────────┐  release  ┌──────────┐
│  FREE    │──────────▶│ OCCUPIED  │──────────▶│  FREE    │
└──────────┘            └───────────┘           └──────────┘
```

## Validation Rules

| Entity | Rule |
|--------|------|
| ConcurrencyConfig | maxConcurrent > 0 |
| ConcurrencyConfig | p1MaxThreads in 1..maxConcurrent |
| ConcurrencyConfig | p3MaxThreads in 1..maxConcurrent |
| PreemptionEvent | preemptedPriority.level > preemptedByPriority.level |
| PrioritySlot | Cannot allocate if already occupied |
| RunningRequest | Job must be active at creation |

## Existing Entities (No Changes Required)

| Entity | Location | Notes |
|--------|----------|-------|
| Priority | domain/model/Priority.kt | P1, P2, P3 enum - unchanged |
| QueuedRequest | domain/model/QueuedRequest.kt | May need preemption handling |
| QueueMetrics | domain/model/QueueMetrics.kt | Unchanged |
