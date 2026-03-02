# Chat Completion API Contract

**Version**: 1.0.0
**Feature**: 002-gigachat-compat
**Compatibility**: GigaChat API (OpenAI-compatible)

## Endpoints

### POST /v1/chat/completions

Creates a model response for the given chat conversation.

**Request Headers**:
| Header | Required | Description |
|--------|----------|-------------|
| Content-Type | Yes | application/json |
| X-Priority | No | Priority level (p1, p2, p3, highest, lowest) |
| x-request-id | No | Request identifier for tracing |

**Request Body**:

```json
{
  "model": "string (optional, default: GigaChat)",
  "messages": [
    {
      "role": "system | user | assistant | tool",
      "content": "string (optional for assistant with tool_calls)",
      "tool_call_id": "string (required for role=tool)",
      "tool_calls": [
        {
          "id": "string",
          "type": "function",
          "function": {
            "name": "string",
            "arguments": "string (JSON)"
          }
        }
      ]
    }
  ],
  "temperature": "number (optional, 0.0-2.0)",
  "top_p": "number (optional, 0.0-1.0)",
  "max_tokens": "integer (optional)",
  "repetition_penalty": "number (optional)",
  "stop": ["string"] | "string (optional)",
  "stream": "boolean (optional, ignored - always returns sync)",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "string (required)",
        "description": "string (optional)",
        "parameters": {
          "type": "object",
          "properties": {
            "property_name": {
              "type": "string | number | integer | boolean | array | object",
              "description": "string (optional)",
              "enum": ["values"] | null,
              "items": {} | null
            }
          },
          "required": ["property_names"]
        }
      }
    }
  ],
  "tool_choice": "auto | none | {\"type\": \"function\", \"function\": {\"name\": \"...\"}}",
  "priority": "string (optional, proxy-specific: p1, p2, p3)"
}
```

**Response (200 OK)**:

```json
{
  "id": "string",
  "object": "chat.completion",
  "created": "integer (unix timestamp)",
  "model": "string",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "string | null",
        "tool_calls": [
          {
            "id": "string",
            "type": "function",
            "function": {
              "name": "string",
              "arguments": "string (JSON)"
            }
          }
        ]
      },
      "finish_reason": "stop | tool_calls | length | null"
    }
  ],
  "usage": {
    "prompt_tokens": "integer",
    "completion_tokens": "integer",
    "total_tokens": "integer"
  }
}
```

**Response Headers**:
| Header | Description |
|--------|-------------|
| x-request-id | Request identifier |
| x-llm-proxy-request-id | Internal proxy request ID |
| x-llm-proxy-priority | Resolved priority level |
| x-llm-proxy-queue-wait-ms | Time spent in queue (ms) |
| x-llm-proxy-provider-latency-ms | Provider response time (ms) |

**Error Responses**:

| Status | Code | Description |
|--------|------|-------------|
| 400 | invalid_request | Malformed JSON or invalid request |
| 400 | messages_required | messages field is empty |
| 500 | provider_error | Upstream provider error |

---

### POST /v1/chat/invoke

Same as `/v1/chat/completions` but returns metrics in response body.

**Response (200 OK)**:

```json
{
  "id": "string",
  "object": "chat.completion",
  "created": "integer",
  "model": "string",
  "choices": [...],
  "usage": {...},
  "metrics": {
    "request_id": "string",
    "priority": "string",
    "queue_wait_ms": "integer | null",
    "provider_latency_ms": "integer | null",
    "endpoint": "string"
  }
}
```

---

## Test Cases

### TC-001: Basic Chat Request

**Request**:
```json
{
  "messages": [{"role": "user", "content": "Hello"}]
}
```

**Expected**: 200 OK with response containing assistant message

---

### TC-002: Request with Tool Definition

**Request**:
```json
{
  "messages": [{"role": "user", "content": "What's the weather?"}],
  "tools": [{
    "type": "function",
    "function": {
      "name": "get_weather",
      "description": "Get weather",
      "parameters": {
        "type": "object",
        "properties": {"city": {"type": "string"}},
        "required": ["city"]
      }
    }
  }]
}
```

**Expected**: 200 OK, response may contain tool_calls if model decides to call

---

### TC-003: Stream=True Graceful Handling

**Request**:
```json
{
  "messages": [{"role": "user", "content": "Hello"}],
  "stream": true
}
```

**Expected**: 200 OK with synchronous response (not SSE stream), no error

---

### TC-004: Tool Result Message

**Request**:
```json
{
  "messages": [
    {"role": "user", "content": "What's the weather?"},
    {"role": "assistant", "tool_calls": [{"id": "call_1", "type": "function", "function": {"name": "get_weather", "arguments": "{\"city\":\"Moscow\"}"}}]},
    {"role": "tool", "tool_call_id": "call_1", "content": "{\"temp\": 20}"}
  ]
}
```

**Expected**: 200 OK with assistant response based on tool result

---

### TC-005: Tool Choice None

**Request**:
```json
{
  "messages": [{"role": "user", "content": "Hello"}],
  "tools": [...],
  "tool_choice": "none"
}
```

**Expected**: 200 OK, response should not contain tool_calls

---

### TC-006: GigaChat-Specific Parameters

**Request**:
```json
{
  "messages": [{"role": "user", "content": "Hello"}],
  "model": "GigaChat-Pro",
  "temperature": 0.7,
  "max_tokens": 100,
  "repetition_penalty": 1.1
}
```

**Expected**: 200 OK with response respecting parameters

---

### TC-007: Empty Messages Error

**Request**:
```json
{
  "messages": []
}
```

**Expected**: 400 Bad Request with error code "messages_required"

---

### TC-008: Invalid JSON Error

**Request**:
```json
{ invalid json }
```

**Expected**: 400 Bad Request with error code "invalid_request"
