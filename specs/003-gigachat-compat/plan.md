# Implementation Plan: GigaChat Native Model Classes Integration

**Branch**: `003-gigachat-compat` | **Date**: 2026-03-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-gigachat-compat/spec.md`

**Note**: This template is filled in by `/speckit.plan` command. See `.specify/templates/plan-template.md` for execution workflow.

## Summary

Replace custom DTOs (ChatCompletionRequest, ChatCompletionResponse) with GigaChat's native model classes from `gigachat-java-0.1.13.jar` library. The refactoring will use the `chat.giga.model.completion` package classes (CompletionRequest, CompletionResponse, ChatMessage, ChatFunction, etc.) to eliminate custom DTO conversion logic and ensure full API compatibility with GigaChat.

Technical approach: Update the presentation layer to accept and return GigaChat native classes directly, modify the application service layer to work with these classes, and ensure the infrastructure layer properly integrates with the langchain4j-gigachat library.

## Technical Context

**Language/Version**: Kotlin 2.x (JVM 21)
**Primary Dependencies**: Spring Boot 3.x, langchain4j-gigachat 0.1.17, gigachat-java 0.1.13, Caffeine 3.x
**Storage**: Caffeine (in-memory cache) - no persistent storage required
**Testing**: JUnit 5, Kotest 5.x, Spring Test
**Target Platform**: JVM server application
**Project Type**: Web service (REST API proxy)
**Performance Goals**: <200ms p95 for cached responses, handle configured concurrent requests to GigaChat
**Constraints**: Must follow DDD and Clean Architecture principles, bounded parallelism to GigaChat API
**Scale/Scope**: ~50 LOC changes (presentation/application layers), ~5 test scenarios

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|----------|--------|
| Domain-Driven Design & Clean Architecture | PASS | Refactoring occurs in presentation/application layers; domain layer remains independent |
| Technology Stack Discipline | PASS | Using gigachat-java 0.1.13 (standard library) + langchain4j-gigachat |
| Testing Standards | PASS | Tests will use JUnit 5 + Kotest, follow unit/integration/contract structure |
| Concurrency Model | PASS | Existing coroutines-based infrastructure is preserved |
| Caching Strategy | PASS | Existing Caffeine-based caching is preserved |

**All gates passed.** Proceeding with implementation planning.

## Research

### GigaChat Native Model Classes

**Discovery**: Investigated `gigachat-java-0.1.13.jar` library to understand available native model classes.

**Key Findings**:

The `chat.giga.model.completion` package provides the following classes:

| Class | Purpose |
|--------|----------|
| CompletionRequest | Main request class with builder |
| CompletionResponse | Main response class with builder |
| CompletionChunkResponse | Streaming response (will be ignored) |
| ChatMessage | Message class with builder |
| ChatMessageRole | Enum (SYSTEM, USER, ASSISTANT, TOOL) |
| ChatFunction | Function definition class with builder |
| ChatFunctionParameters | Function parameters schema class |
| ChatFunctionCall | Function call in response with builder |
| Choice | Response choice with builder |
| ChoiceMessage | Message in response choice with builder |
| ChoiceMessageFunctionCall | Function call in choice message with builder |
| Usage | Token usage statistics with builder |
| ResponseFormat | Response format configuration |
| ChoiceFinishReason | Enum for finish reasons |

**Integration Approach**:

1. **Presentation Layer**: Controllers will accept `CompletionRequest` and return `CompletionResponse` directly
2. **Application Layer**: Services will work with these native classes, converting to langchain4j types as needed for provider interaction
3. **Infrastructure Layer**: Existing GigaChatProvider already uses langchain4j-gigachat, minimal changes required

**Decision**: Use GigaChat native classes directly in presentation layer

**Rationale**: This approach eliminates custom DTO conversion logic, ensures API compatibility, and aligns with the user's requirement to use native classes.

**Alternatives Considered**:
- Keep custom DTOs and add conversion layer to/from native classes - rejected as it adds unnecessary complexity
- Use langchain4j's request/response classes - rejected as they don't match GigaChat's exact API format

## Project Structure

### Documentation (this feature)

```text
specs/003-gigachat-compat/
├── plan.md              # This file (/speckit.plan command output)
├── spec.md              # Feature specification (/speckit.specify command output)
├── research.md           # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md         # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── api-contract.md  # API endpoint contract
└── checklists/
    └── requirements.md    # Specification quality checklist
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── presentation/
│   ├── controller/
│   │   └── ChatController.kt              # Update to use CompletionRequest/CompletionResponse
│   └── dto/
│       ├── ChatCompletionRequest.kt           # REMOVE - replaced by native classes
│       ├── ChatCompletionResponse.kt          # REMOVE - replaced by native classes
│       └── InvokeResponse.kt               # May need updates
├── application/
│   ├── service/
│   │   └── ChatService.kt                 # Update to work with native classes
│   └── port/
│       └── ChatProviderPort.kt             # May need interface updates
└── infrastructure/
    └── gigachat/
        └── GigaChatProvider.kt             # Update conversion logic

src/test/kotlin/ru/ddd/llmproxy/
├── presentation/
│   └── controller/
│       └── ChatControllerTest.kt           # Update for native classes
├── application/
│   └── service/
│       └── ChatServiceTest.kt              # Update for native classes
└── integration/
    └── FunctionCallScenarioTest.kt           # Update for native classes
```

**Structure Decision**: Using existing DDD/Clean Architecture structure. Presentation layer changes to use native GigaChat classes directly. Application layer updated to work with these classes. Infrastructure layer minimal changes to support the new types.

## Data Model

See [data-model.md](./data-model.md) for detailed entity definitions.

## API Contracts

See [contracts/api-contract.md](./contracts/api-contract.md) for the external API contract.

## Complexity Tracking

> **No violations identified.** All constitution gates passed without requiring justifications.
