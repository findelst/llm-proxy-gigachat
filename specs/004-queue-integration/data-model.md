# Data Model: Queue Integration

**Feature**: 004-queue-integration
**Date**: 2026-03-03

## Overview

Эта фича не вводит новые entity - использует существующие из domain layer.

## Existing Entities (No Changes)

### QueuedRequest

**Location**: `src/main/kotlin/ru/ddd/llmproxy/domain/model/QueuedRequest.kt`

Запрос в очереди с приоритетом.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Уникальный идентификатор запроса |
| payload | T | Payload запроса (ChatRequest) |
| priority | Priority | Уровень приоритета |
| deferred | CompletableDeferred<R> | Deferred результат |

**Factory Method**:
```kotlin
companion object {
    fun <T : Any, R : Any> create(
        payload: T,
        priority: Priority,
        id: String = UUID.randomUUID().toString()
    ): QueuedRequest<T, R>
}
```

---

### Priority

**Location**: `src/main/kotlin/ru/ddd/llmproxy/domain/model/Priority.kt`

Уровень приоритета запроса.

| Value | Level | Description |
|-------|-------|-------------|
| P1 | 1 | Highest priority |
| P2 | 2 | Medium priority (default) |
| P3 | 3 | Lowest priority |

**Methods**:
- `resolve(value: String?, default: Priority = P2): Priority` - разрешает priority из string

---

### QueueService Interface

**Location**: `src/main/kotlin/ru/ddd/llmproxy/domain/service/QueueService.kt`

Interface для управления очередью.

| Method | Return Type | Description |
|--------|-------------|-------------|
| enqueue(request: QueuedRequest<T, R>) | R | Добавить запрос в очередь, блокирует до результата |
| tryEnqueue(request: QueuedRequest<T, R>) | Result<R> | Неблокирующее добавление |
| queueLength(priority: Priority) | Int | Длина очереди для приоритета |
| totalQueueLength() | Int | Общая длина очереди |
| inFlight(priority: Priority) | Int | Количество запросов в обработке |
| hasCapacity() | Boolean | Есть ли место в очереди |
| maxCapacity() | Int | Максимальная ёмкость |
| shutdown() | Unit | Graceful shutdown |

---

### QueueMetrics

**Location**: `src/main/kotlin/ru/ddd/llmproxy/domain/model/QueueMetrics.kt`

Метрики обработки запроса.

| Field | Type | Description |
|-------|------|-------------|
| requestId | String | ID запроса |
| priority | Priority | Приоритет |
| endpoint | String | API endpoint |
| enqueuedAt | Instant? | Время постановки в очередь |
| processingStartedAt | Instant? | Время начала обработки |
| processingCompletedAt | Instant? | Время завершения |
| queueWaitMs | Long? | Время ожидания в очереди |
| providerLatencyMs | Long? | Время ответа провайдера |

---

### PrioritySlot

**Location**: `src/main/kotlin/ru/ddd/llmproxy/domain/model/PrioritySlot.kt`

Конфигурация слота приоритета.

| Field | Type | Description |
|-------|------|-------------|
| name | String | Имя приоритета (p1, p2, p3) |
| maxConcurrency | Int | Максимальное количество параллельных запросов |

---

## Entity Relationships

```
┌─────────────────┐
│   ChatRequest   │ (from langchain4j)
│    (payload)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│ QueuedRequest   │─────▶│   Priority   │
│  - id           │      │   - P1/P2/P3 │
│  - payload      │      └──────────────┘
│  - priority     │
│  - deferred     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  QueueService   │
│    enqueue()    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  ChatResponse   │ (from langchain4j)
└─────────────────┘
```

## State Transitions

### QueuedRequest Lifecycle

```
┌──────────┐    enqueue()    ┌─────────┐    acquire()    ┌───────────┐
│  Created │ ──────────────▶ │ Queued  │ ──────────────▶ │ In-Flight │
└──────────┘                 └─────────┘                 └─────┬─────┘
                                                               │
                              ┌────────────────────────────────┤
                              │                                │
                              ▼                                ▼
                     ┌─────────────┐                  ┌─────────────┐
                     │  Completed  │                  │    Failed   │
                     └─────────────┘                  └─────────────┘
```

## Validation Rules

1. **Priority validation**: Только P1, P2, P3 допустимы
2. **Queue overflow**: При maxCapacity exceeded → QueueOverflowError
3. **FIFO within priority**: Запросы одного приоритета обрабатываются в порядке поступления
4. **Concurrency limit**: Semaphore ограничивает параллелизм до maxConcurrency
