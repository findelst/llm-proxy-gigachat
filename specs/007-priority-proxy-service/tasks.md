# Tasks: Priority Proxy Service with Preemption

**Input**: Design documents from `/specs/007-priority-proxy-service/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are included per spec requirements (SC-005: All existing tests pass)

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Source**: `src/main/kotlin/ru/ddd/llmproxy/`
- **Tests**: `src/test/kotlin/ru/ddd/llmproxy/`

---

## Phase 1: Setup

- [X] T001 Extend LlmProxyProperties.QueueConfig with concurrency settings in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/LlmProxyProperties.kt

**Checkpoint**: Configuration ready for implementation

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T002 [P] Create ConcurrencyConfig value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/ConcurrencyConfig.kt
- [X] T003 [P] Create RunningRequest entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/RunningRequest.kt
- [X] T004 [P] Create ExecutionSlot entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/ExecutionSlot.kt
- [X] T005 [P] Create PreemptionEvent domain event in src/main/kotlin/ru/ddd/llmproxy/domain/model/PreemptionEvent.kt
- [X] T006 [P] Create ConcurrencyStats read model in src/main/kotlin/ru/ddd/llmproxy/domain/model/ConcurrencyStats.kt
- [X] T007 Create ConcurrencyController component in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/ConcurrencyController.kt (depends on T002-T006)
- [X] T008 Extend MetricsPort interface with preemption metrics in src/main/kotlin/ru/ddd/llmproxy/application/port/MetricsPort.kt
- [X] T009 Implement preemption metrics in PrometheusMetrics in src/main/kotlin/ru/ddd/llmproxy/infrastructure/metrics/PrometheusMetrics.kt
- [X] T010 Create PreemptionError exception in src/main/kotlin/ru/ddd/llmproxy/domain/model/Exceptions.kt

**Checkpoint**: Foundation ready - user story implementation can begin in parallel

---

## Phase 3: User Story 1 - P1 Preemption (Priority: P1) 🎯 MVP

**Goal**: High-priority P1 requests preempt running P2/P3 requests to guarantee immediate execution.

**Independent Test**: Fill all slots with P2, send P1, verify P1 executes immediately via preemption.

### Tests for User Story 1

- [X] T011 [P] [US1] Unit test for ConcurrencyController preemption logic in src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/ConcurrencyControllerTest.kt
- [X] T012 [P] [US1] Integration test for P1 preemption scenario in src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt

### Implementation for User Story 1

- [X] T013 [US1] Add preemption methods to QueueService interface in src/main/kotlin/ru/ddd/llmproxy/domain/service/QueueService.kt
- [X] T014 [US1] Integrate ConcurrencyController into CoroutinePriorityQueue in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt
- [X] T015 [US1] Implement P1 preemption logic in processRequest in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt
- [X] T016 [US1] Add slot release on request completion in CoroutinePriorityQueue

**Checkpoint**: P1 preemption fully functional - can preempt P2/P3 when needed

---

## Phase 4: User Story 2 - P3 Throttling (Priority: P2)

**Goal**: Low-priority P3 requests limited to max 1 concurrent execution.

**Independent Test**: Send multiple P3 requests, verify only 1 executes at a time.

### Tests for User Story 2
- [X] T017 [P] [US2] Unit test for P3 throttling in ConcurrencyController in src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/ConcurrencyControllerTest.kt
- [X] T018 [P] [US2] Integration test for P3 throttling scenario in src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt

### Implementation for User Story 2
- [X] T019 [US2] Add P3 max threads check in ConcurrencyController.tryAcquire in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/ConcurrencyController.kt
- [X] T020 [US2] Update CoroutinePriorityQueue to check P3 limits before dispatch in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt

**Checkpoint**: P3 throttling functional - max 1 concurrent P3 request

---

## Phase 5: User Story 3 - Graceful Preemption (Priority: P2)

**Goal**: Preempted requests receive HTTP 503 with clear error message.

**Independent Test**: Preempt a P2 request, verify client receives proper HTTP 503 with preemption_error.

### Tests for User Story 3
- [X] T021 [P] [US3] Unit test for preemption error handling in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/exception/GlobalExceptionHandlerTest.kt
- [X] T022 [P] [US3] Integration test for preemption error response in src/test/kotlin/ru/ddd/llmproxy/integration/ChatCompletionsEndpointTest.kt

### Implementation for User Story 3
- [X] T023 [US3] Add PreemptionErrorException and handle in GlobalExceptionHandler in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/GlobalExceptionHandler.kt
- [X] T024 [US3] Create preemption error response DTO in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ErrorResponse.kt (extend existing)
- [X] T025 [US3] Complete request with CancellationException on preemption in CoroutinePriorityQueue

**Checkpoint**: Graceful preemption error handling - clients receive clear error messages

---

## Phase 6: User Story 4 - Concurrency Monitoring (Priority: P3)

**Goal**: Operators can monitor slot usage and preemption events via Prometheus metrics.

**Independent Test**: Verify metrics are available at /actuator/prometheus.

### Tests for User Story 4
- [X] T026 [P] [US4] Unit test for preemption metrics in src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/metrics/PrometheusMetricsTest.kt
- [X] T027 [P] [US4] Integration test for metrics exposure in src/test/kotlin/ru/ddd/llmproxy/integration/MetricsIntegrationTest.kt

### Implementation for User Story 4
- [X] T028 [US4] Add concurrent_requests gauge metric in PrometheusMetrics in src/main/kotlin/ru/ddd/llmproxy/infrastructure/metrics/PrometheusMetrics.kt
- [X] T029 [US4] Add preemption_total counter metric in PrometheusMetrics
- [X] T030 [US4] Add p3_throttled_total counter metric in PrometheusMetrics
- [X] T031 [US4] Update ConcurrencyController to record metrics on state changes

**Checkpoint**: All monitoring metrics available for operations

---

## Phase 7: Polish & Cross-Cutting Concerns
- [X] T032 [P] Add application.yml configuration example with new settings in src/main/resources/application.yml
- [X] T033 [P] Run all existing tests to verify no regressions
- [X] T034 Validate quickstart.md scenarios work end-to-end
- [X] T035 [P] Add logging for preemption events in CoroutinePriorityQueue
- [X] T036 Code cleanup and remove any debug logging

**Checkpoint**: Feature complete and ready for deployment

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - US1 (P1) can start after Phase 2
  - US2 (P2) depends on Phase 2 + US1 infrastructure
  - US3 (P2) depends on US1 (needs preemption to work)
  - US4 (P3) depends on US1, US2, US3 (needs events to record)
- **Polish (Phase 7)**: Depends on all user stories

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models/value objects before services
- Services before integration
- Core implementation before error handling

### Parallel Opportunities

- All Foundational tasks T002-T006 can run in parallel
- Tests within a story can run in parallel
- US1 and US2 implementation can partially overlap (different aspects of ConcurrencyController)

---

## Parallel Example: Foundational Phase

```bash
# Launch all domain model tasks together:
Task: "Create ConcurrencyConfig value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/ConcurrencyConfig.kt"
Task: "Create RunningRequest entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/RunningRequest.kt"
Task: "Create PrioritySlot entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/PrioritySlot.kt"
Task: "Create PreemptionEvent domain event in src/main/kotlin/ru/ddd/llmproxy/domain/model/PreemptionEvent.kt"
Task: "Create ConcurrencyStats read model in src/main/kotlin/ru/ddd/llmproxy/domain/model/ConcurrencyStats.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002-T010)
3. Complete Phase 3: User Story 1 (T011-T016)
4. **STOP and VALIDATE**: Test P1 preemption independently
5. Deploy if ready - P1 preemption is the key differentiator

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy (MVP - P1 preemption!)
3. Add User Story 2 → Test independently → Deploy (P3 throttling)
4. Add User Story 3 → Test independently → Deploy (Graceful errors)
5. Add User Story 4 → Test independently → Deploy (Monitoring)
6. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All existing tests must pass (SC-005)
