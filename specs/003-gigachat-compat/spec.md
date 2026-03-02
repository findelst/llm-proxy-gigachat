# Feature Specification: GigaChat Native Model Classes Integration

**Feature Branch**: `003-gigachat-compat`
**Created**: 2026-03-02
**Status**: Draft
**Input**: User description: "для совместимости с GigaChat API надо использовать классы из chat.giga.model.completion . Например CompletionRequest и другие. Давай переделаем API под данные классы"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Native GigaChat Request Support (Priority: P1)

A developer sends a chat request that follows GigaChat's native request format. The system accepts the request using GigaChat's native model classes (from `chat.giga.model.completion` package) without requiring custom DTO conversions, processes the request through the chat provider, and returns a response using GigaChat's native response format.

**Why this priority**: Using GigaChat's native model classes eliminates the need for custom DTO conversion logic, reduces potential compatibility issues, and ensures the proxy is always aligned with GigaChat's API specification. This is the foundation for reliable GigaChat API compatibility.

**Independent Test**: Can be fully tested by sending a request using GigaChat's native request format and verifying the response matches GigaChat's expected structure without intermediate conversions.

**Acceptance Scenarios**:

1. **Given** a request using GigaChat's native CompletionRequest format, **When** the request is received, **Then** the system accepts and processes it without custom DTO conversion
2. **Given** a request with GigaChat-specific parameters (e.g., repetition_penalty, top_p), **When** the request is processed, **Then** all parameters are correctly handled by the native GigaChat model classes
3. **Given** a request with tools defined in GigaChat's native format, **When** the request is processed, **Then** the tool definitions are passed through correctly
4. **Given** a request with message history in GigaChat's native format, **When** the request is processed, **Then** all messages are handled correctly including system, user, assistant, and tool roles

---

### User Story 2 - Native GigaChat Response Format (Priority: P1)

A client receives a response in GigaChat's native format using classes from `chat.giga.model.completion`. The response structure matches exactly what GigaChat API returns, enabling drop-in replacement for existing GigaChat clients.

**Why this priority**: Response format compatibility is equally important as request compatibility. Clients need to receive responses they can parse without modification to their existing codebases.

**Independent Test**: Can be fully tested by sending a valid request and verifying the response object is an instance of GigaChat's native response class with all expected fields populated.

**Acceptance Scenarios**:

1. **Given** any valid chat request, **When** a response is generated, **Then** the response uses GigaChat's native response class
2. **Given** a response containing assistant text content, **When** the response is returned, **Then** the content is accessible through native GigaChat response fields
3. **Given** a response containing tool calls, **When** the response is returned, **Then** tool calls are formatted according to GigaChat's native structure
4. **Given** a response with token usage information, **When** the response is returned, **Then** usage data is available through native GigaChat response fields

---

### User Story 3 - Tool Calling with Native Classes (Priority: P2)

A developer sends a request with function definitions using GigaChat's native tool format. The system processes the request and returns a response that may include tool calls formatted according to GigaChat's native specification.

**Why this priority**: Tool calling is a core feature of modern LLM APIs. Ensuring tool definitions and tool calls work correctly with native GigaChat classes is critical for function execution workflows.

**Independent Test**: Can be fully tested by sending a request with a tool definition (e.g., weather function) in GigaChat's native format and verifying the response correctly handles tool calls.

**Acceptance Scenarios**:

1. **Given** a request with tools in GigaChat's native format, **When** the model decides to call a tool, **Then** the response includes tool calls in GigaChat's native structure
2. **Given** a request with multiple tools defined, **When** the model calls a specific tool, **Then** the tool call includes the correct tool name and properly formatted JSON arguments
3. **Given** a tool execution result message in GigaChat's native format, **When** sent as part of conversation history, **Then** the system processes it and returns the model's follow-up response
4. **Given** a request with tool_choice parameter set, **When** the request is processed, **Then** the tool choice preference is respected

---

### User Story 4 - Stream Flag Handling (Priority: P2)

A developer sends a request with `stream=true` expecting streaming responses. The system silently ignores the streaming flag (as streaming is not supported) and returns a complete synchronous response using GigaChat's native response format.

**Why this priority**: Many GigaChat clients default to streaming mode. Gracefully handling the stream flag ensures the proxy works with all clients without configuration changes or errors.

**Independent Test**: Can be fully tested by sending a request with `stream=true` and verifying a valid non-streamed response is returned without errors.

**Acceptance Scenarios**:

1. **Given** a request with `stream=true`, **When** the request is processed, **Then** the system returns a complete synchronous response using native GigaChat response classes
2. **Given** a request with `stream=true`, **When** the response is returned, **Then** no error is raised about unsupported streaming
3. **Given** a request with `stream=false` or without stream field, **When** the request is processed, **Then** the behavior is identical (synchronous response)

---

### Edge Cases

- What happens when a request contains fields not supported by GigaChat's native classes? System should pass through fields supported by native classes and handle unknown fields appropriately
- What happens when tool call arguments are malformed? System should return appropriate error response using GigaChat's error format
- What happens when both legacy `functions` and modern `tools` are specified? System should prefer the format supported by GigaChat's native classes
- What happens when message content is null but tool_calls are present? System should handle assistant messages with only tool_calls correctly
- What happens when the request exceeds GigaChat's size limits? System should return appropriate error response

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept requests using GigaChat's native request classes from `chat.giga.model.completion` package
- **FR-002**: System MUST process requests without requiring conversion to custom DTOs
- **FR-003**: System MUST return responses using GigaChat's native response classes from `chat.giga.model.completion` package
- **FR-004**: System MUST support all GigaChat-specific request parameters through native model classes
- **FR-005**: System MUST correctly handle tool definitions and tool calls using GigaChat's native tool format
- **FR-006**: System MUST support all message roles (system, user, assistant, tool) using GigaChat's native message format
- **FR-007**: System MUST silently ignore `stream=true` parameter and return synchronous responses
- **FR-008**: System MUST NOT throw exceptions or return errors when `stream=true` is specified
- **FR-009**: System MUST handle tool execution result messages in conversation history using GigaChat's native format
- **FR-010**: System MUST support multiple tool calls in a single assistant message using GigaChat's native structure
- **FR-011**: System MUST correctly serialize tool call arguments as JSON strings in GigaChat's native format
- **FR-012**: System MUST support `tool_choice` parameter values (auto, none, or specific tool) using GigaChat's native format
- **FR-013**: System MUST preserve all provider-specific metadata in responses using GigaChat's native response classes
- **FR-014**: System MUST return token usage information (prompt_tokens, completion_tokens, total_tokens) using GigaChat's native format
- **FR-015**: System MUST handle requests with model name specified using GigaChat's native format
- **FR-016**: System MUST return responses with all required fields (id, object, created, model, choices, usage) populated according to GigaChat's specification

### Key Entities

- **CompletionRequest** (GigaChat native): The incoming request object from GigaChat's `chat.giga.model.completion` package containing model, messages, parameters, tools, and other GigaChat-specific fields
- **CompletionResponse** (GigaChat native): The response object from GigaChat's `chat.giga.model.completion` package containing id, object type, timestamp, model name, choices array, and usage statistics
- **Message** (GigaChat native): A conversation message from GigaChat's native format with role and content (text for user/assistant, tool calls for assistant with function calls, tool call id and content for tool results)
- **Tool** (GigaChat native): A function definition using GigaChat's native structure with type, name, description, and JSON Schema parameters
- **ToolCall** (GigaChat native): A model-generated tool invocation using GigaChat's native structure with id, type, and function details (name and JSON arguments string)
- **Usage** (GigaChat native): Token usage statistics from GigaChat's native format with prompt_tokens, completion_tokens, and total_tokens

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of valid GigaChat API requests using native request classes are accepted and processed without custom DTO conversion
- **SC-002**: Responses are returned using GigaChat's native response classes with all required fields populated correctly
- **SC-003**: Tool calling scenarios work correctly with native GigaChat classes - requests with tools return proper tool_calls in responses when the model decides to call them
- **SC-004**: Requests with `stream=true` return valid synchronous responses using GigaChat's native response classes without errors
- **SC-005**: Integration tests pass with realistic GigaChat request/response payloads using native model classes
- **SC-006**: All GigaChat-specific parameters (repetition_penalty, top_p, etc.) are correctly handled through native model classes
- **SC-007**: Message handling for all roles (system, user, assistant, tool) works correctly with native GigaChat message format

## Assumptions

- The `chat.giga.model.completion` package provides native request and response classes (CompletionRequest, CompletionResponse) that match GigaChat's API specification
- These native classes are located in the `gigachat-java-0.1.13.jar` library
- The langchain4j-gigachat library can integrate with GigaChat's native model classes
- The current custom DTO-based implementation can be refactored to use native classes without breaking existing functionality
- GigaChat's native model classes include support for tool calling and all required request/response fields
- The proxy does not need to implement actual streaming support - only graceful fallback to synchronous mode
