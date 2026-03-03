# Implementation Plan: Queue Integration for Priority-Based Request Processing

**Branch**: `004-queue-integration` | **Date**: 2026-03-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-queue-integration/spec.md`

**Note**: This template is filled in by `/speckit.plan` command. See `.specify/templates/plan-template.md` for execution workflow.

## Summary

Интеграция существующей инфраструктуры очереди (CoroutinePriorityQueue) в ChatApplicationService для обеспечения приоритезации запросов и bounded parallelism к GigaChat API. Технический подход: внедрить QueueService через DI в ChatApplicationService, заменить прямой вызов chatProvider.generate() на queueService.enqueue(), и инициализировать обработку очереди при старте приложения через ApplicationRunner.

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 21)
**Primary Dependencies**: Spring Boot 3.x, langchain4j-gigachat 0.1.17, gigachat-java 0.1.13, Caffeine 3.x, Kotlinx Coroutines
**Storage**: Caffeine (in-memory cache) - не требуется для очереди
**Testing**: JUnit 5, Kotest 5.x, Spring Test, kotlinx-coroutines-test
**Target Platform**: JVM server application
**Project Type**: Web service (REST API proxy)
**Performance Goals**: <200ms p95 для cached responses, bounded parallelism к GigaChat (сумма priority-slots)
**Constraints**: Bounded parallelism согласно конфигурации priority-slots (p1=2, p2=1, p3=1), max queue length=100
**Scale/Scope**: ~50 LOC изменений в ChatApplicationService, 1 новый класс (QueueStartupRunner), ~3-5 новых тестов

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Domain-Driven Design & Clean Architecture | PASS | QueueService interface в domain layer, CoroutinePriorityQueue в infrastructure layer |
| II. Technology Stack Discipline | PASS | Использует Kotlin coroutines, Spring Boot DI, без новых зависимостей |
| III. Testing Standards | PASS | Тесты будут использовать JUnit 5 + Kotest + kotlinx-coroutines-test |
| IV. Concurrency Model | PASS | Coroutines + bounded parallelism - соответствует требованиям конституции |
| V. Caching Strategy | PASS | Не влияет на caching strategy |

**All gates passed.** Proceeding with implementation planning.

## Research

See [research.md](./research.md) for detailed findings.

### Key Findings:

1. **Существующая инфраструктура очереди готова**:
   - `QueueService` interface уже определён в domain layer
   - `CoroutinePriorityQueue` уже реализован в infrastructure layer
   - Per-priority channels и semaphores уже настроены

2. **Требуемые изменения минимальны**:
   - Внедрить `QueueService` в `ChatApplicationService` через конструктор
   - Заменить `processWithMetrics()` на `queueService.enqueue()`
   - Создать `QueueStartupRunner` для инициализации обработки при старте

3. **Типы уже совместимы**:
   - `CoroutinePriorityQueue<T, R>` где T=ChatRequest, R=ChatResponse
   - `QueuedRequest` уже существует в domain model
   - `Priority` enum уже определён

## Project Structure

### Documentation (this feature)

```text
specs/004-queue-integration/
├── plan.md              # This file (/speckit.plan command output)
├── spec.md              # Feature specification (/speckit.specify command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── queue-contract.md
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── application/
│   ├── service/
│   │   ├── ChatApplicationService.kt    # UPDATE: inject QueueService, use enqueue()
│   │   └── PriorityResolver.kt          # EXISTS: no changes needed
│   └── port/
│       ├── ChatProviderPort.kt          # EXISTS: no changes needed
│       └── MetricsPort.kt               # EXISTS: no changes needed
├── domain/
│   ├── model/
│   │   ├── Priority.kt                  # EXISTS: no changes needed
│   │   ├── QueuedRequest.kt             # EXISTS: no changes needed
│   │   └── QueueMetrics.kt              # EXISTS: no changes needed
│   └── service/
│       └── QueueService.kt              # EXISTS: interface ready to use
├── infrastructure/
│   ├── queue/
│   │   └── CoroutinePriorityQueue.kt    # EXISTS: implementation ready to use
│   └── config/
│       ├── CoroutineConfig.kt           # EXISTS: provides IO dispatcher
│       └── QueueStartupRunner.kt        # NEW: start queue processing on app start
└── presentation/
    ├── controller/
    │   └── ChatController.kt            # EXISTS: no changes needed
    └── exception/
        └── GlobalExceptionHandler.kt    # EXISTS: handles QueueOverflowError

src/test/kotlin/ru/ddd/llmproxy/
├── integration/
│   ├── QueueIntegrationTest.kt          # NEW: end-to-end queue tests
│   └── PriorityQueueConcurrencyTest.kt  # EXISTS: can be reused/extended
└── unit/
    └── application/
        └── service/
            └── ChatApplicationServiceQueueTest.kt  # NEW: unit tests for queue integration
```

**Structure Decision**: Используем существующую DDD/Clean Architecture структуру. Изменения минимальны - только ChatApplicationService и новый QueueStartupRunner.

## Data Model

See [data-model.md](./data-model.md) for detailed entity definitions.

### Key Entities (Existing - No Changes Required):

- **QueuedRequest**: Запрос в очереди с payload, priority, id, deferred result
- **QueueService**: Interface с методами enqueue, tryEnqueue, queueLength, inFlight
- **Priority**: Enum P1, P2, P3 с numeric level
- **QueueMetrics**: Метрики запроса (queueWaitMs, providerLatencyMs, priority)

## API Contracts

See [contracts/queue-contract.md](./contracts/queue-contract.md) for the internal queue contract.

## Complexity Tracking

> **No violations identified.** All constitution gates passed without requiring justifications.
