# Quickstart: FIFO Priority Queue

**Feature**: 005-fifo-priority-queue
**Date**: 2026-03-03

## Overview

Упрощённая очередь с FIFO упорядочиванием внутри каждого приоритета. Per-priority ограничения конкурентности удалены.

## Configuration

### Minimal Configuration

```yaml
llm:
  proxy:
    queue:
      default-priority: p2
      max-length: 100
      retry:
        enabled: true
        max-attempts: 3
```

### Deprecated Configuration (Ignored with Warning)

```yaml
llm:
  proxy:
    queue:
      priority-slots:    # ⚠️ DEPRECATED - will be ignored
        p1: 3
        p2: 2
        p3: 1
```

## Usage

### Sending Requests

```bash
# High priority request (P1)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "X-Priority: p1" \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}]}'

# Default priority (P2)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}]}'

# Low priority (P3)
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "X-Priority: p3" \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}]}'
```

### Processing Order

Requests are processed in this order:
1. All P1 requests (FIFO order within P1)
2. All P2 requests (FIFO order within P2)
3. All P3 requests (FIFO order within P3)

### Example

```
Time 0ms:  Request A (P2) arrives → Queue: [A]
Time 1ms:  Request B (P3) arrives → Queue: [A, B]
Time 2ms:  Request C (P1) arrives → Queue: [C, A, B]  ← P1 jumps ahead
Time 3ms:  Request D (P2) arrives → Queue: [C, A, B, D]

Processing order: C → A → D → B
```

## Metrics

Available Prometheus metrics:

| Metric | Description |
|--------|-------------|
| `llm_proxy_queue_length` | Current queue length by priority |
| `llm_proxy_in_flight` | Requests currently being processed |
| `llm_proxy_queue_wait_seconds` | Time spent waiting in queue |
| `llm_proxy_retry_attempts_total` | Total retry attempts |

## Migration from Previous Version

1. Remove `priority-slots` from configuration (optional, will be ignored)
2. Restart application
3. Verify queue behavior with test requests

## Troubleshooting

### Queue Overflow (429)

```
{"error": "queue_overflow", "message": "Queue is full. Try again later."}
```

**Solution**: Increase `max-length` or reduce request rate.

### Priority Not Respected

Verify the `X-Priority` header is being sent correctly (`p1`, `p2`, or `p3`).
