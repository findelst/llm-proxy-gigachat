# Feature Specification: Queue Integration for Priority-Based Request Processing

**Feature Branch**: `004-queue-integration`
**Created**: 2026-03-03
**Status**: Draft
**Input**: User description: "Wire Queue into ChatApplicationService for priority-based request processing with bounded parallelism to GigaChat API"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request Processing Through Priority Queue (Priority: P1)

Как клиент системы, я хочу, чтобы мои запросы обрабатывались через очередь с приоритетами, чтобы критические запросы (p1) обрабатывались быстрее обычных при высокой нагрузке.

**Why this priority**: Это критическая функциональность - без интеграции очереди все запросы идут напрямую к провайдеру без ограничений параллелизма, что может привести к перегрузке GigaChat API и нарушению rate limits.

**Independent Test**: Можно протестировать отправку запросов с разными приоритетами и проверить, что очередь ограничивает параллелизм согласно конфигурации priority-slots.

**Acceptance Scenarios**:

1. **Given** ChatApplicationService обрабатывает запрос, **When** запрос поступает через completions(), **Then** запрос проходит через QueueService.enqueue() вместо прямого вызова провайдера
2. **Given** все слоты приоритета p1 заняты, **When** приходит новый запрос с приоритетом p1, **Then** запрос ставится в очередь и ждёт освобождения слота
3. **Given** в очереди есть запросы p1 и p3, **When** освобождается слот, **Then** первым обрабатывается запрос p1
4. **Given** запрос обработан через очередь, **When** возвращается ответ, **Then** метрики очереди (queue_wait_ms) корректно отражают время ожидания

---

### User Story 2 - Queue Overflow Handling (Priority: P1)

Как клиент системы, я хочу получать понятное сообщение об ошибке, когда очередь переполнена, чтобы знать, что нужно повторить запрос позже.

**Why this priority**: Обработка переполнения очереди критична для user experience и соответствует требованию FR-QUEUE-005 из базовой спецификации.

**Independent Test**: Можно отправить больше запросов чем maxLen очереди и проверить HTTP 429 с кодом queue_overflow.

**Acceptance Scenarios**:

1. **Given** очередь заполнена до максимума (maxLen), **When** приходит новый запрос, **Then** возвращается HTTP 429 с кодом ошибки `queue_overflow`
2. **Given** очередь переполнена, **When** возвращается ошибка, **Then** тело ответа содержит понятное сообщение "Queue is full. Try again later."
3. **Given** произошёл queue overflow, **When** смотрим метрики, **Then** счётчик llm_proxy_queue_overflow_total инкрементирован

---

### User Story 3 - Priority Resolution with GigaChat Native Format (Priority: P1)

Как клиент системы, я хочу указывать приоритет запроса через заголовок X-Priority при использовании GigaChat native формата, чтобы мои критические запросы обрабатывались в первую очередь.

**Why this priority**: Интеграция приоритезации с GigaChat native форматом необходима для полной совместимости с текущей реализацией контроллера.

**Independent Test**: Можно отправить запрос с заголовком X-Priority: p1 и проверить, что в ответе header x-llm-proxy-priority содержит "p1".

**Acceptance Scenarios**:

1. **Given** запрос содержит заголовок X-Priority: p1, **When** запрос обрабатывается, **Then** приоритет p1 используется для выбора очереди
2. **Given** запрос не содержит заголовок X-Priority, **When** запрос обрабатывается, **Then** используется приоритет по умолчанию из конфигурации (p2)
3. **Given** запрос содержит невалидный приоритет X-Priority: invalid, **When** запрос обрабатывается, **Then** возвращается HTTP 400 с кодом ошибки `invalid_request`

---

### User Story 4 - Application Startup Queue Initialization (Priority: P2)

Как оператор системы, я хочу, чтобы очередь инициализировалась при старте приложения, чтобы запросы сразу обрабатывались через очередь.

**Why this priority**: Корректная инициализация очереди необходима для работы системы, но может быть временно обходится отложенным стартом.

**Independent Test**: Можно проверить логи при старте приложения - должно быть сообщение "Started processing for all priority levels".

**Acceptance Scenarios**:

1. **Given** приложение запускается, **When** Spring контекст инициализирован, **Then** CoroutinePriorityQueue.startProcessing() вызван
2. **Given** очередь инициализирована, **When** проверяем состояние, **Then** все priority channels готовы к приёму запросов

---

### Edge Cases

- Что происходит при shutdown приложения с запросами в очереди? → Очередь должна корректно закрыться, in-flight запросы должны завершиться
- Что происходит при ошибке в processor function? → Запрос должен завершиться с ошибкой, semaphore освобождён, метрики записаны
- Что происходит при одновременном enqueue множества запросов? → Channel должен обработать все без потерь или вернуть overflow
- Что происходит если приоритет не найден в конфигурации? → Используется приоритет по умолчанию или возвращается ошибка

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: ChatApplicationService MUST использовать QueueService для обработки запросов вместо прямого вызова провайдера
- **FR-002**: QueueService MUST быть внедрён (injected) в ChatApplicationService через конструктор
- **FR-003**: При старте приложения MUST вызываться CoroutinePriorityQueue.startProcessing() для запуска обработки очередей
- **FR-004**: Каждый запрос MUST проходить через очередь с приоритетом, определённым через PriorityResolver
- **FR-005**: При переполнении очереди MUST выбрасываться QueueOverflowError и возвращаться HTTP 429
- **FR-006**: Время ожидания в очереди (queue_wait_ms) MUST корректно вычисляться и возвращаться в метриках
- **FR-007**: Semaphore для каждого приоритета MUST ограничивать параллелизм согласно конфигурации priority-slots
- **FR-008**: FIFO порядок MUST поддерживаться в рамках одного приоритета
- **FR-009**: Метрики очереди (queue_length, in_flight, queue_overflow_total) MUST обновляться при каждом событии
- **FR-010**: При shutdown приложения очередь MUST корректно закрываться через shutdown()

### Key Entities

- **QueuedRequest**: Запрос в очереди с приоритетом, содержит payload (ChatRequest), priority, id, и Deferred для результата
- **QueueService**: Сервис управления очередью с методами enqueue, tryEnqueue, queueLength, inFlight, hasCapacity
- **Priority**: Уровень приоритета (P1, P2, P3) с числовым level для сравнения
- **PrioritySlot**: Конфигурация слота приоритета с именем и maxConcurrency

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Запросы с приоритетом p1 обрабатываются раньше запросов p2/p3 при одинаковой позиции в очереди
- **SC-002**: Количество параллельных запросов к провайдеру не превышает сумму всех maxConcurrency из priority-slots
- **SC-003**: Время ожидания в очереди (queue_wait_ms) корректно отражается в метриках и заголовках ответа
- **SC-004**: При переполнении очереди возвращается HTTP 429 с кодом queue_overflow в течение 10ms
- **SC-005**: Все запросы через GigaChat native format (CompletionRequest) корректно проходят через очередь
- **SC-006**: Метрики Prometheus (llm_proxy_queue_length, llm_proxy_in_flight) доступны и корректны

## Assumptions

- Существующая реализация CoroutinePriorityQueue корректна и готова к использованию
- PriorityResolver уже внедрён в ChatApplicationService
- Конфигурация priority-slots в application.yml корректна
- GigaChatProvider готов к вызову через processor function очереди
- Приложение использует Kotlin coroutines для асинхронной обработки

## Out of Scope

- Изменение логики приоритезации (используется существующий PriorityResolver)
- Добавление новых уровней приоритета
- Распределённая очередь между инстансами
- Streaming режим (уже out of scope в базовой спецификации)
