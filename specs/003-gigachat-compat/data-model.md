# Data Model: GigaChat Native Model Classes Integration

**Feature**: [spec.md](./spec.md) | **Date**: 2026-03-02

## Overview

This document describes the data entities used in the GigaChat native model classes integration. The system will use native classes from `gigachat-java-0.1.13.jar` instead of custom DTOs.

## Core Entities

### CompletionRequest

**Source**: `chat.giga.model.completion.CompletionRequest` (gigachat-java-0.1.13)

**Purpose**: Represents an incoming chat completion request from a client.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| messages | List<ChatMessage> | Yes | Conversation messages |
| model | String | Yes | Model name (e.g., "GigaChat") |
| temperature | Double | No | Sampling temperature (0.0 - 2.0) |
| topP | Double | No | Nucleus sampling parameter |
| maxTokens | Integer | No | Maximum tokens to generate |
| stop | List<String> | No | Stop sequences |
| stream | Boolean | No | Stream flag (will be ignored, defaults to false) |
| repetitionPenalty | Double | No | Repetition penalty |
| tools | List<ChatFunction> | No | Function definitions |
| toolChoice | ToolChoice | No | Tool choice preference |

**Validation Rules**:
- `messages` must not be empty
- `model` must be a valid GigaChat model name
- `temperature` must be between 0.0 and 2.0 if provided
- `maxTokens` must be positive if provided
- `stream` is ignored (always processed as synchronous)

**Builder Pattern**: `CompletionRequest.builder()` provides fluent API for construction.

---

### CompletionResponse

**Source**: `chat.giga.model.completion.CompletionResponse` (gigachat-java-0.1.13)

**Purpose**: Represents the chat completion response returned to the client.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| id | String | Yes | Unique response identifier |
| object | String | Yes | Object type ("chat.completion") |
| created | Long | Yes | Unix timestamp |
| model | String | Yes | Model name used |
| choices | List<Choice> | Yes | Response choices |
| usage | Usage | No | Token usage statistics |

**Validation Rules**:
- `choices` must contain exactly one choice (non-streaming mode)
- `id` must be a valid UUID or timestamp-based identifier
- `object` must be "chat.completion"

**Builder Pattern**: `CompletionResponse.builder()` provides fluent API for construction.

---

### ChatMessage

**Source**: `chat.giga.model.completion.ChatMessage` (gigachat-java-0.1.13)

**Purpose**: Represents a message in the conversation.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| role | ChatMessageRole | Yes | Message role (SYSTEM, USER, ASSISTANT, TOOL) |
| content | String | No | Message text content |
| toolCallId | String | No | Tool call ID (for TOOL role) |
| functionCall | ChoiceMessageFunctionCall | No | Function call (for ASSISTANT role) |

**Role Enum** - `ChatMessageRole`:
- `SYSTEM`: System message
- `USER`: User message
- `ASSISTANT`: Assistant message
- `TOOL`: Tool execution result

**Validation Rules**:
- `role` must be one of the enum values
- For `TOOL` role: `toolCallId` is required
- For `ASSISTANT` role: Either `content` or `functionCall` (or both for tool_calls scenario)

**Builder Pattern**: `ChatMessage.builder()` provides fluent API for construction.

---

### ChatFunction

**Source**: `chat.giga.model.completion.ChatFunction` (gigachat-java-0.1.13)

**Purpose**: Defines a function/tool for the LLM to call.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| name | String | Yes | Function name |
| description | String | No | Function description |
| parameters | ChatFunctionParameters | No | JSON Schema for parameters |

**Validation Rules**:
- `name` must be a valid identifier (alphanumeric, underscores)
- `description` should provide clear guidance to the LLM
- `parameters` must be valid JSON Schema if provided

**Builder Pattern**: `ChatFunction.builder()` provides fluent API for construction.

---

### ChatFunctionParameters

**Source**: `chat.giga.model.completion.ChatFunctionParameters` (gigachat-java-0.1.13)

**Purpose**: Defines JSON Schema for function parameters.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| type | String | Yes | Schema type (usually "object") |
| properties | Map<String, ChatFunctionParametersProperty> | No | Property definitions |
| required | List<String> | No | Required property names |

**Validation Rules**:
- `type` must be a valid JSON Schema type
- `properties` map keys must match parameter names
- `required` list must reference keys in `properties`

**Builder Pattern**: `ChatFunctionParameters.builder()` provides fluent API for construction.

---

### ChatFunctionParametersProperty

**Source**: `chat.giga.model.completion.ChatFunctionParametersProperty` (gigachat-java-0.1.13)

**Purpose**: Defines a single parameter property in JSON Schema.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| type | String | Yes | Property type (string, integer, etc.) |
| description | String | No | Property description |
| enum | List<String> | No | Enum values |
| items | ChatFunctionParametersProperty | No | Item type (for arrays) |

**Validation Rules**:
- `type` must be a valid JSON Schema type
- If `enum` is provided, must contain at least one value

**Builder Pattern**: `ChatFunctionParametersProperty.builder()` provides fluent API for construction.

---

### Choice

**Source**: `chat.giga.model.completion.Choice` (gigachat-java-0.1.13)

**Purpose**: Represents a single choice in the completion response.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| index | Integer | Yes | Choice index (always 0 for single choice) |
| message | ChoiceMessage | Yes | The message content |
| finishReason | ChoiceFinishReason | Yes | Reason generation stopped |

**Finish Reason Enum** - `ChoiceFinishReason`:
- `STOP`: Model stopped naturally
- `LENGTH`: Model stopped due to max tokens
- `TOOL_CALLS`: Model initiated tool calls

**Validation Rules**:
- `index` is 0 for non-streaming responses
- `finishReason` must be one of the enum values

**Builder Pattern**: `Choice.builder()` provides fluent API for construction.

---

### ChoiceMessage

**Source**: `chat.giga.model.completion.ChoiceMessage` (gigachat-java-0.1.13)

**Purpose**: Represents the message in a choice.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| role | ChatMessageRole | Yes | Message role (always ASSISTANT) |
| content | String | No | Message text content |
| functionCall | ChoiceMessageFunctionCall | No | Function call data |

**Validation Rules**:
- `role` is always `ASSISTANT` for completion responses
- Either `content` or `functionCall` (or both)

**Builder Pattern**: `ChoiceMessage.builder()` provides fluent API for construction.

---

### ChoiceMessageFunctionCall

**Source**: `chat.giga.model.completion.ChoiceMessageFunctionCall` (gigachat-java-0.1.13)

**Purpose**: Represents a function call in a choice message.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| name | String | Yes | Function name |
| arguments | String | Yes | JSON-encoded function arguments |

**Validation Rules**:
- `name` must match a defined function
- `arguments` must be valid JSON

**Builder Pattern**: `ChoiceMessageFunctionCall.builder()` provides fluent API for construction.

---

### Usage

**Source**: `chat.giga.model.completion.Usage` (gigachat-java-0.1.13)

**Purpose**: Token usage statistics.

**Key Fields**:

| Field | Type | Required | Description |
|--------|--------|-----------|-------------|
| promptTokens | Integer | Yes | Tokens in prompt |
| completionTokens | Integer | Yes | Tokens in completion |
| totalTokens | Integer | Yes | Total tokens |

**Validation Rules**:
- `totalTokens` = `promptTokens` + `completionTokens`
- All values must be non-negative integers

**Builder Pattern**: `Usage.builder()` provides fluent API for construction.

---

## Entity Relationships

```
CompletionRequest
├── messages (List<ChatMessage>)
│   ├── role (ChatMessageRole)
│   ├── content (String)
│   ├── toolCallId (String) - for TOOL role
│   └── functionCall (ChoiceMessageFunctionCall) - for ASSISTANT role
│       ├── name (String)
│       └── arguments (String - JSON)
├── tools (List<ChatFunction>)
│   ├── name (String)
│   ├── description (String)
│   └── parameters (ChatFunctionParameters)
│       ├── type (String)
│       ├── properties (Map<String, ChatFunctionParametersProperty>)
│       └── required (List<String>)
└── [other request parameters]

CompletionResponse
├── id (String)
├── object (String)
├── created (Long)
├── model (String)
├── choices (List<Choice>)
│   ├── index (Integer)
│   ├── message (ChoiceMessage)
│   │   ├── role (ChatMessageRole)
│   │   ├── content (String)
│   │   └── functionCall (ChoiceMessageFunctionCall)
│   │       ├── name (String)
│   │       └── arguments (String - JSON)
│   └── finishReason (ChoiceFinishReason)
└── usage (Usage)
    ├── promptTokens (Integer)
    ├── completionTokens (Integer)
    └── totalTokens (Integer)
```

## Conversion Logic

### Request Conversion (GigaChat Native → langchain4j)

The application layer converts `CompletionRequest` to langchain4j's `ChatRequest` for provider interaction:

1. Map `ChatMessage` → langchain4j message types based on `ChatMessageRole`
2. Map `ChatFunction` → langchain4j `ToolSpecification`
3. Preserve all request parameters (temperature, topP, etc.)

### Response Conversion (langchain4j → GigaChat Native)

The application layer converts langchain4j `ChatResponse` to `CompletionResponse`:

1. Map response ID, timestamp, model name
2. Convert AI message to `ChoiceMessage`
3. Map token usage to `Usage`
4. Determine `finishReason` based on response state

## Notes

- All native classes use Builder pattern for construction
- Enums are used for typed values (ChatMessageRole, ChoiceFinishReason)
- The `stream` flag in `CompletionRequest` is silently ignored
- Tool calling is fully supported through native `ChatFunction` and `ChoiceMessageFunctionCall` classes
