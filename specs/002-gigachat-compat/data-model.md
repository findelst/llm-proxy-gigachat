# Data Model: GigaChat API Compatibility

**Date**: 2026-03-02
**Feature**: 002-gigachat-compat

## Overview

This feature extends existing DTOs to support GigaChat's tool calling format. No new domain entities are required - all changes are in the presentation layer DTOs.

## Entity Definitions

### ChatCompletionRequest (Modified)

The existing request DTO extended with tool calling support.

```
ChatCompletionRequest
├── model: String?                    # Model identifier (e.g., "GigaChat", "GigaChat-Pro")
├── messages: List<Message>           # Conversation messages
├── temperature: Double?              # Sampling temperature (0.0 - 2.0)
├── top_p: Double?                    # Nucleus sampling parameter
├── max_tokens: Int?                  # Maximum response tokens
├── stop: List<String>?               # Stop sequences
├── stream: Boolean                   # Stream flag (ignored, always sync)
├── priority: String?                 # Proxy priority (p1, p2, p3)
├── repetition_penalty: Double?       # [NEW] GigaChat repetition penalty
├── tools: List<Tool>?                # [NEW] Tool/function definitions
└── tool_choice: String?              # [NEW] Tool selection mode (auto, none, tool name)
```

#### Message (Modified)

```
Message
├── role: String                      # Message role (system, user, assistant, tool)
├── content: String?                  # Message text content
├── tool_call_id: String?             # [NEW] For role=tool, references the tool call
└── tool_calls: List<ToolCall>?       # [NEW] For role=assistant, tool calls made
```

#### Tool (New)

```
Tool
├── type: String                      # Always "function"
└── function: FunctionDefinition
    ├── name: String                  # Function name
    ├── description: String           # Function description
    └── parameters: JsonSchema        # JSON Schema for parameters
        ├── type: String              # Always "object"
        ├── properties: Map<String, PropertyDef>
        └── required: List<String>    # Required property names
```

#### PropertyDef (New)

```
PropertyDef
├── type: String                      # Property type (string, number, integer, boolean, array, object)
├── description: String?              # Property description
├── enum: List<String>?               # Enum values (if applicable)
└── items: PropertyDef?               # For array types
```

### ChatCompletionResponse (Modified)

The existing response DTO extended with tool call support.

```
ChatCompletionResponse
├── id: String?                       # Response ID
├── object: String                    # Object type ("chat.completion")
├── created: Long                     # Unix timestamp
├── model: String?                    # Model used
├── choices: List<Choice>             # Response choices
└── usage: Usage?                     # Token usage
```

#### Choice (Modified)

```
Choice
├── index: Int                        # Choice index
├── message: Message                  # Response message
└── finish_reason: String?            # Completion reason (stop, tool_calls, length)
```

#### Message (Modified)

```
Message
├── role: String                      # Always "assistant"
├── content: String?                  # Text content (null if tool_calls)
└── tool_calls: List<ToolCall>?       # [NEW] Tool calls made by model
```

#### ToolCall (New)

```
ToolCall
├── id: String                        # Unique tool call identifier
├── type: String                      # Always "function"
└── function: FunctionCall
    ├── name: String                  # Function name to call
    └── arguments: String             # JSON-encoded arguments
```

#### Usage (Unchanged)

```
Usage
├── prompt_tokens: Int                # Input tokens
├── completion_tokens: Int            # Output tokens
└── total_tokens: Int                 # Total tokens
```

## Entity Relationships

```
ChatCompletionRequest
    │
    ├── 1:N ── Message
    │              ├── role: "system" | "user" | "assistant" | "tool"
    │              ├── content: text
    │              ├── tool_call_id (only for role="tool")
    │              └── tool_calls (only for role="assistant")
    │
    └── 0:N ── Tool
                   └── function: FunctionDefinition
                          ├── name
                          ├── description
                          └── parameters (JSON Schema)

ChatCompletionResponse
    │
    ├── 1:N ── Choice
    │              └── message: Message
    │                     ├── role: "assistant"
    │                     ├── content: text or null
    │                     └── tool_calls: List<ToolCall>
    │
    └── 0:1 ── Usage
```

## Validation Rules

### Request Validation

| Field | Rule | Error Message |
|-------|------|---------------|
| messages | Required, non-empty | "messages field is required and must not be empty" |
| messages[].content | Required unless tool_calls present | "Message content must not be null or empty" |
| messages[].role | Must be valid role | "Invalid role: {role}" |
| tools[].function.name | Required, alphanumeric + underscore | "Invalid function name" |
| tools[].function.parameters | Must be valid JSON Schema | "Invalid parameters schema" |
| tool_choice | Must be "auto", "none", or valid tool name | "Invalid tool_choice value" |
| stream | Ignored (no error) | N/A |

### Response Validation

| Field | Rule |
|-------|------|
| finish_reason | "stop", "tool_calls", "length", or null |
| tool_calls[].id | Unique within response |
| tool_calls[].function.arguments | Valid JSON string |

## State Transitions

### Tool Calling Flow

```
1. User Request (with tools)
   └── Role: user, content: "What's the weather?"
   └── Tools: [get_weather function]

2. Model Response (tool call)
   └── Role: assistant
   └── content: null
   └── tool_calls: [{id: "call_1", function: {name: "get_weather", arguments: "{\"city\":\"Moscow\"}"}}]
   └── finish_reason: "tool_calls"

3. Tool Result Request
   └── Role: tool
   └── tool_call_id: "call_1"
   └── content: "{\"temperature\": 20, \"condition\": \"sunny\"}"

4. Final Response
   └── Role: assistant
   └── content: "The weather in Moscow is sunny with 20°C."
   └── finish_reason: "stop"
```

## JSON Examples

### Request with Tools

```json
{
  "model": "GigaChat",
  "messages": [
    {"role": "user", "content": "What's the weather in Moscow?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get current weather for a city",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "City name"
            }
          },
          "required": ["city"]
        }
      }
    }
  ],
  "tool_choice": "auto",
  "stream": true
}
```

### Response with Tool Call

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "GigaChat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_abc123",
            "type": "function",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\": \"Moscow\"}"
            }
          }
        ]
      },
      "finish_reason": "tool_calls"
    }
  ],
  "usage": {
    "prompt_tokens": 50,
    "completion_tokens": 20,
    "total_tokens": 70
  }
}
```
