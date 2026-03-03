# Implementation Plan: FIFO Priority Queue

**Branch**: `005-fifo-priority-queue` | **Date**: 2026-03-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-fifo-priority-queue/spec.md`

## Summary

Упрощение очереди с удалением per-priority семафоров. Система будет использовать единый PriorityChannel для глобального упорядочивания запросов по приоритету с FIFO внутри каждого приоритета. Существующие PriorityChannel и PrioritizedItem уже реализованы корректно - требуется только удаление семафоров из CoroutinePriorityQueue.

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 21)
**Primary Dependencies**: Spring Boot 3.x, Kotlinx Coroutines, langchain4j-gigachat 0.1.17
**Storage**: N/A (in-memory queue)
**Testing**: JUnit 5, Kotest assertions, kotlinx-coroutines-test
**Target Platform**: Linux server (JVM)
**Project Type**: web-service (API proxy)
**Performance Goals**: FIFO ordering guarantee, <1ms enqueue time
**Constraints**: No per-priority concurrency limits, max-length overflow protection
**Scale/Scope**: 100 concurrent requests max queue length

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. DDD & Clean Architecture | ✅ PASS | Changes in infrastructure layer only, domain unchanged |
| II. Technology Stack Discipline | ✅ PASS | Kotlin, Spring Boot, Coroutines - all compliant |
| III. Testing Standards | ✅ PASS | JUnit 5 + Kotest, existing tests must pass |
| IV. Concurrency Model | ✅ PASS | Coroutines with structured concurrency maintained |
| V. Caching Strategy | ✅ PASS | No caching changes |

**Gate Status**: ✅ PASS - No violations

## Project Structure

### Documentation (this feature)

```text
specs/005-fifo-priority-queue/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output ✅
├── data-model.md        # Phase 1 output ✅
├── quickstart.md        # Phase 1 output ✅
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── domain/
│   ├── model/
│   │   ├── Priority.kt          # Unchanged
│   │   ├── PrioritySlot.kt      # May deprecate
│   │   ├── QueuedRequest.kt     # Unchanged
│   │   ├── QueueMetrics.kt      # Unchanged
│   │   ├── RetryConfig.kt       # Unchanged
│   │   └── Exceptions.kt        # Unchanged
│   └── service/
│       └── QueueService.kt      # Interface unchanged
├── application/
│   ├── port/
│   │   ├── ChatProviderPort.kt  # Unchanged
│   │   └── MetricsPort.kt       # Unchanged
│   └── service/
│       └── ChatApplicationService.kt  # Unchanged
├── infrastructure/
│   ├── config/
│   │   ├── LlmProxyProperties.kt     # Deprecate priority-slots
│   │   └── QueueStartupRunner.kt     # Unchanged
│   ├── queue/
│   │   ├── CoroutinePriorityQueue.kt # REMOVE per-priority semaphores
│   │   ├── PriorityChannel.kt        # Unchanged (already correct)
│   │   ├── PrioritizedItem.kt        # Unchanged (already correct)
│   │   └── RetryExecutor.kt          # Unchanged
│   └── metrics/
│       └── PrometheusMetrics.kt      # Unchanged
└── presentation/
    └── controller/
        └── ChatController.kt         # Unchanged

src/test/kotlin/ru/ddd/llmproxy/
├── unit/
│   └── infrastructure/queue/
│       ├── PriorityChannelTest.kt    # Update for new behavior
│       └── RetryExecutorTest.kt      # Unchanged
└── integration/
    └── PriorityQueueConcurrencyTest.kt  # Update for new behavior
```

**Structure Decision**: Single project structure maintained. Changes isolated to infrastructure/queue layer.

## Complexity Tracking

> No violations to justify - gate passed cleanly.

## Implementation Tasks Summary

### Task 1: Remove Per-Priority Semaphores from CoroutinePriorityQueue

**File**: `infrastructure/queue/CoroutinePriorityQueue.kt`

Changes:
- Remove `prioritySemaphores: Map<Priority, Semaphore>` field
- Remove `prioritySlots` field (keep only for logging if needed)
- Remove `semaphore.acquire()` / `semaphore.release()` in `processRequest()`
- Remove semaphore-related imports

### Task 2: Deprecate priority-slots Configuration

**File**: `infrastructure/config/LlmProxyProperties.kt`

Changes:
- Mark `prioritySlots` field as deprecated or remove
- Add warning log if priority-slots is configured

### Task 3: Update application.yml

**File**: `src/main/resources/application.yml`

Changes:
- Remove `priority-slots` section
- Keep other queue settings (max-length, retry, default-priority)

### Task 4: Update Tests

**File**: `PriorityQueueConcurrencyTest.kt`

Changes:
- Remove tests that verify semaphore limits
- Update tests to verify FIFO ordering
- Ensure priority ordering tests still pass

## Phase Outputs

| Phase | Output | Status |
|-------|--------|--------|
| Phase 0 | research.md | ✅ Complete |
| Phase 1 | data-model.md | ✅ Complete |
| Phase 1 | quickstart.md | ✅ Complete |
| Phase 2 | tasks.md | ⏳ Pending (/speckit.tasks) |
