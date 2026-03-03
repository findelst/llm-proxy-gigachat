# Tasks: Queue Timeout & Cleanup

**Input**: Design documents from `/specs/006-queue-timeout/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Tests**: Unit tests required for timeout functionality

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: Verify current state and prepare for implementation

- [X] T001 Verify all existing tests pass with `./gradlew test`
- [X] T002 Create feature branch from main: `git checkout -b 006-queue-timeout`

---

## Phase 2: Foundational

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 [P] Add `QueueTimeoutException` class to `src/main/kotlin/ru/ddd/llmproxy/domain/model/Exceptions.kt`
- [X] T004 [P] Add timeout configuration fields to `src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/LlmProxyProperties.kt`
- [X] T005 Add timeout configuration to `src/main/resources/application.yml`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Expired Request Cleanup (Priority: P1) 🎯 MVP

**Goal**: Удалять запросы из очереди если они ждут дольше timeout и возвращать понятную ошибку

**Independent Test**: Добавить запрос в очередь, подождать timeout, проверить что получена ошибка timeout

### Implementation for User Story 1

- [X] T006 [US1] Add `recordQueueTimeout(priority: Priority)` method to `src/main/kotlin/ru/ddd/llmproxy/application/port/MetricsPort.kt`
- [X] T007 [US1] Implement `llm_proxy_queue_timeout_total` counter in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/metrics/PrometheusMetrics.kt`
- [X] T008 [US1] Add `removeExpired(maxAgeMs: Long, onExpired: (T) -> Unit): Int` method to `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/PriorityChannel.kt`
- [X] T009 [US1] Add `startTimeoutCleanup()` method with periodic cleanup coroutine to `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [X] T010 [US1] Add `QueueTimeoutException` handler returning 408 status to `src/main/kotlin/ru/ddd/llmproxy/presentation/exception/GlobalExceptionHandler.kt`
- [X] T011 [US1] Call `startTimeoutCleanup()` in init block of `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [X] T012 [US1] Run tests to verify timeout cleanup works: `./gradlew test`

**Checkpoint**: User Story 1 complete - Requests timeout after configured wait time

---

## Phase 4: User Story 2 - Configurable Timeout (Priority: P2)

**Goal**: Allow operators to configure timeout value via configuration

**Independent Test**: Set different timeout values and verify cleanup respects them

### Implementation for User Story 2

- [X] T013 [US2] Verify timeout config is read correctly from `LlmProxyProperties` in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [X] T014 [US2] Add validation for timeout config (>=0) in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/LlmProxyProperties.kt`
- [X] T015 [US2] Add environment variables `LLM_QUEUE_TIMEOUT_MINUTES` and `LLM_QUEUE_TIMEOUT_CHECK_MS` to `src/main/resources/application.yml`
- [X] T016 [US2] Test with timeout=0 (disabled) verifies requests wait forever in `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/QueueTimeoutTest.kt`

**Checkpoint**: User Story 2 complete - Timeout is configurable

---

## Phase 5: User Story 3 - Timeout Metrics (Priority: P3)

**Goal**: Provide metrics for monitoring timeout events

**Independent Test**: Trigger timeout and verify metrics are recorded

### Implementation for User Story 3

- [X] T017 [US3] Verify `recordQueueTimeout` increments counter with priority tag in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/metrics/PrometheusMetrics.kt`
- [X] T018 [US3] Add timeout metrics to `/actuator/prometheus` endpoint verification in `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/QueueTimeoutTest.kt`
- [X] T019 [US3] Add log message for timeout events with priority and wait time in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`

**Checkpoint**: User Story 3 complete - Timeout metrics available

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup and verification

- [X] T020 Run full test suite: `./gradlew test`
- [X] T021 [P] Create `QueueTimeoutTest.kt` with comprehensive timeout tests in `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/QueueTimeoutTest.kt`
- [X] T022 Update `quickstart.md` with timeout documentation in `specs/006-queue-timeout/quickstart.md`
- [X] T023 Commit changes with message: `feat(queue): add timeout cleanup for queued requests`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - US1 must complete first (core timeout functionality)
  - US2 depends on US1 (configuration uses timeout mechanism)
  - US3 depends on US1 (metrics recorded during timeout)
- **Polish (Phase 6)**: Depends on all user stories complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - Core cleanup mechanism
- **User Story 2 (P2)**: Depends on US1 - Configuration
- **User Story 3 (P3)**: Depends on US1 - Metrics

### Within Each User Story

- Configuration/Exception first
- Core implementation
- Integration
- Tests

### Parallel Opportunities

- T003 and T004 can run in parallel (different files)
- T006 and T008 can run in parallel (different files)
- T017 and T019 can run in parallel (different concerns)

---

## Parallel Example: Foundational Phase

```bash
# Launch foundational tasks in parallel:
Task: "Add QueueTimeoutException to Exceptions.kt"
Task: "Add timeout config to LlmProxyProperties.kt"
# Then sequential: T005 application.yml
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T005)
3. Complete Phase 3: User Story 1 (T006-T012)
4. **STOP and VALIDATE**: Test timeout works
5. Deploy if ready

### Incremental Delivery

1. Setup → Verify current state
2. Foundational → Exception + Config
3. US1 → Core timeout mechanism → Test
4. US2 → Configuration flexibility → Test
5. US3 → Metrics → Test
6. Polish → Final cleanup

---

## Summary

| Metric | Count |
|--------|-------|
| Total Tasks | 23 |
| Setup Tasks | 2 |
| Foundational Tasks | 3 |
| US1 Tasks | 7 |
| US2 Tasks | 4 |
| US3 Tasks | 3 |
| Polish Tasks | 4 |
| Parallel Opportunities | 6 |

**MVP Scope**: T001-T012 (Setup + Foundational + US1)

---

## Notes

- Timeout uses existing `QueueMetrics.queuedAt` field
- New exception `QueueTimeoutException` returns HTTP 408 (not 429)
- Cleanup runs every 1 minute by default
- Timeout = 0 disables cleanup (requests wait forever)
- Tests must verify race condition: request picked for processing during cleanup