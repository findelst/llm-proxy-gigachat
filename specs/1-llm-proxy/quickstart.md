# Quick Start: LLM Proxy

## Prerequisites

- JDK 21+
- Gradle 8.x
- GigaChat API ключ авторизации

## Configuration

### Environment Variables

```bash
# GigaChat API
export LLM_API_BASE="https://gigachat.devices.sberbank.ru/v1"
export LLM_API_KEY="your-auth-key-here"
export LLM_DEFAULT_MODEL_NAME="GigaChat"

# Queue Configuration
export LLM_PRIORITY_SLOTS='{"p1":2,"p2":1,"p3":1}'
export LLM_PRIORITY_DEFAULT="p2"
export LLM_QUEUE_MAX_LEN=100

# Cache Configuration
export LLM_CACHE_ENABLED="true"
export LLM_CACHE_TTL="600000"  # 10 minutes in ms

# Service Configuration
export LOG_LEVEL="INFO"
export ENV="development"
```

### application.yml

```yaml
llm:
  proxy:
    api:
      base-url: ${LLM_API_BASE:https://gigachat.devices.sberbank.ru/v1}
      key: ${LLM_API_KEY}
      timeout: 300s
    model:
      default-name: ${LLM_DEFAULT_MODEL_NAME:GigaChat}
      override: ${LLM_MODEL_NAME:}
      temperature: ${LLM_TEMPERATURE:0.0}
      top-p: ${LLM_TOP_P:1.0}
      max-tokens: ${LLM_MAX_TOKENS:50000}
    queue:
      priority-slots: ${LLM_PRIORITY_SLOTS:{"p1":2,"p2":1,"p3":1}}
      default-priority: ${LLM_PRIORITY_DEFAULT:p2}
      max-length: ${LLM_QUEUE_MAX_LEN:100}
    cache:
      enabled: ${LLM_CACHE_ENABLED:true}
      ttl: ${LLM_CACHE_TTL:600000ms}
      max-size: 1000

logging:
  level:
    root: ${LOG_LEVEL:INFO}

spring:
  application:
    name: llm-proxy
```

## Build & Run

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run application
./gradlew bootRun

# Or with custom config
java -jar build/libs/llm-proxy.jar --spring.profiles.active=prod
```

## API Usage

### Basic Chat Completion

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p1" \
  -d '{
    "messages": [
      {"role": "user", "content": "Привет, расскажи о Kotlin"}
    ]
  }'
```

### Response Headers

```
x-llm-proxy-request-id: 550e8400-e29b-41d4-a716-446655440000
x-llm-proxy-queue-wait-ms: 5
x-llm-proxy-provider-latency-ms: 1500
x-llm-proxy-priority: p1
x-request-id: 550e8400-e29b-41d4-a716-446655440000
```

### Chat Invoke (with metrics in body)

```bash
curl -X POST http://localhost:8080/v1/chat/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Hello"}
    ]
  }'
```

Response includes metrics:

```json
{
  "id": "chatcmpl-123",
  "choices": [...],
  "usage": {...},
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "metrics": {
    "queue_wait_ms": 5,
    "provider_latency_ms": 1500,
    "priority": "p2",
    "endpoint": "/v1/chat/invoke"
  }
}
```

### Health Check

```bash
curl http://localhost:8080/health
# {"status":"ok","env":"development"}
```

### Metrics

```bash
curl http://localhost:8080/metrics
# Prometheus format output
```

## Priority System

| Priority | Description | Use Case |
|----------|-------------|----------|
| p1 / highest | Maximum priority | Critical requests, SLA-bound |
| p2 | Default | Normal requests |
| p3 / lowest | Lowest priority | Background, batch processing |

### Setting Priority

1. **Via Header**: `X-Priority: p1`
2. **Via Body**: `{"priority": "p1", "messages": [...]}`
3. **Default**: p2 (configurable)

## Error Handling

All errors follow the same format:

```json
{
  "error": {
    "code": "invalid_request",
    "http_status": 400,
    "message": "Human readable message",
    "request_id": "uuid-for-tracing"
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `invalid_request` | 400 | Bad request, validation error |
| `queue_overflow` | 429 | Queue full, retry later |
| `provider_error` | 500/502 | GigaChat API error |
| `internal` | 500 | Unexpected server error |

## Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ru.ddd.llmproxy.presentation.controller.ChatControllerTest"

# Run with coverage
./gradlew test jacocoTestReport
```

## Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY build/libs/llm-proxy.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t llm-proxy .
docker run -p 8080:8080 \
  -e LLM_API_KEY=your-key \
  llm-proxy
```

## Troubleshooting

### Cache Not Working

```bash
# Check cache status via actuator
curl http://localhost:8080/actuator/caches
```

### Queue Overflow

```bash
# Check metrics
curl http://localhost:8080/metrics | grep queue_overflow

# Increase queue size
export LLM_QUEUE_MAX_LEN=500
```

### Debug Logging

```bash
export LOG_LEVEL=DEBUG
./gradlew bootRun
```

### Request Tracing

Use `x-request-id` header to trace requests:

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "x-request-id: my-trace-id-123" \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "test"}]}'
```

The same `x-request-id` will be in the response headers and error messages.
