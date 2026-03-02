# Quickstart Guide: GigaChat Native Model Classes Integration

**Feature**: [spec.md](../spec.md) | **Date**: 2026-03-02

## Overview

This guide provides quick instructions for working with the refactored LLM Proxy that uses GigaChat's native model classes directly.

## Prerequisites

- Java 21 or higher
- Gradle 8.x
- GigaChat API credentials (auth key)
- The `gigachat-java-0.1.13.jar` library dependency

## Setup

### 1. Clone and Build

```bash
git clone <repository-url>
cd llm-proxy-kotlin
./gradlew build
```

### 2. Configure GigaChat API Key

Create or update `application.yml`:

```yaml
llm-proxy:
  api:
    key: "your-gigachat-api-key"
    base-url: "https://gigachat.devices.sberbank.ru/api/v1"
  model:
    name: "GigaChat"
```

Or use environment variables:

```bash
export LLM_PROXY_API_KEY="your-gigachat-api-key"
export LLM_PROXY_BASE_URL="https://gigachat.devices.sberbank.ru/api/v1"
```

### 3. Run the Service

```bash
./gradlew bootRun
```

The service will be available at `http://localhost:8080`

## Usage Examples

### Basic Chat Request

Using curl:

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat",
    "messages": [
      {
        "role": "USER",
        "content": "Hello, how are you?"
      }
    ]
  }'
```

Using Python:

```python
import requests

response = requests.post(
    "http://localhost:8080/api/v1/chat/completions",
    headers={"Content-Type": "application/json"},
    json={
        "model": "GigaChat",
        "messages": [
            {"role": "USER", "content": "Hello, how are you?"}
        ]
    }
)
print(response.json())
```

### Chat with System Prompt

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat",
    "messages": [
      {
        "role": "SYSTEM",
        "content": "You are a helpful assistant that speaks concisely."
      },
      {
        "role": "USER",
        "content": "What is the capital of France?"
      }
    ]
  }'
```

### Tool Calling (Function Execution)

Define a tool for the LLM to call:

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat",
    "messages": [
      {
        "role": "USER",
        "content": "What'\''s the weather in Moscow?"
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
              "description": "The city name, e.g. Moscow"
            }
          },
          "required": ["location"]
        }
      }
    }
    ],
    "toolChoice": "auto"
  }'
```

### Multi-turn Conversation with Tool Results

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "GigaChat",
    "messages": [
      {
        "role": "USER",
        "content": "What'\''s the weather in Moscow?"
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
  }'
```

## Native GigaChat Classes

The proxy now uses GigaChat's native classes from `gigachat-java-0.1.13`. This means:

- **Request Format**: Exact match with GigaChat API specification
- **Response Format**: Exact match with GigaChat API specification
- **Full Compatibility**: Existing GigaChat clients work without modification

### Available Classes

| Class | Package | Purpose |
|--------|----------|----------|
| CompletionRequest | chat.giga.model.completion | Request body |
| CompletionResponse | chat.giga.model.completion | Response body |
| ChatMessage | chat.giga.model.completion | Message in conversation |
| ChatFunction | chat.giga.model.completion | Function definition |
| Choice | chat.giga.model.completion | Response choice |
| Usage | chat.giga.model.completion | Token usage |

## Common Parameters

### Model Names

- `GigaChat` - Default model
- `GigaChat:latest` - Latest version
- `GigaChat:plus` - Enhanced model

### Message Roles

- `SYSTEM` - System instructions
- `USER` - User messages
- `ASSISTANT` - Assistant responses
- `TOOL` - Tool execution results

### Finish Reasons

- `STOP` - Natural completion
- `LENGTH` - Max tokens reached
- `TOOL_CALLS` - Tool call initiated

## Important Notes

### Streaming Not Supported

The `stream=true` parameter is **accepted but ignored**. All responses are complete (non-streaming). This design choice simplifies the implementation while maintaining API compatibility.

### Tool Calling

1. Define tools in the `tools` array
2. Set `toolChoice` to control behavior:
   - `"auto"` - Model decides whether to call tools (default)
   - `"none"` - Never call tools
   - `{"type": "function", "function": {"name": "tool_name"}}` - Force specific tool

3. Process tool calls in responses:
   - Check `functionCall` field in assistant messages
   - Execute the function with the provided `arguments`
   - Send the result as a `TOOL` role message with `toolCallId`

### Error Handling

The proxy returns standard GigaChat-compatible error responses:

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "authentication_error",
    "code": "invalid_api_key"
  }
}
```

## Testing

Run the integration tests:

```bash
./gradlew test
```

Run specific tests:

```bash
./gradlew test --tests FunctionCallScenarioTest
```

## Troubleshooting

### "Invalid API Key" Error

- Verify your GigaChat API key is correct
- Check the key is configured in `application.yml` or environment variables

### Rate Limit Errors

- Reduce request frequency
- Check your GigaChat account rate limits

### "Module not found" for gigachat-java

Ensure the dependency is in `build.gradle.kts`:

```kotlin
implementation("ru.gigachat.sdk:gigachat-java:0.1.13")
```

## Further Reading

- [API Contract](./contracts/api-contract.md) - Detailed API specification
- [Data Model](./data-model.md) - Entity definitions and relationships
- [Feature Spec](./spec.md) - Complete feature specification
- [Implementation Plan](./plan.md) - Technical implementation details
