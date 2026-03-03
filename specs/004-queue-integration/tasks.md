# Tasks: Queue Integration for Priority-Based Request Processing

**Input**: Design documents from `/specs/004-queue-integration/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Tests are NOT explicitly requested in the specification. Implementation tasks only.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Source**: `src/main/kotlin/ru/ddd/llmproxy/`
- **Tests**: `src/test/kotlin/ru/ddd/llmproxy/`
- Based on plan.md structure with DDD/Clean Architecture layers

---

## Phase 1: Setup (No Setup Required)

**Purpose**: No new dependencies or project structure changes needed

**Note**: Все необходимые компоненты уже существуют в проекте:
- QueueService interface в domain layer
- CoroutinePriorityQueue implementation в infrastructure layer
- Priority, QueuedRequest, QueueMetrics entities в domain model
- PriorityResolver уже внедрён в ChatApplicationService

---

## Phase 2: Foundational (Startup Infrastructure)

**Purpose**: Создание инфраструктуры для инициализации очереди при старте приложения

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T001 Create QueueStartupRunner implementing ApplicationRunner in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/QueueStartupRunner.kt

**Checkpoint**: ✅ Queue startup infrastructure ready

---

## Phase 3: User Story 1 - Request Processing Through Priority Queue (Priority: P1) 🎯 MVP

**Goal**: Запросы обрабатываются через QueueService.enqueue() вместо прямого вызова провайдера

**Independent Test**: Отправить запросы с разными приоритетами и проверить ограничение параллелизма через метрики

### Implementation for User Story 1

- [x] T002 [US1] Add QueueService constructor injection to ChatApplicationService in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T003 [US1] Replace processWithMetrics() call with queueService.enqueue() in completions() method in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T004 [US1] Create QueuedRequest with payload, priority, and requestId before enqueue in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T005 [US1] Preserve cache-first logic (check cache before enqueue) in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T006 [US1] Update QueueMetrics to track queue wait time from QueuedRequest timing in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt

**Checkpoint**: ✅ User Story 1 fully functional - requests flow through queue

---

## Phase 4: User Story 2 - Queue Overflow Handling (Priority: P1)

**Goal**: При переполнении очереди возвращается HTTP 429 с понятным сообщением

**Independent Test**: Отправить > maxLen запросов и проверить HTTP 429 с кодом queue_overflow

**Note**: GlobalExceptionHandler уже обрабатывает QueueOverflowError - требуется только убедиться в корректности

### Verification for User Story 2

- [x] T007 [US2] Verify GlobalExceptionHandler returns HTTP 429 for QueueOverflowError in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/GlobalExceptionHandler.kt (ALREADY EXISTS - verify only)
- [x] T008 [US2] Verify ErrorResponse.queueOverflow() returns correct error format in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/ErrorResponse.kt (ALREADY EXISTS - verify only)
- [x] T009 [US2] Verify queue overflow metrics are recorded in CoroutinePriorityQueue in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt (ALREADY EXISTS - verify only)

**Checkpoint**: ✅ User Stories 1 AND 2 both work - overflow handling verified

---

## Phase 5: User Story 3 - Priority Resolution with GigaChat Native Format (Priority: P1)

**Goal**: Приоритет из заголовка X-Priority корректно используется для выбора очереди

**Independent Test**: Отправить запрос с X-Priority: p1 и проверить x-llm-proxy-priority: p1 в ответе

**Note**: ChatController уже передаёт headerPriority в ChatApplicationService - интеграция работает автоматически после US1

### Verification for User Story 3

- [x] T010 [US3] Verify ChatController passes X-Priority header to ChatApplicationService in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt (ALREADY EXISTS - verify only)
- [x] T011 [US3] Verify PriorityResolver is used to determine queue priority in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt (should work after T002-T006)
- [x] T012 [US3] Verify response header x-llm-proxy-priority reflects resolved priority in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt (ALREADY EXISTS - verify only)

**Checkpoint**: ✅ User Stories 1, 2, AND 3 all work - priority resolution verified

---

## Phase 6: User Story 4 - Application Startup Queue Initialization (Priority: P2)

**Goal**: Очередь инициализируется при старте приложения через QueueStartupRunner

**Independent Test**: Проверить логи при старте - сообщение "Started processing for all priority levels"

### Verification for User Story 4

- [x] T013 [US4] Verify QueueStartupRunner.startProcessing() is called on application startup in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/QueueStartupRunner.kt (created in T001)
- [x] T014 [US4] Verify processor function calls chatProvider.generate() in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/QueueStartupRunner.kt
- [x] T015 [US4] Verify graceful shutdown via @PreDestroy in CoroutinePriorityQueue in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt (ALREADY EXISTS - verify only)

**Checkpoint**: ✅ All user stories complete - queue fully integrated

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T016 [P] Add logging for queue integration in ChatApplicationService in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T017 [P] Verify all existing tests pass after queue integration
- [x] T018 Run ./gradlew test to validate no regressions
- [x] T019 Update quickstart.md validation - all curl examples work with queue

---

## Implementation Summary

### Completed Tasks: 19/19 (100%)

| Phase | Tasks | Status |
|-------|-------|--------|
| Phase 1: Setup | 0 | N/A (not needed) |
| Phase 2: Foundational | 1 | ✅ COMPLETE |
| Phase 3: US1 Queue Integration | 5 | ✅ COMPLETE |
| Phase 4: US2 Overflow | 3 | ✅ COMPLETE |
| Phase 5: US3 Priority | 3 | ✅ COMPLETE |
| Phase 6: US4 Startup | 3 | ✅ COMPLETE |
| Phase 7: Polish | 4 | ✅ COMPLETE |
| **Total** | **19** | **✅ ALL COMPLETE** |

### Files Modified:

1. **NEW**: `src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/QueueStartupRunner.kt`
2. **MODIFIED**: `src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt`
3. **MODIFIED**: `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
4. **MODIFIED**: `src/main/kotlin/ru/ddd/llmproxy/domain/model/QueuedRequest.kt`
5. **MODIFIED**: `src/test/kotlin/ru/ddd/llmproxy/unit/application/service/ChatApplicationServiceTest.kt`

### Key Changes:

- **QueueService injected into ChatApplicationService** via constructor
- **Requests flow through priority queue** instead of direct provider calls
- **Queue metrics tracked correctly** with queue wait time and provider latency
- **Cache-first logic preserved** - cache hits bypass queue
- **Application startup initializes queue** via QueueStartupRunner
- **Graceful shutdown** via @PreDestroy in CoroutinePriorityQueue

### Test Results:

- **86 tests passed** ✅
- **0 tests failed**
- **Build successful**
