# Tasks: GigaChat API Compatibility

**Input**: Design documents from `/specs/002-gigachat-compat/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Tests are NOT explicitly requested in the specification. Existing tests should be updated as part of implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/kotlin/`, `src/test/kotlin/` at repository root
- Paths shown below follow the existing project structure

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization - already complete, existing project structure

No setup tasks required - this is an existing project with all infrastructure in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core DTO extensions that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T001 [P] Add Tool, FunctionDefinition, and PropertyDef data classes to ChatCompletionRequest.kt in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T002 [P] Add ToolCall and FunctionCall data classes to ChatCompletionResponse.kt in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt
- [ ] T003 [P] Add ToolCall and FunctionCall data classes to InvokeResponse.kt in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/InvokeResponse.kt
- [ ] T004 Add tool_calls field to ChatCompletionRequest.Message and tool_call_id field for tool result messages in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T005 Add tool_calls field to ChatCompletionResponse.Message in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt
- [ ] T006 Add tool_calls field to InvokeResponse.Message in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/InvokeResponse.kt

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Tool Calling Support (Priority: P1) 🎯 MVP

**Goal**: Enable developers to send chat requests with tools (functions) defined and receive responses with tool_calls when the model decides to call them.

**Independent Test**: Send a request with a tool definition (e.g., weather function) and verify the response correctly handles the model's decision to call or not call the tool.

### Implementation for User Story 1

- [ ] T007 [US1] Extend toChatRequest() to convert tools DTO to langchain4j ToolSpecification list in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T008 [US1] Handle role="assistant" messages with tool_calls in toChatRequest() conversion in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T009 [US1] Handle role="tool" messages with tool_call_id in toChatRequest() conversion in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T010 [US1] Update ChatCompletionResponse.from() to extract tool_calls from langchain4j AiMessage in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt
- [ ] T011 [US1] Update InvokeResponse.from() to extract tool_calls from langchain4j AiMessage in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/InvokeResponse.kt
- [ ] T012 [US1] Add finish_reason "tool_calls" handling in ChatCompletionResponse.from() in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt

**Checkpoint**: At this point, User Story 1 should be fully functional - tool calling works end-to-end

---

## Phase 4: User Story 2 - Graceful Stream Handling (Priority: P1)

**Goal**: Requests with stream=true should be silently ignored and return synchronous responses without errors.

**Independent Test**: Send a request with stream=true and verify the response is a valid non-streamed response without errors.

### Implementation for User Story 2

- [ ] T013 [US2] Remove stream validation exception from validateRequest() in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt
- [ ] T014 [US2] Add debug logging when stream=true is silently ignored in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt

**Checkpoint**: At this point, User Story 2 should be fully functional - stream=true requests work without errors

---

## Phase 5: User Story 3 - Full Request Format Compatibility (Priority: P2)

**Goal**: Accept all GigaChat-specific request fields including repetition_penalty, tool_choice, and other parameters.

**Independent Test**: Send requests with various GigaChat parameter combinations and verify they are accepted and processed.

### Implementation for User Story 3

- [ ] T015 [P] [US3] Add repetition_penalty field to ChatCompletionRequest in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [ ] T016 [P] [US3] Add tool_choice field to ChatCompletionRequest in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [x] T017 [US3] Update toChatRequest() to pass repetition_penalty via GigaChatChatRequestParameters in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt
- [x] T018 [US3] Update toChatRequest() to handle tool_choice parameter in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionRequest.kt

**Checkpoint**: At this point, User Story 3 should be fully functional - all GigaChat parameters accepted

---

## Phase 6: User Story 4 - Response Format Compatibility (Priority: P2)

**Goal**: Return responses in GigaChat's native format with all expected fields (id, object, created, model, choices, usage).

**Independent Test**: Send a request and verify the response structure matches GigaChat's documented format.

### Implementation for User Story 4

- [ ] T019 [P] [US4] Add object field with value "chat.completion" to ChatCompletionResponse in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/ChatCompletionResponse.kt
- [ ] T020 [P] [US4] Add object field with value "chat.completion" to InvokeResponse in src/main/kotlin/ru/ddd/llmproxy/presentation/dto/InvokeResponse.kt

**Checkpoint**: At this point, User Story 4 should be fully functional - response format matches GigaChat spec

---

## Phase 7: Cache Key Update (Cross-Cutting)

**Purpose**: Ensure cache keys include tool definitions for correct cache behavior

- [ ] T021 Update serializeChatRequest() to include tools in cache key generation in src/main/kotlin/ru/ddd/llmproxy/infrastructure/cache/CacheKeyGenerator.kt
- [x] T022 Update serializeChatRequest() to include tool_choice in cache key generation in src/main/kotlin/ru/ddd/llmproxy/infrastructure/cache/CacheKeyGenerator.kt

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T023 [P] Update FunctionCallScenarioTest to verify stream=true graceful handling in src/test/kotlin/ru/ddd/llmproxy/integration/FunctionCallScenarioTest.kt
- [ ] T024 [P] Update validateRequest() to allow messages with null content when tool_calls present in src/main/kotlin/ru/ddd/llmproxy/presentation/controller/ChatController.kt
- [ ] T025 [P] Add unit tests for ChatCompletionRequest DTO serialization in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/dto/ChatCompletionRequestTest.kt
- [ ] T026 [P] Add unit tests for ChatCompletionResponse DTO serialization in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/dto/ChatCompletionResponseTest.kt

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - already complete
- **Foundational (Phase 2)**: No dependencies - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - US1 (Tool Calling) and US2 (Stream Handling) can proceed in parallel
  - US3 (Request Format) and US4 (Response Format) can proceed in parallel
- **Cache Key (Phase 7)**: Depends on Foundational phase, can run in parallel with user stories
- **Polish (Phase 8)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - No dependencies on other stories

### Within Each User Story

- DTO modifications before conversion logic
- Conversion logic before response handling
- Core implementation before edge cases

### Parallel Opportunities

- T001, T002, T003 can run in parallel (different files)
- T015, T016 can run in parallel (same file, different fields)
- T019, T020 can run in parallel (different files)
- T023, T024, T025, T026 can run in parallel (different files)
- User stories US1 and US2 can run in parallel after foundational phase
- User stories US3 and US4 can run in parallel after foundational phase

---

## Parallel Example: Foundational Phase

```bash
# Launch all independent DTO additions together:
Task: "Add Tool, FunctionDefinition, PropertyDef data classes to ChatCompletionRequest.kt"
Task: "Add ToolCall, FunctionCall data classes to ChatCompletionResponse.kt"
Task: "Add ToolCall, FunctionCall data classes to InvokeResponse.kt"
```

## Parallel Example: User Stories 1 & 2

```bash
# After foundational phase, can work on both in parallel:
Developer A: User Story 1 (Tool Calling) - Tasks T007-T012
Developer B: User Story 2 (Stream Handling) - Tasks T013-T014
```

---

## Implementation Strategy

### MVP First (User Stories 1 & 2)

1. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
2. Complete Phase 3: User Story 1 (Tool Calling Support)
3. Complete Phase 4: User Story 2 (Graceful Stream Handling)
4. **STOP and VALIDATE**: Test tool calling and stream handling independently
5. Deploy/demo if ready - this covers the core P1 requirements

### Incremental Delivery

1. Complete Foundational → DTO structure ready
2. Add User Story 1 → Tool calling works → Deploy/Demo
3. Add User Story 2 → Stream graceful handling → Deploy/Demo
4. Add User Story 3 → Full request params → Deploy/Demo
5. Add User Story 4 → Full response format → Deploy/Demo
6. Add Cache Key + Polish → Production ready

### Parallel Team Strategy

With multiple developers:

1. Team completes Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (Tool Calling)
   - Developer B: User Story 2 (Stream Handling)
3. After P1 stories:
   - Developer A: User Story 3 (Request Format)
   - Developer B: User Story 4 (Response Format)
4. One developer: Cache Key updates in parallel
5. Team: Polish phase together

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- Tests are optional - existing tests updated as part of implementation
