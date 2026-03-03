# Research: Queue Integration for Priority-Based Request Processing

**Feature**: 004-queue-integration
**Date**: 2026-03-03

## Research Questions

### Q1: Как внедрить QueueService в ChatApplicationService?

**Decision**: Использовать Spring Constructor Injection

**Rationale**:
- Spring Boot автоматически внедряет зависимости через конструктор
- QueueService уже помечен как @Component (через CoroutinePriorityQueue)
- Constructor injection предпочтительнее для immutable dependencies

**Implementation**:
```kotlin
@Service
class ChatApplicationService(
    private val chatProvider: ChatProviderPort<ChatRequest>,
    private val queueService: QueueService<ChatRequest, ChatResponse>,  // NEW
    private val cacheRepository: CacheRepository<ChatResponse>,
    // ... other dependencies
)
```

**Alternatives Considered**:
- Setter injection - отклонено: менее предпочтительно для обязательных зависимостей
- Field injection - отклонено: затрудняет тестирование

---

### Q2: Как заменить прямой вызов провайдера на очередь?

**Decision**: Использовать QueuedRequest и queueService.enqueue()

**Rationale**:
- QueuedRequest уже существует в domain model
- enqueue() возвращает результат через Deferred
- Метрики очереди вычисляются автоматически в CoroutinePriorityQueue

**Implementation**:
```kotlin
// Before:
val response = chatProvider.generate(request)

// After:
val queuedRequest = QueuedRequest.create(
    payload = request,
    priority = priority,
    id = requestId
)
val response = queueService.enqueue(queuedRequest)
```

**Alternatives Considered**:
- Создать wrapper вокруг chatProvider - отклонено: избыточно, QueueService уже выполняет эту роль

---

### Q3: Как инициализировать обработку очереди при старте приложения?

**Decision**: Создать QueueStartupRunner реализующий ApplicationRunner

**Rationale**:
- ApplicationRunner выполняется после полной инициализации Spring контекста
- Позволяет передать processor function в CoroutinePriorityQueue
- Processor function вызывает chatProvider.generate()

**Implementation**:
```kotlin
@Component
class QueueStartupRunner(
    private val priorityQueue: CoroutinePriorityQueue<ChatRequest, ChatResponse>,
    private val chatProvider: ChatProviderPort<ChatRequest>
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        priorityQueue.startProcessing { request ->
            chatProvider.generate(request)
        }
    }
}
```

**Alternatives Considered**:
- @PostConstruct - отклонено: может выполниться до полной инициализации всех бинов
- CommandLineRunner - отклонено: ApplicationRunner предпочтительнее для доступа к аргументам

---

### Q4: Как обрабатывать cache с очередью?

**Decision**: Проверять cache ДО постановки в очередь

**Rationale**:
- Cache hit не должен занимать место в очереди
- Существующая логика cache lookup в ChatApplicationService сохраняется
- Только cache miss запросы идут в очередь

**Implementation**:
```kotlin
// Try cache first (existing logic)
if (properties.cache.enabled) {
    val cached = cacheRepository.get(cacheKey)
    if (cached != null) {
        return ChatResult(cached, metrics, cacheHit = true)
    }
}

// Then enqueue for processing
val response = queueService.enqueue(queuedRequest)
```

---

### Q5: Как обеспечить корректный shutdown очереди?

**Decision**: Использовать существующий @PreDestroy в CoroutinePriorityQueue

**Rationale**:
- CoroutinePriorityQueue уже имеет @PreDestroy метод destroy()
- Spring автоматически вызывает destroy() при shutdown
- In-flight запросы завершаются gracefull

**Verification**:
- Проверить что destroy() закрывает channels и отменяет scope
- Убедиться что нет утечки coroutines

---

## Summary

| Question | Decision | Risk Level |
|----------|----------|------------|
| DI approach | Constructor injection | Low |
| Queue usage | QueuedRequest + enqueue() | Low |
| Startup init | ApplicationRunner | Low |
| Cache handling | Check before enqueue | Low |
| Shutdown | Use existing @PreDestroy | Low |

**Overall Risk**: LOW - все компоненты уже существуют, требуются минимальные изменения.
