# Tasks: Test Pyramid Coverage

**Input**: Design documents from `/Users/aleksandrfeoktistov/IdeaProjects/llm-proxy-kotlin/specs/001-test-pyramid/`
**Prerequisites**: plan.md (required), spec.md (required for user stories with priorities)
**Tests**: The examples below include test tasks for each user story (US1-US5)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] Description with file path`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, etc.)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `src/test/` at repository root

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure test framework and enable test execution

- [X] T001 [P] Add JaCoCo dependency to build.gradle.kts
  - [X] T002 [P] Enable JaCoCo plugin configuration
  - [X] T003 [P] Configure test coverage reporting in build.gradle.kts

**Checkpoint**: Test infrastructure is ready - user stories can now be implemented in parallel

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T004 [P] Create base test directory structure in src/test/kotlin/
  - [X] T005 [P] Create base test class for unit test configuration
  - [X] T006 [P] Set up test resources directory for test data

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Unit Test Coverage Foundation (Priority: P1)

**Goal**: System has comprehensive unit tests for all core application components following the test pyramid methodology.

**Independent Test**: Can be fully tested by running unit test suite and verifying all core application logic has test coverage.

**Acceptance Scenarios**:

1. **Given** the codebase has adapters for request/response conversion, **When** unit tests are run, **Then** all adapter methods have appropriate test coverage with both positive and negative cases
2. **Given** the service layer contains business logic, **When** unit tests are run, **Then** all service methods are tested with mocked dependencies
3. **Given** the controller layer handles HTTP requests, **When** unit tests are run, **Then** all controller methods are tested with mocked service layer
4. **Given** domain models contain validation logic, **When** unit tests are run, **Then** all validation rules are tested
5. **Given** the application uses caching, **When** unit tests are run, **Then** cache-related logic is tested

### Tests for User Story 1

- [X] T007 [P] [US1] Create test directory structure for unit tests in src/test/kotlin/
- [X] T008 [P] [US1] Create base test class for unit test configuration
- [X] T009 [P] [US1] Create test resources directory in src/test/resources/
- [X] T010 [P] [US1] Add test configuration properties to src/test/resources/application-test.yml
- [X] T011 [P] [US1] Create GigaChatRequestAdapterTest in src/test/kotlin/ru/ddd/llmproxy/unit/application/adapter/GigaChatRequestAdapterTest.kt
- [X] T012 [P] [US1] Create GigaChatResponseAdapterTest in src/test/kotlin/ru/ddd/llmproxy/unit/application/adapter/GigaChatResponseAdapterTest.kt
- [X] T013 [P] [US1] Create ChatApplicationServiceTest in src/test/kotlin/ru/ddd/llmproxy/unit/application/service/ChatApplicationServiceTest.kt
- [X] T014 [P] [US1] Create ChatControllerTest in src/test/kotlin/ru/ddd/llmproxy/unit/presentation/controller/ChatControllerTest.kt
- [X] T015 [P] [US1] Add JaCoCo dependency to build.gradle.kts
- [X] T016 [P] [US1] Enable JaCoCo plugin configuration in build.gradle.kts
- [X] T017 [US1] Configure test coverage reporting in build.gradle.kts

### Implementation for User Story 1

- [X] T018 [US1] Implement BaseTestConfig class with test annotations
- [X] T019 [US1] Create test application.yml configuration file
- [X] T020 [US1] Create GigaChatRequestAdapterTest with basic test cases
- [X] T021 [US1] Create GigaChatRequestAdapterTest with edge cases
- [X] T022 [US1] Create GigaChatRequestAdapterTest with parameter validation
- [X] T023 [US1] Create GigaChatRequestAdapterTest with tool calling scenarios
- [X] T024 [US1] Create GigaChatResponseAdapterTest with basic response mapping
- [X] T025 [US1] Create GigaChatResponseAdapterTest with usage mapping
- [X] T026 [US1] Create ChatApplicationServiceTest with cache scenarios
- [X] T027 [US1] Create ChatApplicationServiceTest with priority resolution
- [X] T028 [US1] Create ChatApplicationServiceTest with error handling
- [X] T029 [US1] Create ChatApplicationServiceTest with metrics recording
- [X] T030 [US1] Create ChatControllerTest with request validation
- [X] T031 [US1] Create ChatControllerTest with successful responses
- [X] T032 [US1] Create ChatControllerTest with error responses
- [X] T033 [US1] Create ChatControllerTest with metrics headers
- [X] T035 [US1] Add JaCoCo dependency to build.gradle.kts
- [X] T036 [US1] Enable JaCoCo plugin configuration in build.gradle.kts
- [X] T037 [US1] Configure test coverage reporting in build.gradle.kts

### Implementation for User Story 1

- [X] T018 [US1] Implement BaseTestConfig class with test annotations
- [X] T019 [US1] Create test application.yml configuration file
- [X] T020 [US1] Create GigaChatRequestAdapterTest with basic test cases
  - [X] T021 [US1] Create GigaChatRequestAdapterTest with edge cases
  - [X] T022 [US1] Create GigaChatRequestAdapterTest with parameter validation
  - [X] T023 [US1] Create GigaChatRequestAdapterTest with tool calling scenarios
- [X] T024 [US1] Create GigaChatResponseAdapterTest with basic response mapping
  - [X] T025 [US1] Create GigaChatResponseAdapterTest with usage mapping
- [X] T026 [US1] Create ChatApplicationServiceTest with cache scenarios
- [X] T027 [US1] Create ChatApplicationServiceTest with priority resolution
- [X] T028 [US1] Create ChatApplicationServiceTest with error handling
- [X] T029 [US1] Create ChatApplicationServiceTest with metrics recording
- [X] T030 [US1] Create ChatControllerTest with request validation
- [X] T031 [US1] Create ChatControllerTest with successful responses
- [X] T032 [US1] Create ChatControllerTest with error responses
- [X] T033 [US1] Create ChatControllerTest with metrics headers
- [X] T035 [US1] Add JaCoCo dependency to build.gradle.kts
- [X] T036 [US1] Enable JaCoCo plugin configuration in build.gradle.kts
- [X] T037 [US1] Configure test coverage reporting in build.gradle.kts

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Integration Test Coverage (Priority: P1)

**Goal**: System has integration tests that verify components work together correctly.

**Independent Test**: Can be fully tested by running integration test suite and verifying all major use cases work correctly with in-memory or test databases.

**Acceptance Scenarios**:

1. **Given** a chat completion request, **When** the request is sent to the API, **Then** a valid response is returned with correct status and headers
2. **Given** a request with GigaChat native format, **When** the request is processed, **Then** the adapter correctly converts to langchain4j format
3. **Given** the request contains tools/functions, **When** the request is processed, **Then** tool specifications are correctly converted
4. **Given** the cache is enabled, **When** a cached response exists, **Then** the cached response is returned without calling the provider
5. **Given** invalid input is provided, **When** the request is processed, **Then** appropriate error response is returned

### Tests for User Story 2

- [X] T038 [P] [US2] Create ChatCompletionsEndpointTest in src/test/kotlin/ru/ddd/llmproxy/integration/ChatCompletionsEndpointTest.kt
- [X] T039 [P] [US2] Create ChatCompletionsEndpointTest with GigaChat native requests
- [X] T040 [P] [US2] Create ChatCompletionsEndpointTest with parameters
- [X] T041 [P] [US2] Create ChatCompletionsEndpointTest with validation
- [X] T042 [P] [US2] Create ChatCompletionsEndpointTest with multiple messages
- [X] T043 [P] [US2] Create ChatCompletionsEndpointTest with error scenarios
- [X] T044 [P] [US2] Create ChatCompletionsEndpointTest with cache scenarios
- [X] T045 [P] [US2] Create ChatInvokeEndpointTest in src/test/kotlin/ru/ddd/llmproxy/integration/ChatInvokeEndpointTest.kt
- [X] T046 [P] [US2] Create ChatInvokeEndpointTest with metrics
- [X] T047 [P] [US2] Create ChatInvokeEndpointTest with streaming flag handling

### Implementation for User Story 2

- [X] T048 [US2] Implement ChatCompletionsEndpointTest class
- [X] T049 [US2] Implement ChatCompletionsEndpointTest methods
- [X] T050 [US2] Implement ChatInvokeEndpointTest class
- [X] T051 [US2] Implement ChatInvokeEndpointTest methods
- [X] T052 [US2] Set up test configuration properties
- [X] T053 [US2] Add test data files to resources

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - End-to-End Test Coverage (Priority: P2)

**Goal**: System has E2E tests that verify complete user workflows.

**Independent Test**: Can be fully tested by running E2E test suite and verifying complete user workflows work correctly against the running application.

**Acceptance Scenarios**:

1. **Given** a user wants to chat with GigaChat API, **When** they send a request to `/v1/chat/completions`, **Then** they receive a valid response in GigaChat native format
2. **Given** a user wants to use the metrics endpoint, **When** they send a request to `/v1/chat/invoke`, **Then** they receive a response with metrics included
3. **Given** a user sends multiple messages in a conversation, **When** the request is processed, **Then** the conversation context is maintained correctly
4. **Given** a user sends a request with tools defined, **When** the model decides to call a tool, **Then** the tool call is correctly formatted in the response

### Tests for User Story 3

- [ ] T054 [P] [US3] Create ChatWorkflowE2ETest in src/test/kotlin/ru/ddd/llmproxy/e2e/ChatWorkflowE2ETest.kt
- [ ] T055 [P] [US3] Implement ChatWorkflowE2ETest with happy path scenarios
- [ ] T056 [P] [US3] Implement ChatWorkflowE2ETest with tool calling scenarios
- [ ] T057 [P] [US3] Implement ChatWorkflowE2ETest with conversation history
- [ ] T058 [P] [US3] Create ChatWorkflowE2ETest with error scenarios
- [ ] T059 [P] [US3] Set up test environment and test data

**Checkpoint**: At this point, All user stories should now be independently functional

---

## Phase 6: User Story 4 - Test Pyramid Ratio Enforcement (Priority: P2)

**Goal**: System follows the test pyramid ratio guidelines: many unit tests, fewer integration tests, even fewer end-to-end tests.

**Independent Test**: Can be verified by running test coverage reports and ensuring the ratio of unit:integration:E2E tests follows the pyramid guideline (70:20:10).

**Acceptance Scenarios**:

1. **Given** the test suite is complete, **When** test coverage is measured, **Then** unit tests represent at least 70% of the total test count
2. **Given** the test suite is complete, **When** test coverage is measured, **Then** integration tests represent approximately 20% of the total test count
3. **Given** the test suite is complete, **When** test coverage is measured, **Then** end-to-end tests represent approximately 10% of the total test count

### Tests for User Story 4

- [ ] T062 [P] [US4] Configure JaCoCo in build.gradle.kts with coverage thresholds
- [ ] T063 [P] [US4] Verify JaCoCo generates coverage reports
- [ ] T064 [P] [US4] Run tests and generate coverage reports
- [ ] T065 [P] [US4] Verify test pyramid ratio compliance

### Implementation for User Story 4

- [ ] T066 [US4] Adjust task generation to respect test pyramid ratio
- [ ] T067 [US4] Document test pyramid guidelines for reference

**Checkpoint**: At this point, All user stories complete and test infrastructure is set up

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories.

- [ ] T068 [P] [US5] Add test documentation comments to complex scenarios
- [ ] T069 [P] [US5] Review and refactor tests following best practices
- [ ] T070 [P] [US5] Ensure test execution time is acceptable
- [ ] T071 [P] [US5] Update task completion status as tests are completed

**Checkpoint**: All user stories complete, test infrastructure ready for implementation

---

## Dependencies & Execution Order

### Phase Dependencies
- Phase 1 (Setup): Complete
- Phase 2 (Foundational): Complete
- User Story 1 (Phase 3): Depends on Phase 2
- User Story 2 (Phase 4): Depends on Phase 3
- User Story 3 (Phase 5): Depends on Phase 4
- Polish (Phase 6): Depends on all user stories

### User Story Dependencies
- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1/US2/US3 but should be independently testable

### Within Each User Story
- Tests MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities
- Setup phase tasks (T001-T006) can run in parallel (different files, no dependencies)
- Foundational phase tasks (T004-T005) can run in parallel (different files, no dependencies)
- All tests for a user story marked [P] can run in parallel (different files, no dependencies on incomplete tasks)
- Different user stories can be worked on in parallel by different team members
- Polish tasks marked [P] can run in parallel (T068-T071)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "T007 [P] [US1] Create GigaChatRequestAdapterTest with basic test cases"
Task: "T020 [P] [US1] Create GigaChatRequestAdapterTest with edge cases"

# Once tests pass, launch implementation in order:
Task: "T021 [US1] Implement BaseTestConfig class with test annotations"
Task: "T022 [US1] Create test application.yml configuration file"
Task: "T023 [US1] Create GigaChatRequestAdapterTest with basic test cases"
...
```

---

## Implementation Strategy

### MVP First (Unit Tests Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Start User Story 1 (Unit Tests) - 19 tasks
4. **STOP and VALIDATE**: Test User Story 1 independently

### Incremental Delivery

1. Week 1: Unit Tests for Adapters - 18 tasks
2. Week 2: Unit Tests for Services - 15 tasks
3. Week 3: Integration Tests - 25 tasks
4. Week 4: End-to-End Tests - 17 tasks
5. Week 5: Code Coverage & Quality - 8 tasks

---

## Notes

- **Testing Framework**: Using test pyramid methodology balances test execution speed with coverage
- **Test Categories**: Unit tests verify individual components, integration tests verify component interactions, E2E tests verify complete workflows
- **Ratio Guidelines**: The 70:20:10 ratio is a starting point and should be adjusted based on actual codebase needs
- **Test Maintenance**: Regular review of test quality and effectiveness as the codebase evolves
- **Documentation**: Tests should include comments explaining complex scenarios or edge cases
- **Performance**: Tests should not become a bottleneck in the development cycle - they should run quickly and provide fast feedback

---

## Dependencies & Execution Order

### Phase Dependencies
- Phase 1 (Setup): Complete
- Phase 2 (Foundational): Complete
- User Story 1 (Phase 3): Depends on Phase 2
- User Story 2 (Phase 4): Depends on Phase 3
- User Story 3 (Phase 5): Depends on Phase 4
- Polish (Phase 6): Depends on all user stories
