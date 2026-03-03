# Quickstart: Queue Timeout & Cleanup

**Feature**: 006-queue-timeout
**Date**: 2026-03-03

## Overview

Механизм автоматического удаления запросов из очереди если они ждут дольше настроенного timeout. Клиенты получают понятную ошибку 408 вместо бесконечного ожидания.

## Configuration

### Default Configuration

```yaml
llm:
  proxy:
    queue:
      timeout-minutes: 10           # Max wait time in queue (default: 10 minutes)
      timeout-check-interval-ms: 60000  # Check interval (default: 1 minute)
```

### Environment Variables

```bash
# Set queue timeout to 5 minutes
export LLM_QUEUE_TIMEOUT_MINUTES=5

# Check every 30 seconds
export LLM_QUEUE_TIMEOUT_CHECK_MS=30000
```

### Disable Timeout

```yaml
llm:
  proxy:
    queue:
      timeout-minutes: 0  # Disable timeout - requests wait forever
```

## Usage Examples

### Normal Request Flow

```bash
# Request processed immediately
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}]}'
# Response: 200 OK with result
```

### Timeout Scenario

```bash
# When queue is overloaded and request waits too long
# Client receives:
HTTP/1.1 408 Request Timeout
{
  "error": {
    "type": "queue_timeout",
    "message": "Request timed out after waiting 10 minutes in queue",
    "priority": "p3",
    "waitTimeMs": 600123
  }
}
```

### Overflow vs Timeout

| Scenario | HTTP Status | Error Type | Message |
|----------|-------------|------------|---------|
| Queue full | 429 | queue_overflow | "Queue is full, try again later" |
| Wait too long | 408 | queue_timeout | "Request timed out after waiting X minutes in queue" |

## Metrics

New Prometheus metrics available:

| Metric | Type | Description |
|--------|------|-------------|
| `llm_proxy_queue_timeout_total` | Counter | Total requests timed out, by priority |

Example query:
```promql
# Total timeouts per priority
sum by (priority) (llm_proxy_queue_timeout_total)

# Rate of timeouts per minute
rate(llm_proxy_queue_timeout_total[5m])
```

## Error Handling

### For Clients

When receiving 408 timeout:
1. The request was accepted but waited too long
2. Safe to retry with same request (idempotent)
3. Consider using higher priority for time-sensitive requests

### For Operators

Monitor timeout metrics to:
- Identify capacity issues
- Adjust timeout configuration
- Tune priority distribution

## Testing

### Manual Test

```bash
# 1. Set very short timeout for testing
export LLM_QUEUE_TIMEOUT_MINUTES=0.1  # 6 seconds
export LLM_QUEUE_TIMEOUT_CHECK_MS=2000  # Check every 2 seconds

# 2. Fill queue with requests
for i in {1..20}; do
  curl -X POST http://localhost:8080/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d "{\"messages\": [{\"role\": \"user\", \"content\": \"Test $i\"}]}" &
done

# 3. Some requests should timeout after 6+ seconds
```

## Troubleshooting

### Too Many Timeouts

**Symptom**: High `llm_proxy_queue_timeout_total` rate

**Solutions**:
- Increase `timeout-minutes`
- Add more backend capacity
- Review priority distribution
- Check for processing bottlenecks

### No Timeouts But Slow

**Symptom**: Requests succeed but take long time

**Solutions**:
- Timeout is too high
- Check processing latency metrics
- Review retry configuration
