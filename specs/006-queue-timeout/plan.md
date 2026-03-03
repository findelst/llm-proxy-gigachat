# Implementation Plan: Queue Timeout & Cleanup

**Branch**: `006-queue-timeout` | **Date**: 2026-03-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-queue-timeout/spec.md`

## Summary

Добавить механизм timeout для запросов в очереди. Если запрос ждёт дольше настроенного времени (по умолчанию 10 минут), он должен быть удалён из очереди и клиенту возвращена понятная ошибка timeout. Реализация использует периодическую проверку (каждую минуту) в отдельной корутине.

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 21)
**Primary Dependencies**: Spring Boot 3.x, Kotlinx Coroutines
**Storage**: N/A (in-memory queue)
**Testing**: JUnit 5, Kotest assertions, kotlinx-coroutines-test
**Target Platform**: Linux server (JVM)
**Project Type**: web-service (API proxy)
**Performance Goals**: Timeout check within 1 minute of expiration
**Constraints**: Minimal overhead on queue operations
**Scale/Scope**: 100 concurrent requests max queue length

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. DDD & Clean Architecture | ✅ PASS | Timeout logic in infrastructure layer, new exception in domain |
| II. Technology Stack Discipline | ✅ PASS | Kotlin, Spring Boot, Coroutines - all compliant |
| III. Testing Standards | ✅ PASS | JUnit 5 + Kotest, new tests required |
| IV. Concurrency Model | ✅ PASS | Uses coroutine for periodic cleanup |
| V. Caching Strategy | ✅ PASS | No caching changes |

**Gate Status**: ✅ PASS - No violations

## Project Structure

### Documentation (this feature)

```text
specs/006-queue-timeout/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── domain/
│   ├── model/
│   │   ├── Exceptions.kt          # ADD: QueueTimeoutException
│   │   ├── QueuedRequest.kt       # Unchanged (already has queuedAt via QueueMetrics)
│   │   └── QueueMetrics.kt        # Already has queuedAt
│   └── service/
│       └── QueueService.kt        # Unchanged
├── application/
│   └── port/
│       └── MetricsPort.kt         # ADD: recordQueueTimeout method
├── infrastructure/
│   ├── config/
│   │   └── LlmProxyProperties.kt  # ADD: timeout config in QueueConfig
│   ├── queue/
│   │   ├── CoroutinePriorityQueue.kt  # ADD: timeout cleanup coroutine
│   │   └── PriorityChannel.kt         # ADD: removeExpired method
│   └── metrics/
│       └── PrometheusMetrics.kt   # ADD: queue_timeout_total counter
└── presentation/
    └── exception/
        └── GlobalExceptionHandler.kt  # ADD: handle QueueTimeoutException

src/test/kotlin/ru/ddd/llmproxy/
└── unit/
    └── infrastructure/queue/
        └── QueueTimeoutTest.kt    # NEW: timeout tests
```

**Structure Decision**: Single project structure maintained. Changes in infrastructure/queue layer.

## Complexity Tracking

> No violations to justify - gate passed cleanly.

## Implementation Tasks Summary

### Task 1: Add Timeout Configuration

**File**: `infrastructure/config/LlmProxyProperties.kt`

Add to QueueConfig:
- `timeoutMinutes: Long = 10` (default 10 minutes)
- `timeoutCheckIntervalMs: Long = 60000` (default 1 minute check)

### Task 2: Add QueueTimeoutException

**File**: `domain/model/Exceptions.kt`

Add new exception class distinct from QueueOverflowException.

### Task 3: Add Timeout Metrics

**File**: `application/port/MetricsPort.kt`

Add method:
- `recordQueueTimeout(priority: Priority)`

**File**: `infrastructure/metrics/PrometheusMetrics.kt`

Implement counter:
- `llm_proxy_queue_timeout_total` with priority tag

### Task 4: Implement Timeout Cleanup in PriorityChannel

**File**: `infrastructure/queue/PriorityChannel.kt`

Add method:
- `removeExpired(maxAgeMs: Long): List<PrioritizedItem<T>>`

### Task 5: Add Timeout Cleanup Coroutine

**File**: `infrastructure/queue/CoroutinePriorityQueue.kt`

Add:
- `startTimeoutCleanup()` method
- Periodic coroutine that checks for expired requests
- Complete expired requests with QueueTimeoutException

### Task 6: Add GlobalExceptionHandler

**File**: `presentation/exception/GlobalExceptionHandler.kt`

Handle QueueTimeoutException:
- Return 503 Service Unavailable with clear message
- Differentiate from 429 Too Many Requests (overflow)

### Task 7: Update application.yml

**File**: `src/main/resources/application.yml`

Add timeout configuration:
```yaml
llm:
  proxy:
    queue:
      timeout-minutes: ${LLM_QUEUE_TIMEOUT_MINUTES:10}
      timeout-check-interval-ms: ${LLM_QUEUE_TIMEOUT_CHECK_MS:60000}
```

## Phase Outputs

| Phase | Output | Status |
|-------|--------|--------|
| Phase 0 | research.md | ⏳ Pending |
| Phase 1 | data-model.md | ⏳ Pending |
| Phase 1 | quickstart.md | ⏳ Pending |
| Phase 2 | tasks.md | ⏳ Pending (/speckit.tasks) |
