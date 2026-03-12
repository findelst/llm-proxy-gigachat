# Quickstart: Priority Proxy Service with Preemption

**Feature**: 007-priority-proxy-service
**Date**: 2026-03-12

## Configuration

### application.yml

```yaml
llm:
  proxy:
    api:
      base-url: https://gigachat.devices.sberbank.ru/v1
      key: ${GIGACHAT_API_KEY}
      timeout: 5m

    queue:
      # Existing settings
      default-priority: p2
      max-length: 100
      timeout-minutes: 10

      # NEW: Concurrency control
      max-concurrent: 3        # Total concurrent requests
      p1-max-threads: 2        # Max concurrent P1 (high priority)
      p3-max-threads: 1        # Max concurrent P3 (low priority)
      preemption-enabled: true # Enable P1 preemption
```

## Usage Examples

### Sending P1 (High Priority) Request

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p1" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Urgent: process this now"}]
  }'
```

**Behavior**: P1 requests preempt running P2/P3 requests if needed. Max 2 P1 concurrent.

### Sending P2 (Normal Priority) Request

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p2" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Normal request"}]
  }'
```

**Behavior**: P2 fills available slots. Can be preempted by P1.

### Sending P3 (Low Priority) Request

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p3" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Background task"}]
  }'
```

**Behavior**: P3 limited to 1 concurrent. Waits if slot occupied. Can be preempted by P1.

## Monitoring

### Check Current Concurrency

```bash
curl http://localhost:8080/actuator/prometheus | grep llm_proxy_concurrent
```

Output:
```
llm_proxy_concurrent_requests{priority="p1"} 2
llm_proxy_concurrent_requests{priority="p2"} 1
llm_proxy_concurrent_requests{priority="p3"} 0
llm_proxy_concurrent_slots_available 0
```

### Check Preemption Events

```bash
curl http://localhost:8080/actuator/prometheus | grep llm_proxy_preemption
```

Output:
```
llm_proxy_preemption_total{priority="p2",preempted_by="p1"} 15
llm_proxy_preemption_total{priority="p3",preempted_by="p1"} 3
```

## Scenario Walkthrough

### Scenario: P1 Preempts P2

1. System has 3 P2 requests running (slots full)
2. P1 request arrives
3. System preempts 2 P2 requests (P1 needs max 2 slots)
4. P1 starts executing immediately
5. Preempted P2 requests receive HTTP 503 with `preemption_error`
6. Clients retry P2 requests

```
Time  | Slot 1 | Slot 2 | Slot 3 | Queue
------|--------|--------|--------|-------
T0    | P2-A   | P2-B   | P2-C   | -
T1    | P2-A   | P2-B   | P2-C   | P1-X (arrives)
T2    | P1-X   | P1-X   | P2-A   | P2-B, P2-C (preempted)
T3    | P1-X   | P1-X   | P2-A   | P2-B
T4    | P2-B   | P1-X   | P2-A   | P2-C (P1 complete, P2-B resumes)
```

### Scenario: P3 Throttling

1. P3 request A is running (slot occupied)
2. P3 request B arrives
3. B queues (P3 max 1 concurrent)
4. A completes
5. B starts executing

```
Time  | Slot 1 | Queue (P3)
------|--------|------------
T0    | P3-A   | -
T1    | P3-A   | P3-B (throttled)
T2    | P3-A   | P3-B, P3-C
T3    | P3-B   | P3-C (A complete, B starts)
```

## Error Handling

### Retry Preempted Requests

```python
import requests
import time

def send_with_retry(url, payload, priority, max_retries=3):
    headers = {
        "Content-Type": "application/json",
        "X-Priority": priority
    }

    for attempt in range(max_retries):
        response = requests.post(url, json=payload, headers=headers)

        if response.status_code == 200:
            return response.json()

        if response.status_code == 503:
            error = response.json().get("error", {})

            if error.get("code") == "request_preempted":
                # Preempted - retry immediately
                print(f"Request preempted, retrying (attempt {attempt + 1})")
                time.sleep(0.1)  # Brief pause
                continue

            if error.get("code") == "p3_throttled":
                # Throttled - wait recommended time
                retry_after = response.headers.get("Retry-After", 5)
                print(f"P3 throttled, waiting {retry_after}s")
                time.sleep(int(retry_after))
                continue

        response.raise_for_status()

    raise Exception("Max retries exceeded")
```

## Testing

### Unit Test: Preemption Logic

```kotlin
@Test
fun `P1 should preempt P2 when slots full`() = runTest {
    // Given: 3 P2 requests running
    val config = ConcurrencyConfig(maxConcurrent = 3, p1MaxThreads = 2)
    val controller = ConcurrencyController(config)

    repeat(3) {
        controller.acquire(createRequest(priority = Priority.P2))
    }

    // When: P1 arrives
    val p1Request = createRequest(priority = Priority.P1)
    val preempted = controller.preemptFor(p1Request)

    // Then: 2 P2 requests are preempted
    preempted shouldHaveSize 2
    preempted.all { it.priority == Priority.P2 } shouldBe true
}
```

### Integration Test: End-to-End Preemption

```kotlin
@Test
fun `P1 request preempts running P2`() = runTest {
    // Given: Queue processing P2 requests
    val processingLatch = CompletableDeferred<Unit>()
    val queue = createQueueWithProcessor {
        processingLatch.await()
        "result"
    }

    // Start 3 P2 requests
    val p2Jobs = List(3) {
        async { queue.enqueue(createP2Request()) }
    }

    // When: P1 arrives
    val p1Result = async { queue.enqueue(createP1Request()) }

    // Then: P1 completes first (after preemption)
    processingLatch.complete(Unit)

    p1Result.await() shouldNotBe null
}
```
