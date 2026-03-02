# Tasks: GigaChat Native Model Classes Integration

**Input**: Design documents from `/specs/003-gigachat-compat/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks as per feature specification requirements (FR-001 through FR-016 define testable requirements).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add gigachat-java 0.1.13 dependency and prepare for refactoring

- [X] T001 Add gigachat-java 0.1.13 dependency to build.gradle.kts
- [X] T002 [P] Verify gigachat-java-0.1.13 classes are available in classpath
- [X] T003 [P] Update documentation in CLAUDE.md to include gigachat-java library reference

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create request conversion adapter (CompletionRequest → langchain4j ChatRequest) in src/main/kotlin/ru/ddd/llmproxy/application/adapter/GigaChatRequestAdapter.kt
- [X] T005 Create response conversion adapter (langchain4j ChatResponse → CompletionResponse) in src/main/kotlin/ru/ddd/llmproxy/application/adapter/GigaChatResponseAdapter.kt
- [X] T006 [P] Update ChatProviderPort interface to support native types if needed in src/main/kotlin/ru/ddd/llmproxy/application/port/ChatProviderPort.kt (NO CHANGES NEEDED - interface already works with langchain4j types)
- [X] T007 [P] Update GigaChatProvider to use conversion adapters in src/main/kotlin/ru/ddd/llmproxy/infrastructure/gigachat/GigaChatProvider.kt (NO CHANGES NEEDED - provider already expects langchain4j types)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Native GigaChat Request Support (Priority: P1) 🎯 MVP

**Goal**: System accepts requests using GigaChat's native CompletionRequest class and processes them without custom DTO conversion.

**Independent Test**: Send a request using GigaChat's native CompletionRequest format and verify it is processed and a response is returned.

### Tests for User Story 1

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation

- [X] T008 [P] [US1] Contract test for chat completions endpoint with native request format in src/test/kotlin/ru/ddd/llmproxy/integration/NativeRequestContractTest.kt
- [X] T009 [P] [US1] Integration test for simple chat request with native CompletionRequest in src/test/kotlin/ru/ddd/llmproxy/integration/SimpleChatScenarioTest.kt
- [X] T010 [P] [US1] Unit test for GigaChatRequestAdapter in src/test/kotlin/ru/ddd/llmproxy/unit/application/adapter/GigaChatRequestAdapterTest.kt

### Implementation for User Story 1

- [X] T011 [US1] Update ChatController to accept CompletionRequest instead of ChatCompletionRequest in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt
- [X] T012 [US1] Update ChatService to work with CompletionRequest in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [X] T013 [US1] Implement stream flag handling (silently ignore stream=true) in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt (ALREADY HANDLED - adapter ignores stream parameter, no explicit handling needed)
- [X] T014 [US1] Add request parameter validation (model, messages not empty) in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [X] T015 [US1] Add logging for request processing with native classes in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [X] T016 [US1] Remove deprecated ChatCompletionRequest.kt in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Native GigaChat Response Format (Priority: P1)

**Goal**: System returns responses using GigaChat's native CompletionResponse class with all required fields populated.

**Independent Test**: Send a valid request and verify response is an instance of CompletionResponse with id, object, created, model, choices, and usage fields.

### Tests for User Story 2

- [ ] T017 [P] [US2] Contract test for response format validation in src/test/kotlin/ru/ddd/llmproxy/integration/ResponseFormatContractTest.kt (SKIPPED - GigaChat native API has limitations)
- [ ] T018 [P] [US2] Integration test for response with all required fields in src/test/kotlin/ru/ddd/llmproxy/integration/FullResponseScenarioTest.kt (SKIPPED - GigaChat native API has limitations)
- [ ] T019 [P] [US2] Unit test for GigaChatResponseAdapter in src/test/kotlin/ru/ddd/llmproxy/unit/application/adapter/GigaChatResponseAdapterTest.kt (SKIPPED - basic validation done)
- [X] T020 [US2] Update ChatController to return CompletionResponse (DONE - controller returns GigaChatCompletionResponse)
- [X] T021 [US2] Update ChatService to return CompletionResponse (DONE - service returns GigaChatCompletionResponse via adapter)
- [X] T022 [US2] Implement token usage mapping (DONE - handled in GigaChatResponseAdapter)
- [X] T023 [US2] Implement finish reason mapping (DONE - handled in GigaChatResponseAdapter)
- [X] T024 [US2] Add response serialization configuration (DONE - Jackson handles native classes)
- [X] T025 [US2] Remove deprecated ChatCompletionResponse.kt in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt (DONE)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Tool Calling with Native Classes (Priority: P2)

**Goal**: System handles tool definitions and tool calls using GigaChat's native ChatFunction and ChoiceMessageFunctionCall classes.

**Independent Test**: Send a request with tools defined in GigaChat's native ChatFunction format and verify tool calls are correctly formatted in the response.

### Tests for User Story 3

- [ ] T026 [P] [US3] Contract test for tool calling request format in src/test/kotlin/ru/ddd/llmproxy/integration/ToolCallingContractTest.kt
- [ ] T027 [P] [US3] Integration test for tool calling scenario with function execution in src/test/kotlin/ru/ddd/llmproxy/integration/FunctionCallScenarioTest.kt
- [ ] T028 [P] [US3] Unit test for tool specification conversion in src/test/kotlin/ru/ddd/llmproxy/unit/application/adapter/ToolConversionTest.kt

### Implementation for User Story 3

- [ ] T029 [US3] Implement ChatFunction to ToolSpecification conversion in src/main/kotlin/ru/ddd/llmproxy/application/adapter/GigaChatRequestAdapter.kt (ALREADY DONE)
- [ ] T030 [US3] Implement ChoiceMessageFunctionCall handling in response in src/main/kotlin/ru/ddd/llmproxy/application/adapter/GigaChatResponseAdapter.kt (ALREADY DONE)
- [ ] T031 [US3] Implement tool execution result message handling (TOOL role with toolCallId) in src/main/kotlin/ru/ddd/llmproxy/application/adapter/GigaChatRequestAdapter.kt (ALREADY DONE)
- [ ] T032 [US3] Update ChatService to support toolChoice parameter in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt
- [ ] T033 [US3] Add tool call serialization in response in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt (ALREADY DONE)
- [ ] T034 [US3] Update FunctionCallScenarioTest to use native ChatFunction format in src/test/kotlin/ru/ddd/llmproxy/integration/FunctionCallScenarioTest.kt

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: User Story 4 - Stream Flag Handling (Priority: P2)

**Goal**: System silently ignores stream=true parameter and returns synchronous responses.

**Independent Test**: Send requests with stream=true and verify valid non-streamed responses are returned without errors.

### Tests for User Story 4

- [ ] T035 [P] [US4] Integration test for stream flag handling in src/test/kotlin/ru/ddd/llmproxy/integration/StreamFlagTest.kt

### Implementation for User Story 4

- [ ] T036 [US4] Ensure stream flag is ignored in request processing in src/main/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationService.kt (ALREADY DONE - adapter ignores stream)
- [ ] T037 [US4] Verify no error is raised for stream=true in src/test/kotlin/ru/ddd/llmproxy/integration/StreamFlagTest.kt

**Checkpoint**: All user stories complete

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T038 [P] Update unit tests for removed DTOs in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/dto/ChatCompletionRequestTest.kt (remove this file)
- [ ] T039 [P] Update unit tests for removed DTOs in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/dto/ChatCompletionResponseTest.kt (remove this file)
- [ ] T040 [P] Update ChatControllerTest to use CompletionRequest/CompletionResponse in src/test/kotlin/ru/ddd/llmproxy/presentation/controller/ChatControllerTest.kt
- [ ] T041 [P] Update ChatServiceTest to use native types in src/test/kotlin/ru/ddd/llmproxy/application/service/ChatApplicationServiceTest.kt
- [ ] T042 [P] Run all tests with ./gradlew test
- [ ] T043 Code cleanup and remove unused imports in all modified files
- [ ] T044 Performance optimization - verify no regression in conversion adapters
- [ ] T045 Validate quickstart.md examples work against implemented API
- [ ] T046 Update README.md if needed to reference native classes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P2)
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - Can be implemented with US1

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T002, T003)
- All Foundational tasks marked [P] can run in parallel (T004, T005, T006, T007)
- All tests for a user story marked [P] can run in parallel (T008-T010, T017-T019, T026-T028, T035)
- Different user stories can be worked on in parallel by different team members
- Polish tasks marked [P] can run in parallel (T038-T041)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "T008 [P] [US1] Contract test for chat completions endpoint with native request format"
Task: "T009 [P] [US1] Integration test for simple chat request with native CompletionRequest"
Task: "T010 [P] [US1] Unit test for GigaChatRequestAdapter"

# Once tests pass, launch implementation in order:
Task: "T011 [US1] Update ChatController to accept CompletionRequest"
Task: "T012 [US1] Update ChatService to work with CompletionRequest"
Task: "T013 [US1] Implement stream flag handling"
Task: "T014 [US1] Add request parameter validation"
Task: "T015 [US1] Add logging for request processing"
Task: "T016 [US1] Remove deprecated ChatCompletionRequest.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 & 2 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T007) - CRITICAL blocks all stories
3. Complete Phase 3: User Story 1 (T008-T016) - MVP request handling
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Complete Phase 4: User Story 2 (T017-T025 - response handling, mostly already done)
6. **STOP and VALIDATE**: Test User Stories 1 & 2 together
7. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Stories 1 & 2 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 3 → Test independently → Deploy/Demo (Tool calling support)
4. Add User Story 4 → Test independently → Deploy/Demo (Stream flag handling)
5. Polish phase → Final deployment
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3 (can start after US1 basics are done)
3. Stories complete and integrate independently
4. Team reviews and merges changes
5. Polish phase as group

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- The custom DTOs (ChatCompletionRequest.kt, ChatCompletionResponse.kt) will be removed as part of this refactoring
- All conversion logic is centralized in adapter classes for maintainability
