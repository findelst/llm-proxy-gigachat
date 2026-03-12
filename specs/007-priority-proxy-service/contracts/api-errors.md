# API Contracts: Preemption Error Responses

**Feature**: 007-priority-proxy-service
**Date**: 2026-03-12

## HTTP Error Responses

### Preemption Error (HTTP 503)

Returned when a request is preempted by a higher priority request.

**Request**:
```http
POST /api/v1/chat/completions
X-Priority: p2
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [{"role": "user", "content": "Hello"}]
}
```

**Response** (when preempted):
```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Retry-After: 1

{
  "error": {
    "type": "preemption_error",
    "code": "request_preempted",
    "message": "Request was preempted by higher priority request",
    "details": {
      "preempted_by": "p1",
      "original_priority": "p2",
      "elapsed_ms": 2340,
      "retry_recommended": true
    }
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### P3 Throttling (HTTP 429)

Returned when P3 queue is at capacity (max 1 concurrent).

**Request**:
```http
POST /api/v1/chat/completions
X-Priority: p3
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [{"role": "user", "content": "Background task"}]
}
```

**Response** (when throttled):
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 30

{
  "error": {
    "type": "throttling_error",
    "code": "p3_throttled",
    "message": "Low priority requests are limited to 1 concurrent execution",
    "details": {
      "priority": "p3",
      "max_concurrent": 1,
      "current_concurrent": 1,
      "queue_position": 5,
      "estimated_wait_seconds": 45
    }
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440001"
}
```

### P1 Queue Full (HTTP 503)

Returned when P1 queue is at capacity (max 2 concurrent P1).

**Request**:
```http
POST /api/v1/chat/completions
X-Priority: p1
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [{"role": "user", "content": "Urgent request"}]
}
```

**Response** (when P1 at capacity):
```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Retry-After: 5

{
  "error": {
    "type": "capacity_error",
    "code": "p1_capacity_exceeded",
    "message": "High priority capacity exceeded (max 2 concurrent)",
    "details": {
      "priority": "p1",
      "max_concurrent": 2,
      "current_concurrent": 2,
      "queue_position": 3,
      "estimated_wait_seconds": 10
    }
  },
  "request_id": "550e8400-e29b-41d4-a716-446655440002"
}
```

## Error Types Summary

| HTTP Status | Error Type | Code | Condition |
|-------------|------------|------|-----------|
| 503 | preemption_error | request_preempted | Request preempted by higher priority |
| 429 | throttling_error | p3_throttled | P3 at max concurrent (1) |
| 503 | capacity_error | p1_capacity_exceeded | P1 at max concurrent (2) |
| 503 | queue_error | queue_overflow | General queue full |
| 503 | queue_error | queue_timeout | Request timed out in queue |

## Headers

| Header | Type | Description |
|--------|------|-------------|
| X-Priority | string | Request priority: p1, p2, p3 (default: p2) |
| X-Request-Id | string | Client-provided request ID |
| Retry-After | integer | Seconds to wait before retry |

## Metrics Endpoint

New metrics exposed at `/actuator/prometheus`:

```
# Preemption metrics
llm_proxy_preemption_total{priority="p2",preempted_by="p1"} 15
llm_proxy_preemption_total{priority="p3",preempted_by="p1"} 3

# Concurrency metrics
llm_proxy_concurrent_requests{priority="p1"} 2
llm_proxy_concurrent_requests{priority="p2"} 1
llm_proxy_concurrent_requests{priority="p3"} 0
llm_proxy_concurrent_slots_available 0

# Throttling metrics
llm_proxy_p3_throttled_total 42
llm_proxy_p3_throttled_current 5
```

## Backward Compatibility

All existing error responses remain unchanged. New error types are additive:

- Existing `queue_overflow` error continues for general queue capacity
- Existing `queue_timeout` error continues for timed-out requests
- New errors only appear when preemption/throttling features are triggered
