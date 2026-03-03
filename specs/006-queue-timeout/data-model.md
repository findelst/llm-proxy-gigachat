# Data Model: Queue Timeout & Cleanup

**Feature**: 006-queue-timeout
**Date**: 2026-03-03

## Overview

Эта фича добавляет механизм timeout для запросов в очереди. Основные изменения - новая конфигурация и новый тип исключения.

## New Entities

### QueueTimeoutConfig (embedded in QueueConfig)

Конфигурация timeout для очереди.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| timeoutMinutes | Long | 10 | Максимальное время ожидания в минутах |
| timeoutCheckIntervalMs | Long | 60000 | Интервал проверки в миллисекундах |

**Invariants**:
- `timeoutMinutes` >= 0 (0 = disabled)
- `timeoutCheckIntervalMs` > 0

### QueueTimeoutException (NEW)

Исключение для запросов превысивших timeout.

| Field | Type | Description |
|-------|------|-------------|
| message | String | "Request timed out after waiting X minutes in queue" |
| priority | Priority | Приоритет истёкшего запроса |
| waitTimeMs | Long | Фактическое время ожидания в ms |

**Relationships**:
- Наследуется от `RuntimeException`
- Отличается от `QueueOverflowException` (capacity vs timeout)

## Modified Entities

### LlmProxyProperties.QueueConfig (MODIFIED)

Добавлены поля timeout.

```kotlin
data class QueueConfig(
    // ... existing fields
    val timeoutMinutes: Long = 10,           // NEW
    val timeoutCheckIntervalMs: Long = 60000  // NEW
)
```

### QueueMetrics (UNCHANGED)

Уже содержит поле `queuedAt: Instant` которое используется для определения времени ожидания.

## State Transitions

### Request Lifecycle with Timeout

```
[Client Request]
       │
       ▼ enqueue()
┌──────────────┐
│   QUEUED     │ ← queuedAt timestamp set
│   (waiting)  │ ← Periodic timeout check
└──────┬───────┘
       │
       ├─────────────────────┐
       │                     │
       │ timeout exceeded    │ picked for processing
       ▼                     ▼
┌──────────────┐      ┌──────────────┐
│   EXPIRED    │      │  PROCESSING  │
│ (timeout)    │      │              │
└──────────────┘      └──────┬───────┘
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────┐
│   408 Error  │      │   SUCCESS    │
│   returned   │      │   or FAILED  │
└──────────────┘      └──────────────┘
```

### Timeout Check Flow

```
┌─────────────────────────────────────────┐
│         Every 1 minute (configurable)    │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  For each item in PriorityChannel:      │
│    age = now - item.metrics.queuedAt    │
│    if age > timeoutMinutes * 60 * 1000  │
│      AND item NOT in processing          │
│    then                                  │
│      remove item from queue              │
│      completeExceptionally(Timeout)      │
│      record metrics                      │
└─────────────────────────────────────────┘
```

## Relationships

```
┌─────────────────────────────────────────┐
│           LlmProxyProperties            │
│  ┌─────────────────────────────────┐    │
│  │          QueueConfig            │    │
│  │  - maxLength                    │    │
│  │  - timeoutMinutes (NEW)         │    │
│  │  - timeoutCheckIntervalMs (NEW) │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│        CoroutinePriorityQueue           │
│  - Uses timeout config for cleanup      │
│  - Starts cleanup coroutine             │
└─────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│           PriorityChannel               │
│  - removeExpired(maxAgeMs, callback)    │
│  - Checks each item's queuedAt          │
└─────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────┐
│           QueuedRequest                 │
│  - metrics.queuedAt (already exists)    │
│  - completeExceptionally(exception)    │
└─────────────────────────────────────────┘
```

## HTTP Error Mapping

| Exception | HTTP Status | Message |
|-----------|-------------|---------|
| QueueOverflowException | 429 | "Queue is full" |
| QueueTimeoutException | 408 | "Request timed out after waiting X minutes in queue" |
