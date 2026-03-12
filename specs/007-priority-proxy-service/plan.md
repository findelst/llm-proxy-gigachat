# Implementation Plan: Priority Proxy Service with Preemption

**Branch**: `007-priority-proxy-service` | **Date**: 2026-03-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-priority-proxy-service/spec.md`

## Summary

Extend the existing `CoroutinePriorityQueue` with preemption capabilities for p1 priority and throttling for p3 priority. P1 requests can preempt running p2/p3 requests to guarantee immediate execution (max 2 slots). P3 requests are throttled to max 1 concurrent execution. The implementation integrates with existing queue infrastructure and maintains DDD architecture.

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 21)
**Primary Dependencies**: Spring Boot 3.x, kotlinx-coroutines 1.8.1, Caffeine 3.x, langchain4j-gigachat 0.1.17
**Storage**: N/A (in-memory queue with Caffeine cache)
**Testing**: JUnit 5, Kotest assertions, kotlinx-coroutines-test, MockK
**Target Platform**: JVM 21 server
**Project Type**: web-service (REST API proxy)
**Performance Goals**: p1 preemption within 500ms, max 3 concurrent requests
**Constraints**: Bounded parallelism (MAX_CONCURRENT=3), cooperative cancellation
**Scale/Scope**: Single-node deployment, ~1000 req/min

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. DDD & Clean Architecture | ✅ PASS | New domain entities in domain layer, infrastructure in infrastructure layer |
| II. Technology Stack Discipline | ✅ PASS | Kotlin + Spring Boot + coroutines - all approved |
| III. Testing Standards | ✅ PASS | Unit tests for domain logic, integration tests for queue behavior |
| IV. Concurrency Model | ✅ PASS | Coroutines with structured concurrency, cooperative cancellation |
| V. Caching Strategy | ✅ PASS | No new caching requirements, uses existing Caffeine |

**Gate Status**: ✅ PASSED - No violations

## Project Structure

### Documentation (this feature)

```text
specs/007-priority-proxy-service/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── domain/
│   ├── model/
│   │   ├── Priority.kt              # Existing - add preemption-related helpers
│   │   ├── PrioritySlot.kt          # New - track slot allocation
│   │   ├── PreemptionEvent.kt       # New - preemption event entity
│   │   └── ConcurrencyConfig.kt     # New - concurrency configuration value object
│   └── service/
│       ├── QueueService.kt          # Existing - add preemption methods
│       └── PreemptionManager.kt     # New - preemption domain service
├── application/
│   ├── port/
│   │   └── MetricsPort.kt           # Existing - add preemption metrics
│   └── service/
│       └── ChatApplicationService.kt # Existing - integrate preemption
├── infrastructure/
│   ├── config/
│   │   └── LlmProxyProperties.kt    # Existing - add concurrency config
│   ├── queue/
│   │   ├── CoroutinePriorityQueue.kt # Existing - extend with preemption
│   │   ├── ConcurrencyController.kt  # New - slot allocation management
│   │   └── PriorityChannel.kt        # Existing - may need modifications
│   └── metrics/
│       └── PrometheusMetrics.kt      # Existing - add preemption metrics
└── presentation/
    ├── controller/
    │   └── ChatController.kt         # Existing - may need error handling
    └── exception/
        └── GlobalExceptionHandler.kt # Existing - add preemption error

src/test/kotlin/ru/ddd/llmproxy/
├── unit/
│   ├── domain/
│   │   └── service/
│   │       └── PreemptionManagerTest.kt  # New
│   └── infrastructure/
│       └── queue/
│           ├── ConcurrencyControllerTest.kt  # New
│           └── PreemptionIntegrationTest.kt  # New
└── integration/
    └── PriorityQueueConcurrencyTest.kt  # Existing - extend
```

**Structure Decision**: Extends existing single-project structure following DDD layers. New components in domain/service for preemption logic, infrastructure/queue for slot management.

## Complexity Tracking

> No violations to justify - all changes fit within existing architecture.
