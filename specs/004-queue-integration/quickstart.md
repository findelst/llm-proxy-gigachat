# Quickstart: Queue Integration

**Feature**: 004-queue-integration
**Date**: 2026-03-03

## Prerequisites

- JDK 21+
- Gradle 8.x
- GigaChat API credentials

## Configuration

### Environment Variables

```bash
# Queue configuration
export LLM_PRIORITY_SLOTS_P1=2    # Max concurrent P1 requests
export LLM_PRIORITY_SLOTS_P2=1    # Max concurrent P2 requests
export LLM_PRIORITY_SLOTS_P3=1    # Max concurrent P3 requests
export LLM_PRIORITY_DEFAULT=p2    # Default priority
export LLM_QUEUE_MAX_LEN=100      # Max queue length

# API configuration
export LLM_API_KEY=your-api-key
export LLM_API_BASE=https://gigachat.devices.sberbank.ru/v1
```

## Usage Examples

### 1. Send Request with Default Priority

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "x-request-id: req-001" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

Response headers:
```
x-llm-proxy-priority: p2
x-llm-proxy-queue-wait-ms: 5
x-llm-proxy-provider-latency-ms: 1234
```

### 2. Send High Priority Request

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p1" \
  -H "x-request-id: req-002" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Urgent request!"}]
  }'
```

Response headers:
```
x-llm-proxy-priority: p1
x-llm-proxy-queue-wait-ms: 0
```

### 3. Queue Overflow Scenario

When queue is full:

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat",
    "messages": [{"role": "user", "content": "Request when full"}]
  }'
```

Response (HTTP 429):
```json
{
  "error": {
    "code": "queue_overflow",
    "status": 429,
    "message": "Queue is full. Try again later."
  }
}
```

### 4. Check Queue Metrics

```bash
curl http://localhost:8080/metrics | grep llm_proxy_queue
```

Output:
```
llm_proxy_queue_length{priority="p1"} 0
llm_proxy_queue_length{priority="p2"} 2
llm_proxy_queue_length{priority="p3"} 0
llm_proxy_in_flight{priority="p1"} 2
llm_proxy_in_flight{priority="p2"} 1
llm_proxy_in_flight{priority="p3"} 0
llm_proxy_queue_overflow_total{priority="p2"} 0
```

## Testing Queue Behavior

### Test Priority Ordering

```bash
# Terminal 1: Send low priority request
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "X-Priority: p3" \
  -H "Content-Type: application/json" \
  -d '{"model": "GigaChat", "messages": [{"role": "user", "content": "Low"}]}' &

# Terminal 2: Send high priority request (should complete first)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "X-Priority: p1" \
  -H "Content-Type: application/json" \
  -d '{"model": "GigaChat", "messages": [{"role": "user", "content": "High"}]}'
```

### Test Concurrency Limits

```bash
# Send 5 requests to P1 (max concurrency = 2)
for i in {1..5}; do
  curl -X POST http://localhost:8080/v1/chat/completions \
    -H "X-Priority: p1" \
    -H "Content-Type: application/json" \
    -d "{\"model\": \"GigaChat\", \"messages\": [{\"role\": \"user\", \"content\": \"Request $i\"}]}" &
done
wait

# Check that max 2 were processed in parallel
# (verify via queue_wait_ms in response headers)
```

## Verification Checklist

- [ ] Queue initializes on startup (check logs for "Started processing for all priority levels")
- [ ] Requests with X-Priority header use correct priority
- [ ] Requests without X-Priority use default (p2)
- [ ] Queue overflow returns HTTP 429
- [ ] Metrics are recorded correctly
- [ ] Priority ordering works (P1 before P3)
- [ ] Concurrency limits respected per priority
