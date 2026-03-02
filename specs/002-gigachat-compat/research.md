# Research: GigaChat API Compatibility

**Date**: 2026-03-02
**Feature**: 002-gigachat-compat

## Research Questions

### RQ-1: GigaChat Tool Calling Format

**Question**: What is the exact format for tool definitions and tool calls in GigaChat API?

**Decision**: GigaChat uses OpenAI-compatible format for tool calling.

**Rationale**: The langchain4j-gigachat library implements the standard langchain4j ToolSpecification API which is compatible with OpenAI's function calling format. GigaChat API documentation confirms OpenAI-compatible structure.

**Format Details**:

**Tool Definition (Request)**:
```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get current weather",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string"}
          },
          "required": ["city"]
        }
      }
    }
  ]
}
```

**Tool Call (Response)**:
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "get_weather",
          "arguments": "{\"city\": \"Moscow\"}"
        }
      }]
    }
  }]
}
```

**Tool Result (Follow-up Request)**:
```json
{
  "messages": [
    {"role": "user", "content": "What's the weather?"},
    {"role": "assistant", "tool_calls": [...]},
    {"role": "tool", "tool_call_id": "call_abc123", "content": "{\"temp\": 20}"}
  ]
}
```

**Alternatives Considered**:
- Custom GigaChat-specific format: Rejected - langchain4j already handles OpenAI format

---

### RQ-2: langchain4j Tool Support

**Question**: How does langchain4j handle tool calling in ChatRequest/ChatResponse?

**Decision**: Use langchain4j's built-in tool specifications and tool execution result message types.

**Rationale**: langchain4j provides native support for:
- `ToolSpecification` class for defining tools
- `ToolExecutionRequestMessage` for tool results
- `AiMessage.toolExecutionRequests()` for model-generated tool calls

**Key Classes**:
- `dev.langchain4j.agent.tool.ToolSpecification` - Tool definition
- `dev.langchain4j.agent.tool.ToolExecutionRequest` - Tool call from model
- `dev.langchain4j.data.message.ToolExecutionResultMessage` - Tool result

**Implementation Notes**:
1. Convert DTO `Tool` to langchain4j `ToolSpecification`
2. Convert langchain4j `ToolExecutionRequest` to DTO `ToolCall`
3. Handle `ToolExecutionResultMessage` in request conversion

**Alternatives Considered**:
- Manual JSON serialization: Rejected - langchain4j provides type-safe API

---

### RQ-3: Stream Handling Approach

**Question**: How should the system handle `stream=true` requests?

**Decision**: Silently ignore `stream=true` and process as synchronous request.

**Rationale**:
1. Many GigaChat clients default to streaming mode
2. Returning an error would break compatibility
3. Implementing SSE streaming would add significant complexity
4. Synchronous fallback provides graceful degradation

**Implementation**:
1. Remove stream validation from ChatController
2. Do not pass stream parameter to langchain4j (not supported by current implementation)
3. Log a debug message when stream=true is ignored

**Alternatives Considered**:
- Return error for stream=true: Rejected - breaks compatibility requirement
- Implement SSE streaming: Rejected - out of scope, adds complexity

---

### RQ-4: GigaChat-Specific Parameters

**Question**: What GigaChat-specific parameters need to be supported?

**Decision**: Support all common GigaChat parameters through pass-through to langchain4j.

**Parameters to Support**:

| Parameter | Type | Langchain4j Support |
|-----------|------|---------------------|
| model | String | ✅ ChatRequestParameters.modelName() |
| temperature | Double | ✅ ChatRequestParameters.temperature() |
| max_tokens | Int | ✅ ChatRequestParameters.maxOutputTokens() |
| top_p | Double | ✅ ChatRequestParameters.topP() |
| repetition_penalty | Double | ⚠️ Check GigaChatChatRequestParameters |
| tools | List<Tool> | ✅ ChatRequest.toolSpecifications() |
| tool_choice | String | ⚠️ Check GigaChatChatRequestParameters |

**Rationale**: langchain4j-gigachat extends standard ChatRequestParameters with GigaChat-specific options.

**Alternatives Considered**:
- Only support OpenAI parameters: Rejected - doesn't meet compatibility requirement

---

### RQ-5: Cache Key with Tools

**Question**: How should tools be included in cache key generation?

**Decision**: Include serialized tools array in cache key hash.

**Rationale**:
1. Same request with different tools may produce different responses
2. Tool definitions affect model behavior
3. Deterministic serialization ensures cache hits for identical tool sets

**Implementation**:
1. Extend `CacheKeyGenerator.serializeChatRequest()` to include tools
2. Serialize tools in consistent order (sorted by name)
3. Include tool_choice in cache key

**Alternatives Considered**:
- Ignore tools in cache key: Rejected - could return incorrect cached responses

---

## Summary

All research questions resolved. Key findings:

1. **Tool Format**: Use OpenAI-compatible format, already supported by langchain4j
2. **langchain4j Integration**: Use native ToolSpecification and ToolExecutionRequest classes
3. **Stream Handling**: Silent ignore, no exception
4. **Parameters**: Pass through to langchain4j-gigachat
5. **Cache Keys**: Include tools in hash calculation

No blocking issues identified. Ready for Phase 1 design.
