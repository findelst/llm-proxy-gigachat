# Research: Priority Proxy Service with Preemption

**Feature**: 007-priority-proxy-service
**Date**: 2026-03-12

## Research Questions

### 1. Coroutine Cancellation for Preemption

**Decision**: Use Kotlin coroutine cancellation mechanism (Job.cancel()) for preemption

**Rationale**:
- Cooperative cancellation is the idiomatic Kotlin way
- Running requests can complete gracefully if they check isActive
- CompletableDeferred can be completed exceptionally with CancellationException
- Works naturally with existing CoroutinePriorityQueue architecture

**Alternatives Considered**:
- Thread interruption: Not applicable to coroutines
- Timeout-based cancellation: Less precise, doesn't guarantee immediate preemption
- External process kill: Too destructive, no graceful handling

**Implementation Pattern**:
```kotlin
// Preempt running request
runningRequest.deferred.cancel(CancellationException("Preempted by higher priority"))

// Check in processor
if (!currentCoroutineContext().isActive) {
    throw CancellationException("Request was preempted")
}
```

### 2. Slot Allocation Strategy

**Decision**: Use atomic counters with Semaphore for slot management

**Rationale**:
- AtomicInteger for per-priority counters is thread-safe and fast
- Semaphore for global MAX_CONCURRENT enforcement
- Non-blocking checks for availability before attempting preemption
- Fits existing coroutine-based architecture

**Alternatives Considered**:
- Synchronized blocks: Higher contention, less scalable
- Lock-free queues: Overkill for simple counter management
- Database-backed allocation: Too slow, adds unnecessary dependency

**Implementation Pattern**:
```kotlin
class ConcurrencyController(
    private val maxConcurrent: Int,
    private val p1MaxThreads: Int,
    private val p3MaxThreads: Int
) {
    private val globalSemaphore = Semaphore(maxConcurrent)
    private val p1Counter = AtomicInteger(0)
    private val p3Counter = AtomicInteger(0)
    private val runningRequests = ConcurrentHashMap<String, RunningRequest>()
}
```

### 3. Preemption Algorithm

**Decision**: Preempt lowest priority first, then oldest within priority

**Rationale**:
- P3 is lowest priority, should be preempted before P2
- Within same priority, preempt oldest (already running longest)
- Guarantees P1 gets slots within bounded time
- Fair to same-priority requests

**Alternatives Considered**:
- Random preemption: Unpredictable, poor UX
- Newest-first: Wastes work already done
- All-at-once: Too aggressive, may not need all slots

**Implementation Pattern**:
```kotlin
suspend fun preemptSlots(needed: Int, forPriority: Priority): List<RunningRequest> {
    return runningRequests.values
        .filter { it.priority.level > forPriority.level } // Only lower priorities
        .sortedWith(compareBy({ it.priority.level }, { it.startTime })) // P3 first, then oldest
        .take(needed)
        .onEach { it.cancel("Preempted by ${forPriority.value}") }
}
```

### 4. Integration with Existing Queue

**Decision**: Extend CoroutinePriorityQueue, add ConcurrencyController as component

**Rationale**:
- Minimal changes to existing working code
- ConcurrencyController encapsulates slot management
- Queue remains responsible for ordering, Controller for execution limits
- Clear separation of concerns

**Alternatives Considered**:
- Rewrite entire queue: Too risky, loses proven functionality
- Separate service: Adds complexity, harder to coordinate
- Mix into existing code: Violates SRP, harder to test

**Implementation Pattern**:
```kotlin
@Component
class CoroutinePriorityQueue<T, R>(
    private val properties: LlmProxyProperties,
    private val metricsPort: MetricsPort,
    private val concurrencyController: ConcurrencyController, // New
    private val dispatcher: CoroutineDispatcher
) : QueueService<T, R> {
    // Existing code...
    // Add preemption logic in processRequest()
}
```

### 5. Metrics for Preemption

**Decision**: Add preemption-specific metrics to existing Prometheus integration

**Rationale**:
- Consistent with existing metrics approach
- Prometheus allows rate calculations for preemption events
- Per-priority breakdown helps identify bottlenecks
- Standard naming convention with existing metrics

**Metrics to Add**:
```
llm_proxy_preemption_total{priority="p2", preempted_by="p1"}
llm_proxy_preemption_total{priority="p3", preempted_by="p1"}
llm_proxy_concurrent_slots{priority="p1"} // current usage
llm_proxy_concurrent_slots{priority="p2"}
llm_proxy_concurrent_slots{priority="p3"}
llm_proxy_p3_throttled_total // p3 rejected due to max 1 slot
```

### 6. Configuration Structure

**Decision**: Extend existing LlmProxyProperties.QueueConfig

**Rationale**:
- Consistent with existing configuration pattern
- Spring Boot @ConfigurationProperties binding
- YAML configuration for operators
- Default values match spec (MAX=3, P1=2, P3=1)

**Configuration YAML**:
```yaml
llm:
  proxy:
    queue:
      max-concurrent: 3
      p1-max-threads: 2
      p3-max-threads: 1
      preemption-enabled: true
```

## Open Questions (Resolved)

| Question | Resolution |
|----------|------------|
| How to handle preempted requests? | Complete with CancellationException, client can retry |
| What HTTP status for preemption? | 503 Service Unavailable (transient condition) |
| Should P3 queue separately? | No, use same PriorityChannel with throttling |
| How to test preemption timing? | Use kotlinx-coroutines-test with virtual time |

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| kotlinx-coroutines-core | 1.8.1 | Cancellation, structured concurrency |
| kotlinx-coroutines-test | 1.8.1 | Testing async behavior |
| kotlin-stdlib | 2.x | Atomic operations |
| spring-boot | 3.x | Configuration binding |

## Next Steps

1. Phase 1: Create data-model.md with entity definitions
2. Phase 1: Create contracts/ with API error responses
3. Phase 1: Create quickstart.md with configuration examples
