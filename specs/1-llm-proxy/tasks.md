# Tasks: LLM Proxy

**Input**: Design documents from `/specs/1-llm-proxy/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Tests are NOT explicitly requested in the specification. Implementation tasks only.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/kotlin/ru/ddd/llmproxy/` for source
- **Tests**: `src/test/kotlin/ru/ddd/llmproxy/` for tests
- Based on plan.md structure with Clean Architecture layers

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Update build.gradle.kts with Spring Boot, Kotlin, and all dependencies per plan.md
- [x] T002 Create application.yml with all configuration properties in src/main/resources/application.yml
- [x] T003 [P] Create domain directory structure src/main/kotlin/ru/ddd/llmproxy/domain/model/
- [x] T004 [P] Create application directory structure src/main/kotlin/ru/ddd/llmproxy/application/
- [x] T005 [P] Create infrastructure directory structure src/main/kotlin/ru/ddd/llmproxy/infrastructure/
- [x] T006 [P] Create presentation directory structure src/main/kotlin/ru/ddd/llmproxy/presentation/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Layer (No External Dependencies)

- [x] T007 [P] Create Priority value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/Priority.kt
- [x] T008 [P] Create QueueMetrics entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/QueueMetrics.kt
- [x] T009 [P] Create CacheKey value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/CacheKey.kt
- [x] T010 [P] Create PrioritySlot config value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/PrioritySlot.kt
- [x] T011 [P] Create QueuedRequest entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/QueuedRequest.kt
- [x] T012 [P] Create CacheRepository interface in src/main/kotlin/ru/ddd/llmproxy/domain/repository/CacheRepository.kt
- [x] T013 [P] Create QueueService interface in src/main/kotlin/ru/ddd/llmproxy/domain/service/QueueService.kt

### Application Layer Ports

- [x] T014 [P] Create ChatProviderPort interface in src/main/kotlin/ru/ddd/llmproxy/application/port/ChatProviderPort.kt
- [x] T015 [P] Create MetricsPort interface in src/main/kotlin/ru/ddd/llmproxy/application/port/MetricsPort.kt
- [x] T016 Create PriorityResolver in src/main/kotlin/ru/ddd/llmproxy/application/service/PriorityResolver.kt

### Infrastructure Layer Configuration

- [x] T017 [P] Create LlmProxyProperties in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/LlmProxyProperties.kt
- [x] T018 [P] Create CoroutineConfig in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/CoroutineConfig.kt
- [x] T019 Create CacheKeyGenerator in src/main/kotlin/ru/ddd/llmproxy/infrastructure/cache/CacheKeyGenerator.kt
- [x] T020 Create CaffeineCacheRepository in src/main/kotlin/ru/ddd/llmproxy/infrastructure/cache/CaffeineCacheRepository.kt

### Error Handling Infrastructure

- [x] T021 Create ErrorResponse data class in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/ErrorResponse.kt
- [x] T022 Create GlobalExceptionHandler in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/GlobalExceptionHandler.kt
- [x] T023 Create custom exceptions (QueueOverflowError, InvalidRequestError, ProviderError) in src/main/kotlin/ru/ddd/llmproxy/domain/model/Exceptions.kt

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Basic Chat Completion (Priority: P1) 🎯 MVP

**Goal**: Клиент может отправлять запросы к LLM через прокси-сервер и получать ответы

**Independent Test**: POST `/v1/chat/completions` с телом `{messages: [{role: "user", content: "Hello"}]}` возвращает ответ от GigaChat

### Infrastructure for US1

- [x] T024 [US1] Create GigaChatProvider implementing ChatProviderPort in src/main/kotlin/ru/ddd/llmproxy/infrastructure/gigachat/GigaChatProvider.kt

### Application Service for US1

- [x] T025 [US1] Create ChatApplicationService in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt

### Presentation for US1

- [x] T026 [P] [US1] Create RequestIdMiddleware in src/main/kotlin/ru/ddd/llmproxy/presentation/middleware/RequestIdMiddleware.kt
- [x] T027 [US1] Create ChatController with POST /v1/chat/completions endpoint in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt

**Checkpoint**: At this point, User Story 1 should be fully functional - basic chat completion works

---

## Phase 4: User Story 2 - Priority Queue Management (Priority: P1)

**Goal**: Критические запросы обрабатываются быстрее обычных через очередь приоритетов

**Independent Test**: Отправка запросов с разными приоритетами через заголовок `X-Priority` показывает разный порядок обработки

### Infrastructure for US2

- [x] T028 [US2] Create CoroutinePriorityQueue implementing QueueService in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt

### Application Service for US2

- [x] T029 [US2] Update ChatApplicationService to use priority queue in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T030 [US2] Add queue overflow handling and error response in src/main/kotlin/ru/ddd/llmproxy/presentation/exception/GlobalExceptionHandler.kt

### Presentation for US2

- [x] T031 [US2] Add X-Priority header support to ChatController in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt
- [x] T032 [US2] Add response headers (x-llm-proxy-queue-wait-ms, x-llm-proxy-priority) in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - priority queue is functional

---

## Phase 5: User Story 3 - Response Caching (Priority: P2)

**Goal**: Повторные запросы получают кэшированные ответы для сокращения задержки

**Independent Test**: Отправка одинакового запроса дважды показывает, что второй ответ пришёл из кэша

### Application Service for US3

- [x] T033 [US3] Update ChatApplicationService to use cache with CacheKeyGenerator in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T034 [US3] Add cache hit/miss logging in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt

**Checkpoint**: At this point, caching is functional and reduces API calls

---

## Phase 6: User Story 4 - Observability & Metrics (Priority: P2)

**Goal**: Метрики работы прокси доступны в формате Prometheus

**Independent Test**: GET `/metrics` возвращает метрики в текстовом формате Prometheus

### Infrastructure for US4

- [x] T035 [US4] Create PrometheusMetrics implementing MetricsPort in src/main/kotlin/ru/ddd/llmproxy/infrastructure/metrics/PrometheusMetrics.kt
- [x] T036 [US4] Configure Micrometer Prometheus registry in src/main/kotlin/ru/ddd/llmproxy/infrastructure/config/MetricsConfig.kt

### Application for US4

- [x] T037 [US4] Integrate PrometheusMetrics into ChatApplicationService in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [x] T038 [US4] Add queue metrics recording to CoroutinePriorityQueue in src/main/kotlin/ru/ddd/llmproxy/infrastructure/queue/CoroutinePriorityQueue.kt

### Presentation for US4

- [x] T039 [US4] Create MetricsController with GET /metrics endpoint in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/MetricsController.kt

**Checkpoint**: At this point, observability is functional - metrics available

---

## Phase 7: User Story 5 - Chat Invoke with Metrics (Priority: P3)

**Goal**: Метрики выполнения включаются в тело ответа

**Independent Test**: POST `/v1/chat/invoke` возвращает ответ с полем `metrics` в теле

### Presentation for US5

- [x] T040 [US5] Add POST /v1/chat/invoke endpoint to ChatController in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt
- [x] T041 [US5] Create InvokeResponse wrapper with metrics in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/InvokeResponse.kt

**Checkpoint**: At this point, chat invoke with metrics is functional

---

## Phase 8: User Story 6 - Health Check (Priority: P3)

**Goal**: Проверка здоровья сервиса через специальный эндпоинт

**Independent Test**: GET `/health` возвращает `{status: "ok", env: <environment>}`

### Presentation for US6

- [x] T042 [US6] Create HealthController with GET /health endpoint in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/HealthController.kt

**Checkpoint**: At this point, all user stories are complete

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T043 [P] Add KDoc documentation to all public APIs in domain layer
- [ ] T044 [P] Add KDoc documentation to all public APIs in application layer
- [ ] T045 Verify all acceptance scenarios from spec.md work correctly
- [ ] T046 Run quickstart.md validation - all curl examples work
- [x] T047 Add README.md with project overview and quick start

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-8)**: All depend on Foundational phase completion
  - US1 (P1): Basic Chat - can start after Phase 2
  - US2 (P1): Priority Queue - depends on US1 GigaChatProvider
  - US3 (P2): Caching - depends on US1 ChatApplicationService
  - US4 (P2): Metrics - independent, can run in parallel with US3
  - US5 (P3): Chat Invoke - depends on US1 and US4
  - US6 (P3): Health Check - independent, can run anytime after Phase 2
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories
- **US2 (P1)**: Depends on US1 GigaChatProvider existing
- **US3 (P2)**: Depends on US1 ChatApplicationService existing
- **US4 (P2)**: No dependencies - can run in parallel
- **US5 (P3)**: Depends on US1 controller and US4 metrics
- **US6 (P3)**: No dependencies - can run anytime

### Within Each User Story

- Infrastructure before application
- Application before presentation
- Core implementation before integration

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational domain models (T007-T015) can run in parallel
- US4 (Metrics) can run in parallel with US3 (Caching)
- US6 (Health) can run in parallel with US3, US4, US5

---

## Parallel Example: Foundational Phase

```bash
# Launch all domain models in parallel:
Task: "Create Priority value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/Priority.kt"
Task: "Create QueueMetrics entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/QueueMetrics.kt"
Task: "Create CacheKey value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/CacheKey.kt"
Task: "Create PrioritySlot config value object in src/main/kotlin/ru/ddd/llmproxy/domain/model/PrioritySlot.kt"
Task: "Create QueuedRequest entity in src/main/kotlin/ru/ddd/llmproxy/domain/model/QueuedRequest.kt"
Task: "Create CacheRepository interface in src/main/kotlin/ru/ddd/llmproxy/domain/repository/CacheRepository.kt"
Task: "Create QueueService interface in src/main/kotlin/ru/ddd/llmproxy/domain/service/QueueService.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 (Basic Chat)
4. Complete Phase 4: User Story 2 (Priority Queue)
5. **STOP and VALIDATE**: Test chat with priority queue
6. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add US1 (Basic Chat) → Test → Deploy (MVP!)
3. Add US2 (Priority Queue) → Test → Deploy
4. Add US3 (Caching) → Test → Deploy
5. Add US4 (Metrics) → Test → Deploy
6. Add US5 + US6 (Convenience) → Test → Deploy
7. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 → US2 → US5
   - Developer B: US3 (Caching)
   - Developer C: US4 (Metrics) → US6 (Health)
3. Stories complete and integrate independently

---

## Task Summary

| Phase | Tasks | Parallelizable |
|-------|-------|----------------|
| Phase 1: Setup | 6 | 4 |
| Phase 2: Foundational | 17 | 12 |
| Phase 3: US1 Basic Chat | 4 | 1 |
| Phase 4: US2 Priority Queue | 5 | 0 |
| Phase 5: US3 Caching | 2 | 0 |
| Phase 6: US4 Metrics | 5 | 0 |
| Phase 7: US5 Chat Invoke | 2 | 0 |
| Phase 8: US6 Health | 1 | 0 |
| Phase 9: Polish | 5 | 2 |
| **Total** | **47** | **19** |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All tasks follow the checklist format with ID, optional [P], optional [Story], and file path
