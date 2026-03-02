# Implementation Plan: GigaChat API Compatibility

**Branch**: `002-gigachat-api-compat` | **Date**: 2026-03-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-gigachat-compat/spec.md`

## Summary

Extend the existing LLM Proxy to be fully compatible with GigaChat API format instead of OpenAI. This includes:
1. Supporting tool/function calling in both requests and responses
2. Gracefully handling `stream=true` by ignoring it instead of throwing exceptions
3. Supporting all GigaChat-specific request/response fields

The implementation will modify the existing DTOs and controller to handle GigaChat's tool calling format while maintaining backward compatibility with the existing priority queue and caching infrastructure.

## Technical Context

**Language/Version**: Kotlin 2.0.21 (JVM 21)
**Primary Dependencies**: Spring Boot 3.3.5, langchain4j-gigachat 0.1.17, Caffeine 3.1.8
**Storage**: Caffeine in-memory cache (no database)
**Testing**: JUnit 5, Spring Test, Kotest assertions, kotlinx-coroutines-test
**Target Platform**: Linux server (containerized deployment)
**Project Type**: web-service (REST API proxy)
**Performance Goals**: Handle concurrent requests with bounded parallelism, sub-second latency for cached responses
**Constraints**: GigaChat API rate limits, configurable concurrency limits
**Scale/Scope**: Internal enterprise use, 10-100 concurrent users

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Domain-Driven Design & Clean Architecture | ✅ PASS | Changes are in presentation layer (DTOs, controllers); domain layer unchanged |
| II. Technology Stack Discipline | ✅ PASS | Using langchain4j-gigachat for all LLM interactions |
| III. Testing Standards | ✅ PASS | Will add tests using JUnit 5, Spring Test, Kotest |
| IV. Concurrency Model | ✅ PASS | No changes to concurrency model |
| V. Caching Strategy | ✅ PASS | Cache key generation will be updated to include tools |

**Gate Status**: ✅ ALL GATES PASS

## Project Structure

### Documentation (this feature)

```text
specs/002-gigachat-compat/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── chat-completion-api.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/kotlin/ru/ddd/llmproxy/
├── presentation/
│   ├── controller/
│   │   └── ChatController.kt        # MODIFY: Remove stream validation exception
│   ├── dto/
│   │   ├── ChatCompletionRequest.kt # MODIFY: Add tools, tool_choice fields
│   │   ├── ChatCompletionResponse.kt# MODIFY: Add tool_calls to Message
│   │   └── InvokeResponse.kt        # MODIFY: Add tool_calls support
│   └── middleware/
│       └── RequestIdMiddleware.kt   # No changes
├── application/
│   ├── service/
│   │   ├── ChatApplicationService.kt# No changes
│   │   └── PriorityResolver.kt      # No changes
│   └── port/
│       ├── ChatProviderPort.kt      # No changes
│       └── MetricsPort.kt           # No changes
├── domain/
│   ├── model/
│   │   ├── Priority.kt              # No changes
│   │   ├── Exceptions.kt            # No changes
│   │   └── ...                     # No changes
│   └── ...                         # No changes
└── infrastructure/
    ├── gigachat/
    │   └── GigaChatProvider.kt      # No changes (langchain4j handles tools)
    ├── cache/
    │   └── CacheKeyGenerator.kt     # MODIFY: Include tools in cache key
    └── ...                         # No changes

src/test/kotlin/ru/ddd/llmproxy/
├── integration/
│   ├── TestConfig.kt                # No changes
│   ├── ChatIntegrationTest.kt       # MODIFY: Add tool calling tests
│   ├── FunctionCallScenarioTest.kt  # MODIFY: Update stream handling tests
│   └── PriorityQueueConcurrencyTest.kt # No changes
└── unit/
    └── presentation/
        └── dto/
            ├── ChatCompletionRequestTest.kt  # NEW: Unit tests for DTO
            └── ChatCompletionResponseTest.kt # NEW: Unit tests for DTO
```

**Structure Decision**: Single project structure with modifications to existing presentation layer DTOs and controllers. No new modules or packages needed.

## Implementation Approach

### Key Changes

1. **ChatCompletionRequest.kt** - Add GigaChat-specific fields:
   - `tools: List<Tool>?` - Tool/function definitions
   - `tool_choice: String?` - Tool selection preference (auto, none, or specific tool)
   - `repetition_penalty: Double?` - GigaChat-specific parameter

2. **ChatCompletionRequest.Message** - Extend for tool results:
   - Add `tool_call_id: String?` for tool result messages
   - Handle `role: "tool"` messages

3. **ChatCompletionResponse.kt** - Add tool call support:
   - Add `ToolCall` nested class with id, type, function.name, function.arguments
   - Add `tool_calls: List<ToolCall>?` to `Message`

4. **ChatController.kt** - Remove stream exception:
   - Remove validation that throws exception for `stream=true`
   - Silently ignore stream flag

5. **CacheKeyGenerator.kt** - Include tools in cache key:
   - Serialize tools array into cache key generation

6. **Tests** - Add comprehensive tests for:
   - Tool calling request parsing
   - Tool calling response formatting
   - Stream=true graceful handling
   - GigaChat-specific parameters

## Complexity Tracking

> No constitution violations - all changes align with existing architecture.

| Aspect | Complexity | Justification |
|--------|------------|---------------|
| DTO Extensions | Low | Adding optional fields to existing DTOs |
| Controller Change | Low | Removing validation, not adding logic |
| Cache Key | Low | Adding field to existing serialization |
| Tests | Medium | New test cases for tool calling scenarios |
