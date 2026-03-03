# Research: Queue Timeout & Cleanup

**Feature**: 006-queue-timeout
**Date**: 2026-03-03

## Research Summary

Это фича добавляет механизм timeout для запросов в очереди. Исследование фокусируется на паттернах cleanup и интеграции с существующей архитектурой.

## Technical Decisions

### Decision 1: Periodic Cleanup vs Per-Request Timer

**Decision**: Использовать периодический cleanup (каждую минуту) вместо индивидуальных таймеров для каждого запроса

**Rationale**:
- Меньше overhead - один таймер вместо N таймеров
- Проще реализация - не нужно управлять жизненным циклом таймеров
- Предсказуемая нагрузка на систему
- Точность cleanup = interval (1 минута) - приемлемо для бизнес-требований

**Alternatives Considered**:
- Индивидуальные таймеры для каждого запроса → отвергнуто: слишком много overhead при большом количестве запросов
- Delayed queue → отвергнуто: усложняет архитектуру без явной выгоды

### Decision 2: Exception Type

**Decision**: Создать отдельный `QueueTimeoutException` отличный от `QueueOverflowException`

**Rationale**:
- Разные причины ошибки (timeout vs capacity)
- Разные HTTP статусы (408 Request Timeout vs 429 Too Many Requests)
- Помогает клиентам различать сценарии и применять разные стратегии retry

**HTTP Status Mapping**:
- QueueOverflowException → 429 Too Many Requests
- QueueTimeoutException → 408 Request Timeout

### Decision 3: Cleanup Implementation

**Decision**: Реализовать cleanup как отдельную coroutine в CoroutinePriorityQueue

**Rationale**:
- Интеграция с существующим scope
- Использует тот же dispatcher
- Корректная отмена при shutdown

**Implementation Pattern**:
```kotlin
private fun startTimeoutCleanup() {
    scope.launch {
        while (!isShutdown) {
            delay(properties.queue.timeoutCheckIntervalMs)
            cleanupExpiredRequests()
        }
    }
}
```

### Decision 4: Access to Queued Requests for Cleanup

**Decision**: Добавить метод `removeExpired()` в PriorityChannel

**Rationale**:
- Инкапсуляция логики доступа к очереди
- PriorityChannel владеет PriorityBlockingQueue
- Безопасный доступ через Mutex

**Method Signature**:
```kotlin
suspend fun removeExpired(
    maxAgeMs: Long,
    onExpired: (T) -> Unit
): Int  // Returns count of removed items
```

### Decision 5: Where to Track queuedAt Timestamp

**Decision**: Использовать существующее поле `QueueMetrics.queuedAt`

**Rationale**:
- Уже существует в текущей реализации
- Не требует изменений в QueuedRequest
- Доступно через `request.metrics.queuedAt`

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Race condition: request expires during processing | Low | Medium | Check in-flight status before removing |
| Memory leak if cleanup fails | Low | High | Log errors, continue cleanup loop |
| Too aggressive cleanup (wrong config) | Medium | Medium | Validate config, sensible defaults |

## Implementation Approach

### Files to Create/Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `Exceptions.kt` | MODIFY | Add QueueTimeoutException |
| `LlmProxyProperties.kt` | MODIFY | Add timeout config |
| `MetricsPort.kt` | MODIFY | Add recordQueueTimeout |
| `PrometheusMetrics.kt` | MODIFY | Implement timeout counter |
| `PriorityChannel.kt` | MODIFY | Add removeExpired method |
| `CoroutinePriorityQueue.kt` | MODIFY | Add cleanup coroutine |
| `GlobalExceptionHandler.kt` | MODIFY | Handle QueueTimeoutException |
| `application.yml` | MODIFY | Add timeout config |
| `QueueTimeoutTest.kt` | CREATE | Unit tests for timeout |

### Files Unchanged

- `QueuedRequest.kt` - Already has queuedAt via QueueMetrics
- `QueueMetrics.kt` - Already has queuedAt field
- `QueueService.kt` - Interface unchanged
- All domain models except Exceptions.kt

## Conclusion

Research phase complete. No NEEDS CLARIFICATION items remain. Ready for Phase 1 design.
