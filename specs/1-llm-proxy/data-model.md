# Data Model: LLM Proxy

**Feature**: 1-llm-proxy
**Date**: 2026-03-02

## Entity Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Domain Layer                                 │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐         │
│  │  Priority   │    │ QueueMetrics │    │    CacheKey     │         │
│  │ (Value Obj) │    │   (Entity)   │    │  (Value Obj)    │         │
│  └─────────────┘    └──────────────┘    └─────────────────┘         │
│                                                                      │
│  ┌─────────────────────┐    ┌─────────────────────────────┐         │
│  │  QueuedRequest<T>   │    │    PrioritySlot (Config)    │         │
│  │      (Entity)       │    │        (Value Obj)          │         │
│  └─────────────────────┘    └─────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      External Types (langchain4j-gigachat)           │
├─────────────────────────────────────────────────────────────────────┤
│  ChatCompletionRequest  │  ChatMessage  │  ChatCompletionResponse   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Domain Entities

### Priority (Value Object)

**Purpose**: Представляет уровень приоритета запроса

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `value` | String | Имя приоритета (p1, p2, p3...) | Non-empty, matches `^p[0-9]+$` |
| `level` | Int | Числовое значение для сравнения | Positive integer |

**Predefined Values**:
- `P1` (highest) → level 1
- `P2` (default) → level 2
- `P3` (lowest) → level 3

**Behavior**:
- `Comparable<Priority>` - сортировка по level
- `resolve(input: String?): Priority` - резолвинг из строки/заголовка

**State Transitions**: N/A (immutable)

---

### QueueMetrics (Entity)

**Purpose**: Метрики выполнения запроса в очереди

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `requestId` | String | UUID запроса | Valid UUID |
| `priority` | Priority | Использованный приоритет | Non-null |
| `endpoint` | String | Эндпоинт запроса | Non-empty |
| `queuedAt` | Instant | Время постановки в очередь | Non-null |
| `startedAt` | Instant? | Время начала обработки | Null until started |
| `completedAt` | Instant? | Время завершения | Null until completed |

**Computed Properties**:
- `queueWaitMs: Long?` = startedAt - queuedAt (ms)
- `providerLatencyMs: Long?` = completedAt - startedAt (ms)

---

### CacheKey (Value Object)

**Purpose**: Детерминированный ключ для кэширования

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `hash` | String | SHA-256 hex digest | 64 chars, hex |
| `providerUrl` | String | Base URL провайдера | Valid URL |

**Generation**:
```kotlin
fun generate(request: ChatCompletionRequest, providerUrl: String): CacheKey {
    val json = Json { encodeDefaults = false; sortKeys = true }
        .encodeToString(request)
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(json.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return CacheKey(hash, providerUrl)
}
```

---

### QueuedRequest<T> (Entity)

**Purpose**: Запрос в очереди с контекстом

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | UUID запроса |
| `priority` | Priority | Приоритет |
| `payload` | T | Данные запроса (ChatCompletionRequest) |
| `metrics` | QueueMetrics | Метрики |
| `deferred` | CompletableDeferred<T> | Результат для ожидающих |

---

### PrioritySlot (Configuration Value Object)

**Purpose**: Конфигурация слота приоритета

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `name` | String | Имя приоритета | Non-empty |
| `maxConcurrency` | Int | Максимальная параллельность | > 0 |

---

## External Types (langchain4j-gigachat)

### ChatCompletionRequest

Используется напрямую из библиотеки. Основные поля:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `model` | String? | No | Идентификатор модели |
| `messages` | List<ChatMessage> | Yes | Сообщения чата |
| `temperature` | Double? | No | Температура (0-2) |
| `topP` | Double? | No | Nucleus sampling (0-1) |
| `maxTokens` | Int? | No | Максимум токенов |
| `stop` | List<String>? | No | Stop sequences |
| `stream` | Boolean | No | Должно быть false |

### ChatMessage (sealed)

Поддерживаемые роли:
- `SystemMessage` - системные инструкции
- `UserMessage` - сообщение пользователя
- `AiMessage` - ответ ассистента
- `ToolExecutionResultMessage` - результат tool

### ChatCompletionResponse

Тип ответа из библиотеки для возврата клиенту.

---

## Repository Interfaces

### CacheRepository

```kotlin
interface CacheRepository {
    suspend fun get(key: CacheKey): ChatCompletionResponse?
    suspend fun set(key: CacheKey, value: ChatCompletionResponse)
    suspend fun invalidate(key: CacheKey)
    fun stats(): CacheStats
}

data class CacheStats(
    val hits: Long,
    val misses: Long,
    val sets: Long,
    val expired: Long
)
```

---

## Validation Rules

### Request Validation

| Rule | Error Code | Message |
|------|------------|---------|
| `messages` is empty | `invalid_request` | "messages field is required and must not be empty" |
| `stream == true` | `invalid_request` | "Streaming is not supported" |
| Invalid priority format | `invalid_request` | "Invalid priority value: {value}" |
| `content` is null | `invalid_request` | "Message content must not be null" |

### Priority Resolution

| Input | Resolved Priority |
|-------|-------------------|
| `null` | Default (P2) |
| `"highest"`, `"p0"` | P1 (first priority) |
| `"lowest"` | Last configured priority |
| `"p1"`, `"p2"`, `"p3"` | Corresponding priority |
| Other | Error: invalid_request |

---

## Relationships

```
┌─────────────────────┐
│ ChatCompletionReq   │ (langchain4j-gigachat)
└──────────┬──────────┘
           │ creates
           ▼
┌─────────────────────┐      ┌─────────────────────┐
│   QueuedRequest     │─────►│    QueueMetrics     │
│ <ChatCompletionReq> │ 1:1  │                     │
└──────────┬──────────┘      └─────────────────────┘
           │ has
           ▼
┌─────────────────────┐
│     Priority        │
└─────────────────────┘

┌─────────────────────┐      ┌─────────────────────┐
│ ChatCompletionReq   │─────►│     CacheKey        │
│                     │ gen  │                     │
└─────────────────────┘      └──────────┬──────────┘
                                        │ maps to
                                        ▼
                             ┌─────────────────────┐
                             │ ChatCompletionResp  │ (langchain4j-gigachat)
                             └─────────────────────┘
```

---

## State Diagrams

### Queue Request Lifecycle

```
    ┌──────────┐
    │ Received │
    └────┬─────┘
         │ validate
         ▼
    ┌──────────┐     overflow     ┌──────────┐
    │ Valid    ├─────────────────►│ Rejected │
    └────┬─────┘                  │ (429)    │
         │ enqueue                └──────────┘
         ▼
    ┌──────────┐
    │ Queued   │◄──────────────┐
    └────┬─────┘               │
         │ slot available      │ more requests
         ▼                     │
    ┌──────────┐               │
    │Processing│               │
    └────┬─────┘               │
         │ complete            │
         ▼                     │
    ┌──────────┐               │
    │Completed │───────────────┘
    └──────────┘
```

### Cache Lookup Flow

```
    ┌──────────┐
    │  Request │
    └────┬─────┘
         │ generate key
         ▼
    ┌──────────┐
    │Cache Hit?│
    └────┬─────┘
         │
    ┌────┴────┐
    │         │
   Yes        No
    │         │
    ▼         ▼
┌───────┐ ┌───────────┐
│Return │ │Call Giga  │
│Cached │ │Chat       │
└───────┘ └─────┬─────┘
                │ store in cache
                ▼
          ┌───────────┐
          │Return     │
          │Response   │
          └───────────┘
```
