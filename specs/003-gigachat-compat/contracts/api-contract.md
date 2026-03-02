# API Contract: GigaChat Native Model Classes Integration

**Feature**: [spec.md](../spec.md) | **Date**: 2026-03-02

## Overview

This document defines the external API contract for the LLM Proxy service using GigaChat's native model classes. The proxy exposes a GigaChat-compatible chat completion endpoint.

## Base URL

```
http://localhost:8080/api/v1
```

## Endpoints

### Chat Completion

Creates a model response for the given chat conversation.

**Endpoint**: `POST /chat/completions`

**Content-Type**: `application/json`

#### Request

The request body uses GigaChat's native `CompletionRequest` format.

**Schema**:

```json
{
  "model": "string (required)",
  "messages": [
    {
      "role": "SYSTEM|USER|ASSISTANT|TOOL",
      "content": "string (optional)",
      "toolCallId": "string (optional, for TOOL role)",
      "functionCall": {
        "name": "string (optional, for ASSISTANT role)",
        "arguments": "string (optional, JSON)"
      }
    }
  ] (required)",
  "temperature": "number (optional, 0.0 - 2.0)",
  "topP": "number (optional, 0.0 - 1.0)",
  "maxTokens": "integer (optional, > 0)",
  "stop": ["string"] (optional)",
  "stream": "boolean (optional, ignored, defaults to false)",
  "repetitionPenalty": "number (optional)",
  "tools": [
    {
      "name": "string",
      "description": "string (optional)",
      "parameters": {
        "type": "string",
        "properties": {
          "propertyName": {
            "type": "string|integer|number|boolean|array|object",
            "description": "string (optional)",
            "enum": ["value1", "value2"] (optional)
          }
        },
        "required": ["propertyName"] (optional)
      }
    }
  ] (optional),
  "toolChoice": "string|object (optional)"
}
```

**Request Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| model | string | Yes | Model identifier (e.g., "GigaChat", "GigaChat:latest") |
| messages | array | Yes | Array of message objects |
| temperature | number | No | Sampling temperature, 0.0 - 2.0, default varies by model |
| topP | number | No | Nucleus sampling parameter, 0.0 - 1.0 |
| maxTokens | integer | No | Maximum number of tokens to generate |
| stop | array | No | Array of strings where the API will stop generating |
| stream | boolean | No | Stream flag. **Ignored** - always returns complete response |
| repetitionPenalty | number | No | Penalty for repeating content |
| tools | array | No | Array of tool/function definitions |
| toolChoice | string/object | No | Controls tool calling behavior ("auto", "none", or specific tool) |

**Message Object**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| role | enum | Yes | Message role: SYSTEM, USER, ASSISTANT, TOOL |
| content | string | No | Message content text |
| toolCallId | string | Conditional | Required for TOOL role messages |
| functionCall | object | Conditional | Required for ASSISTANT role when calling tools |

**Tool Object**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| name | string | Yes | Function name |
| description | string | No | Function description for the model |
| parameters | object | No | JSON Schema for function parameters |

#### Response

The response uses GigaChat's native `CompletionResponse` format.

**Status Codes**:

| Code | Description |
|-------|-------------|
| 200 | Success |
| 400 | Invalid request |
| 401 | Unauthorized |
| 429 | Rate limit exceeded |
| 500 | Internal server error |

**Success Response Schema** (200 OK):

```json
{
  "id": "string (e.g., 'chatcmpl-123')",
  "object": "chat.completion",
  "created": 1710000000,
  "model": "string (model name)",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "ASSISTANT",
        "content": "string (assistant's text response)",
        "functionCall": {
          "name": "string (function name)",
          "arguments": "string (JSON arguments)"
        }
      },
      "finishReason": "STOP|LENGTH|TOOL_CALLS"
    }
  ],
  "usage": {
    "promptTokens": 100,
    "completionTokens": 50,
    "totalTokens": 150
  }
}
```

**Response Fields**:

| Field | Type | Description |
|--------|--------|-------------|
| id | string | Unique identifier for the completion |
| object | string | Always "chat.completion" |
| created | integer | Unix timestamp of creation |
| model | string | Model used for the completion |
| choices | array | Array of choice objects (always 1 for non-streaming) |
| usage | object | Token usage statistics |

**Choice Object**:

| Field | Type | Description |
|--------|--------|-------------|
| index | integer | Always 0 for non-streaming responses |
| message | object | The assistant's response message |
| finishReason | enum | Reason the model stopped generating |

**Finish Reason Values**:

| Value | Description |
|--------|-------------|
| STOP | Model stopped naturally |
| LENGTH | Model stopped due to max tokens limit |
| TOOL_CALLS | Model decided to call a tool |

#### Example Requests

**Simple Chat Request**:

```json
POST /api/v1/chat/completions
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [
    {
      "role": "USER",
      "content": "Hello, how are you?"
    }
  ]
}
```

**Request with System Message**:

```json
POST /api/v1/chat/completions
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [
    {
      "role": "SYSTEM",
      "content": "You are a helpful assistant."
    },
    {
      "role": "USER",
      "content": "What is the weather?"
    }
  ]
}
```

**Request with Tools (Function Calling)**:

```json
POST /api/v1/chat/completions
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [
    {
      "role": "USER",
      "content": "What's the weather in Moscow?"
    }
  ],
  "tools": [
    {
      "name": "get_weather",
      "description": "Get the current weather for a location",
      "parameters": {
        "type": "object",
        "properties": {
          "location": {
            "type": "string",
            "description": "The city and state, e.g. San Francisco, CA"
          }
        },
        "required": ["location"]
      }
    }
  }
  ],
  "toolChoice": "auto"
}
```

**Request with Tool Result**:

```json
POST /api/v1/chat/completions
Content-Type: application/json

{
  "model": "GigaChat",
  "messages": [
    {
      "role": "USER",
      "content": "What's the weather in Moscow?"
    },
    {
      "role": "ASSISTANT",
      "functionCall": {
        "name": "get_weather",
        "arguments": "{\"location\": \"Moscow\"}"
      }
    },
    {
      "role": "TOOL",
      "toolCallId": "call_123",
      "content": "The weather in Moscow is 5°C, cloudy."
    }
  ]
}
```

#### Example Responses

**Simple Text Response**:

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1710000000,
  "model": "GigaChat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "ASSISTANT",
        "content": "Hello! I'm doing well, thank you for asking. How can I help you today?"
      },
      "finishReason": "STOP"
    }
  ],
  "usage": {
    "promptTokens": 10,
    "completionTokens": 20,
    "totalTokens": 30
  }
}
```

**Response with Tool Call**:

```json
{
  "id": "chatcmpl-def456",
  "object": "chat.completion",
  "created": 1710000001,
  "model": "GigaChat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "ASSISTANT",
        "functionCall": {
          "name": "get_weather",
          "arguments": "{\"location\": \"Moscow\"}"
        },
        "finishReason": "TOOL_CALLS"
      }
    }
  ],
  "usage": {
    "promptTokens": 25,
    "completionTokens": 15,
    "totalTokens": 40
  }
}
```

## Error Responses

All error responses follow this format:

```json
{
  "error": {
    "message": "string (error description)",
    "type": "string (error type)",
    "code": "string (error code)"
  }
}
```

**Common Error Codes**:

| Code | Description |
|-------|-------------|
| invalid_request | Request format is invalid |
| authentication_error | API key is invalid or missing |
| rate_limit_exceeded | Rate limit has been exceeded |
| internal_error | Internal server error |

## Backward Compatibility

The API contract is fully compatible with GigaChat's native API format. Existing GigaChat clients can use the proxy without modifications.

Key compatibility notes:
1. The `stream` parameter is accepted but ignored (non-streaming only)
2. All GigaChat-specific parameters are supported
3. Tool calling follows GigaChat's exact format
4. Response structure matches GigaChat's specification

## Notes

- Streaming is not supported - the `stream=true` flag is silently ignored
- Only one choice is returned in the `choices` array (index 0)
- All timestamps are Unix timestamps in seconds
- Token usage is provided when available from the provider
