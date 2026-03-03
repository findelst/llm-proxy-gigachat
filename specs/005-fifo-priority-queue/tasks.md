# Tasks: FIFO Priority Queue

**Input**: Design documents from `/specs/005-fifo-priority-queue/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Tests**: Tests are implicitly required (SC-005: all existing tests must pass)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: Verify current state and prepare for refactoring

- [x] T001 Verify all existing tests pass with `./gradlew test`
- [x] T002 Create feature branch from main: `git checkout -b 005-fifo-priority-queue`

---

## Phase 2: Foundational

**Purpose**: No foundational changes needed - existing PriorityChannel and PrioritizedItem are already correct

**⚠️ NOTE**: This is a refactoring task. The existing infrastructure is sufficient.

**Checkpoint**: Foundation already exists - proceed directly to user story implementation

---

## Phase 3: User Story 1 - Strict FIFO Processing Within Priority (Priority: P1) 🎯 MVP

**Goal**: Удалить per-priority семафоры, сохранив FIFO упорядочивание

**Independent Test**: Отправить запросы A, B, C с одинаковым приоритетом и проверить порядок обработки

### Implementation for User Story 1

- [x] T003 [US1] Remove `prioritySemaphores: Map<Priority, Semaphore>` field from `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T004 [US1] Remove `Semaphore` import from `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T005 [US1] Remove `semaphore.acquire()` and `semaphore.release()` calls from `processRequest()` in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T006 [US1] Remove `prioritySlots` field from `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt` (keep local variable in init if needed for logging)
- [x] T007 [US1] Simplify `processRequest()` method - remove semaphore parameter in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T008 [US1] Update init block to remove semaphore initialization in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T009 [US1] Run tests to verify FIFO ordering still works: `./gradlew test`

**Checkpoint**: User Story 1 complete - FIFO processing works without semaphores

---

## Phase 4: User Story 2 - Global Priority Ordering (Priority: P2)

**Goal**: Убедиться что глобальное упорядочивание по приоритетам работает корректно

**Independent Test**: Отправить P3 запрос, затем P1, проверить что P1 обработан первым

### Tests for User Story 2

- [x] T010 [P] [US2] Verify priority ordering test exists in `src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt`
- [x] T011 [US2] Update `should process higher priority requests first` test if needed in `src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt`

### Implementation for User Story 2

- [x] T012 [US2] Verify PriorityChannel correctly orders by priority.level in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/PriorityChannel.kt` (no changes needed, just verify)
- [x] T013 [US2] Run priority ordering tests: `./gradlew test --tests "*PriorityQueueConcurrencyTest*"`

**Checkpoint**: User Story 2 complete - Priority ordering verified

---

## Phase 5: User Story 3 - Simplified Configuration (Priority: P3)

**Goal**: Убрать `priority-slots` из конфигурации, добавить warning при использовании

**Independent Test**: Запустить приложение без `priority-slots` в конфигурации

### Implementation for User Story 3

- [x] T014 [US3] Mark `prioritySlots` field as `@Deprecated` in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/LlmProxyProperties.kt`
- [x] T015 [US3] Add warning log in init block if `prioritySlots` is configured in `src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt`
- [x] T016 [US3] Remove `priority-slots` section from `src/main/resources/application.yml`
- [x] T017 [US3] Remove `LLM_PRIORITY_SLOTS_*` environment variables from `src/main/resources/application.yml`
- [x] T018 [US3] Update `CLAUDE.md` to reflect configuration changes

**Checkpoint**: User Story 3 complete - Configuration simplified

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup and verification

- [x] T019 Run full test suite: `./gradlew test`
- [x] T020 [P] Update `PriorityChannelTest.kt` if any tests assume semaphore behavior in `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/PriorityChannelTest.kt`
- [x] T021 [P] Remove or update semaphore-related tests in `src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt`
- [x] T022 Verify application starts correctly: `./gradlew bootRun`
- [ ] T023 Commit changes with message: `refactor(queue): remove per-priority semaphores for strict FIFO ordering`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Not needed - skip
- **User Stories (Phase 3-5)**: Depend on Setup completion
  - US1 must complete first (removes semaphores)
  - US2 depends on US1 (verifies ordering still works)
  - US3 depends on US1 (updates config)
- **Polish (Phase 6)**: Depends on all user stories complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Setup - Core refactoring
- **User Story 2 (P2)**: Depends on US1 - Verification
- **User Story 3 (P3)**: Can start in parallel with US2 after US1 complete

### Within Each User Story

- Remove semaphores first (T003-T008)
- Verify tests pass (T009)
- Then proceed to next story

### Parallel Opportunities

- T010 and T012-T013 can run in parallel (different concerns)
- T020 and T021 can run in parallel (different test files)

---

## Parallel Example: User Story 1 + 2 + 3 Combined

```bash
# After T001-T002 (Setup):
# Sequential: T003 → T004 → T005 → T006 → T007 → T008 (same file)
# Then: T009 (verify)

# After US1 complete:
# Parallel: T010 + T014 (different files)
# Sequential within each story
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 3: User Story 1 (T003-T009)
3. **STOP and VALIDATE**: Run tests, verify FIFO works
4. Deploy if ready

### Incremental Delivery

1. Setup → Verify current state
2. US1 → Remove semaphores → Test
3. US2 → Verify priority ordering → Test
4. US3 → Simplify config → Test
5. Polish → Final cleanup

---

## Summary

| Metric | Count |
|--------|-------|
| Total Tasks | 23 |
| Setup Tasks | 2 |
| US1 Tasks | 7 |
| US2 Tasks | 4 |
| US3 Tasks | 5 |
| Polish Tasks | 5 |
| Parallel Opportunities | 4 |

**MVP Scope**: T001-T009 (Setup + US1)

---

## Notes

- This is a refactoring task - no new entities needed
- PriorityChannel and PrioritizedItem are already correct
- Focus on removing code, not adding
- All existing tests must pass (SC-005)
- Commit after each phase completion
