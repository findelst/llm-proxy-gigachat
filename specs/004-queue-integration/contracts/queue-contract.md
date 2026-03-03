# Queue Contract

**Feature**: 004-queue-integration
**Date**: 2026-03-03

## Overview

Внутренний контракт между ChatApplicationService и QueueService.

## Interface Contract

### QueueService<T, R>

```kotlin
interface QueueService<T : Any, R : Any> {
    /**
     * Enqueues a request for processing.
     * Blocks until result is available.
     *
     * @param request The request to enqueue
     * @return The response from processor
     * @throws QueueOverflowException if queue is at maximum capacity
     */
    suspend fun enqueue(request: QueuedRequest<T, R>): R

    /**
     * Attempts to enqueue without blocking on result.
     *
     * @param request The request to enqueue
     * @return Result.success with response if completed, Result.failure if overflow
     */
    suspend fun tryEnqueue(request: QueuedRequest<T, R>): Result<R>

    /**
     * Returns current queue length for priority.
     */
    fun queueLength(priority: Priority): Int

    /**
     * Returns total queue length across all priorities.
     */
    fun totalQueueLength(): Int

    /**
     * Returns number of requests currently being processed.
     */
    fun inFlight(priority: Priority): Int

    /**
     * Returns whether queue can accept more requests.
     */
    fun hasCapacity(): Boolean

    /**
     * Returns maximum queue capacity.
     */
    fun maxCapacity(): Int

    /**
     * Gracefully shuts down the queue.
     */
    suspend fun shutdown()
}
```

## Usage Contract

### From ChatApplicationService

```kotlin
// 1. Create queued request
val queuedRequest = QueuedRequest.create<ChatRequest, ChatResponse>(
    payload = request,
    priority = priority,
    id = requestId
)

// 2. Enqueue and wait for result
try {
    val response = queueService.enqueue(queuedRequest)
    // success handling
} catch (e: QueueOverflowException) {
    // overflow handling → HTTP 429
}
```

## Behavior Contract

### Priority Handling

| Scenario | Expected Behavior |
|----------|-------------------|
| Request with P1 | Goes to P1 queue, processed by P1 semaphore |
| Request with P2 | Goes to P2 queue, processed by P2 semaphore |
| Request with P3 | Goes to P3 queue, processed by P3 semaphore |
| Unknown priority | QueueOverflowException |

### Overflow Handling

| Condition | Behavior |
|-----------|----------|
| Queue length < maxCapacity | Request enqueued successfully |
| Queue length >= maxCapacity | QueueOverflowException thrown |
| Queue is shutting down | QueueOverflowException thrown |

### Concurrency Limits

| Priority | Max Concurrency (config) |
|----------|--------------------------|
| P1 | 2 (LLM_PRIORITY_SLOTS_P1) |
| P2 | 1 (LLM_PRIORITY_SLOTS_P2) |
| P3 | 1 (LLM_PRIORITY_SLOTS_P3) |

### Ordering Guarantees

1. **Within priority level**: FIFO - first in, first out
2. **Across priority levels**: Higher priority processed first when slot available
3. **No preemption**: In-flight requests complete before queue is checked

## Error Contract

### QueueOverflowException

```kotlin
class QueueOverflowException(
    message: String = "Queue is full. Try again later.",
    val priority: Priority? = null
) : RuntimeException(message)
```

### HTTP Mapping

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| QueueOverflowException | 429 | queue_overflow |
| QueueOverflowError | 429 | queue_overflow |

## Metrics Contract

### Prometheus Metrics

| Metric Name | Type | Labels | Description |
|-------------|------|--------|-------------|
| llm_proxy_queue_length | Gauge | priority | Current queue length per priority |
| llm_proxy_in_flight | Gauge | priority | Requests currently being processed |
| llm_proxy_queue_overflow_total | Counter | priority | Total queue overflow events |
| llm_proxy_queue_wait_seconds | Histogram | endpoint, priority | Time spent waiting in queue |

## Startup Contract

### QueueStartupRunner

```kotlin
@Component
class QueueStartupRunner(
    private val priorityQueue: CoroutinePriorityQueue<ChatRequest, ChatResponse>,
    private val chatProvider: ChatProviderPort<ChatRequest>
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        priorityQueue.startProcessing { request ->
            chatProvider.generate(request)
        }
    }
}
```

### Execution Order

1. Spring context initializes all beans
2. ApplicationRunner beans execute
3. QueueStartupRunner calls startProcessing()
4. Queue begins consuming from all priority channels
5. Application ready to accept requests
