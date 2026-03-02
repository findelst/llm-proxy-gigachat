# Research: GigaChat Native Model Classes Integration

**Feature**: [spec.md](../spec.md) | **Date**: 2026-03-02

## Research Overview

This document summarizes the research findings for implementing GigaChat native model classes integration, including discovery of available classes in `gigachat-java-0.1.13.jar` and integration strategy.

## Research Questions

### Q1: What native model classes are available in gigachat-java-0.1.13?

**Method**: Inspected `gigachat-java-0.1.13.jar` from Gradle cache and listed all classes in the `chat.giga.model.completion` package.

**Findings**:

The `chat.giga.model.completion` package provides the following key classes:

#### Core Request/Response Classes

| Class | Description | Builder Available |
|--------|-------------|------------------|
| CompletionRequest | Main request class | Yes |
| CompletionResponse | Main response class | Yes |
| CompletionChunkResponse | Streaming response class | Yes |

#### Message Classes

| Class | Description | Builder Available |
|--------|-------------|------------------|
| ChatMessage | Message in conversation | Yes |
| ChatMessageRole | Enum for roles | No (enum) |
| Choice | Response choice | Yes |
| ChoiceMessage | Message in choice | Yes |
| ChoiceMessageFunctionCall | Function call in message | Yes |

#### Function/Tool Classes

| Class | Description | Builder Available |
|--------|-------------|------------------|
| ChatFunction | Function definition | Yes |
| ChatFunctionParameters | Function parameters schema | Yes |
| ChatFunctionParametersProperty | Single parameter property | Yes |
| ChatFunctionCall | Function call (legacy?) | Yes |

#### Metadata Classes

| Class | Description | Builder Available |
|--------|-------------|------------------|
| Usage | Token usage statistics | Yes |
| ResponseFormat | Response format configuration | Yes |
| ResponseFormatType | Format type enum | No (enum) |
| ChoiceFinishReason | Finish reason enum | No (enum) |

**Decision**: All required native classes are available in gigachat-java-0.1.13 with builder pattern support.

---

### Q2: How do these classes integrate with langchain4j-gigachat?

**Method**: Reviewed current GigaChatProvider implementation and langchain4j documentation.

**Findings**:

1. **Current Implementation**: The GigaChatProvider uses langchain4j's `GigaChatChatModel` which internally converts to GigaChat API format.

2. **Integration Points**:
   - **Request Flow**: `CompletionRequest` (native) → langchain4j `ChatRequest` → GigaChat provider
   - **Response Flow**: GigaChat provider → langchain4j `ChatResponse` → `CompletionResponse` (native)

3. **Conversion Requirements**:
   - Need converters in application layer between native classes and langchain4j types
   - GigaChatProvider changes minimal (only return type conversion)

**Decision**: Create conversion adapters in application layer to bridge native GigaChat classes and langchain4j.

---

### Q3: What is the impact on existing tests?

**Method**: Reviewed existing test structure and test files.

**Findings**:

1. **Affected Tests**:
   - `ChatControllerTest.kt` - Update to use native request/response classes
   - `ChatServiceTest.kt` - Update to work with native classes
   - `FunctionCallScenarioTest.kt` - Update test data to use native format
   - Unit tests for `ChatCompletionRequest.kt` and `ChatCompletionResponse.kt` - These DTOs will be removed

2. **New Tests Needed**:
   - Conversion logic tests (native ↔ langchain4j)
   - Builder pattern usage tests
   - Edge case handling (stream flag ignored, etc.)

**Decision**: Update existing tests to use native classes, remove DTO-specific tests, add conversion tests.

---

## Decisions Made

| Decision | Rationale | Alternatives Considered |
|----------|------------|-------------------------|
| Use gigachat-java 0.1.13 native classes | Direct requirement from user, eliminates custom DTO conversion | Keep custom DTOs and add conversion layer (rejected due to complexity) |
| Convert at application layer | Maintains clean separation between presentation and infrastructure | Convert at controller layer (rejected - tight coupling) |
| Ignore stream flag | Streaming not supported, matches user story requirement | Implement streaming (rejected - out of scope) |
| Use builder pattern for construction | Native classes use builders, idiomatic approach | Direct instantiation (rejected - less flexible) |

## Integration Strategy

### Architecture Layer Changes

```
┌─────────────────────────────────────────────────────┐
│         Presentation Layer (Changed)            │
│  ChatController uses CompletionRequest/Response  │
└─────────────────────┬───────────────────────────┘
                      │
                      │ Conversion
                      ▼
┌─────────────────────────────────────────────────────┐
│      Application Layer (Changed)               │
│   ChatService converts to/from langchain4j      │
└─────────────────────┬───────────────────────────┘
                      │
                      │ langchain4j types
                      ▼
┌─────────────────────────────────────────────────────┐
│      Infrastructure Layer (Minimal Change)        │
│  GigaChatProvider uses langchain4j types      │
└─────────────────────────────────────────────────────┘
```

### Conversion Logic

**Request Conversion** (`CompletionRequest` → `ChatRequest`):

```kotlin
fun toLangchain4jRequest(): ChatRequest {
    // Map ChatMessage → langchain4j message types
    // Map ChatFunction → ToolSpecification
    // Preserve all parameters
}
```

**Response Conversion** (`ChatResponse` → `CompletionResponse`):

```kotlin
fun toCompletionResponse(): CompletionResponse {
    // Build using CompletionResponse.builder()
    // Map choice, usage, metadata
    // Handle tool calls
}
```

## Known Limitations

1. **Streaming**: Not supported - `stream=true` flag is silently ignored
2. **Batch Requests**: Not documented - assume single choice only
3. **Legacy Functions**: Modern `tools` format is used; legacy `functions` field handling needs verification

## References

- gigachat-java-0.1.13 library: Located in Gradle cache at `~/.gradle/caches/modules-2/files-2.1/chat.giga/gigachat-java/0.1.13/`
- langchain4j-gigachat 0.1.17: Currently in use, compatible with gigachat-java
- Constitution: [../.specify/memory/constitution.md](../.specify/memory/constitution.md)
