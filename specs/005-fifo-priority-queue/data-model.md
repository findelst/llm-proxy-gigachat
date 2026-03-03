# Data Model: FIFO Priority Queue

**Feature**: 005-fifo-priority-queue
**Date**: 2026-03-03

## Overview

Эта фича не добавляет новые сущности - она удаляет существующие (per-priority semaphores). Документируем изменённые сущности.

## Entities

### PrioritizedItem (Unchanged)

Элемент в приоритетной очереди.

| Field | Type | Description |
|-------|------|-------------|
| item | T | Сам запрос |
| priority | Int | Уровень приоритета (1=P1, 2=P2, 3=P3) |
| sequenceNumber | Long | Порядковый номер для FIFO |

**Invariants**:
- `priority` должен быть положительным числом
- `sequenceNumber` монотонно возрастает

### QueuedRequest (Unchanged)

Запрос в очереди с отложенным результатом.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Уникальный идентификатор запроса |
| payload | T | Тело запроса |
| priority | Priority | Приоритет (P1, P2, P3) |
| deferred | CompletableDeferred<R> | Отложенный результат |
| metrics | QueueMetrics | Метрики запроса |

### QueueMetrics (Unchanged)

Метрики запроса в очереди.

| Field | Type | Description |
|-------|------|-------------|
| requestId | String | ID запроса |
| priority | Priority | Приоритет |
| endpoint | String | Endpoint API |
| queuedAt | Instant | Время постановки в очередь |
| startedAt | Instant? | Время начала обработки |
| completedAt | Instant? | Время завершения |
| retryAttempts | Int | Количество retry попыток |
| lastRetryAt | Instant? | Время последнего retry |

### Priority (Unchanged)

Перечисление уровней приоритета.

| Value | Level | Description |
|-------|-------|-------------|
| P1 | 1 | Highest priority |
| P2 | 2 | Medium priority |
| P3 | 3 | Lowest priority |

## Relationships

```
┌─────────────────┐
│ PriorityChannel │
│  (single queue) │
└────────┬────────┘
         │ contains
         ▼
┌─────────────────┐
│ PrioritizedItem │
└────────┬────────┘
         │ wraps
         ▼
┌─────────────────┐
│ QueuedRequest   │
│  - id           │
│  - payload      │
│  - priority     │
│  - deferred     │
│  - metrics      │
└─────────────────┘
```

## State Transitions

### Request Lifecycle

```
[Client Request]
       │
       ▼ enqueue()
┌──────────────┐
│   QUEUED     │ ← In PriorityChannel
└──────┬───────┘
       │ receive() by consumer
       ▼
┌──────────────┐
│  PROCESSING  │ ← RetryExecutor active
└──────┬───────┘
       │
       ├─────────────────┐
       │                 │
       ▼                 ▼
┌──────────────┐  ┌──────────────┐
│   SUCCESS    │  │   FAILED     │
└──────────────┘  └──────────────┘
```

### No Per-Priority Concurrency Control

Ранее существовали состояния "waiting for semaphore". Теперь запросы обрабатываются сразу после извлечения из очереди.

```
Before (with semaphores):
QUEUED → WAITING_SEMAPHORE → PROCESSING → SUCCESS/FAILED

After (without semaphores):
QUEUED → PROCESSING → SUCCESS/FAILED
```

## Removed Entities

### PrioritySemaphore (REMOVED)

Ранее: семафор для ограничения конкурентности каждого приоритета.

**Reason for removal**: Пользователь не нуждается в per-priority ограничениях.

### PrioritySlot (DEPRECATED)

Конфигурация слотов для каждого приоритета.

**Status**: Будет игнорироваться с warning в логах.
