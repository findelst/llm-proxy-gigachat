# Research: FIFO Priority Queue

**Feature**: 005-fifo-priority-queue
**Date**: 2026-03-03

## Research Summary

Это refactoring задача - удаление per-priority семафоров из существующей реализации. Исследование не требуется, так как:

1. **Существующая архитектура уже корректна**: PriorityChannel и PrioritizedItem уже реализованы и работают правильно
2. **Требуется только удаление кода**: Убрать `prioritySemaphores` и связанную логику из CoroutinePriorityQueue
3. **Паттерны известны**: Kotlin coroutines, structured concurrency, priority queue

## Technical Decisions

### Decision 1: Remove Per-Priority Semaphores

**Decision**: Полностью удалить `prioritySemaphores: Map<Priority, Semaphore>` из CoroutinePriorityQueue

**Rationale**:
- Пользователь явно указал, что per-priority ограничения не нужны
- Порядок обработки (FIFO + приоритеты) важнее конкурентности
- Глобальный лимит параллельных запросов контролируется на уровне HTTP клиента

**Alternatives Considered**:
- Оставить семафоры опционально → отвергнуто: усложняет конфигурацию без пользы
- Заменить на глобальный семафор → отвергнуто: HTTP клиент уже ограничивает

### Decision 2: Keep PriorityChannel As-Is

**Decision**: Сохранить текущую реализацию PriorityChannel без изменений

**Rationale**:
- Already implements correct priority ordering
- Already implements FIFO within same priority via sequenceNumber
- Thread-safe with Mutex and PriorityBlockingQueue

**Alternatives Considered**:
- Переписать на чистый Channel → отвергнуто: текущая реализация работает

### Decision 3: Deprecate PrioritySlot Configuration

**Decision**: Удалить `priority-slots` из конфигурации, игнорировать если указаны

**Rationale**:
- Configuration no longer needed without semaphores
- Log warning if old config is present (backward compatibility)

**Alternatives Considered**:
- Keep config unused → отвергнуто: путает пользователей
- Fail on old config → отвергнуто: ломает обновление

### Decision 4: Simplify In-Flight Metrics

**Decision**: Сохранить per-priority in-flight метрики для наблюдаемости

**Rationale**:
- Метрики полезны для мониторинга даже без ограничения конкурентности
- Минимальные изменения кода

**Alternatives Considered**:
- Убрать per-priority метрики → отвергнуто: теряем observability
- Только глобальные метрики → может быть добавлено позже

## Implementation Approach

### Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `CoroutinePriorityQueue.kt` | MODIFY | Remove semaphores, simplify processing |
| `LlmProxyProperties.kt` | MODIFY | Deprecate priority-slots in QueueConfig |
| `application.yml` | MODIFY | Remove priority-slots section |
| `PriorityQueueConcurrencyTest.kt` | MODIFY | Update tests for new behavior |

### Files Unchanged

- `PriorityChannel.kt` - Already correct
- `PrioritizedItem.kt` - Already correct
- `RetryExecutor.kt` - No changes needed
- `QueueService.kt` - Interface unchanged
- All domain models - No changes

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Existing tests fail | Low | Medium | Run full test suite after changes |
| Performance regression | Low | Low | No blocking operations added |
| Config migration issues | Low | Low | Log warning, continue working |

## Conclusion

Research phase complete. No NEEDS CLARIFICATION items remain. Ready for Phase 1 design.
