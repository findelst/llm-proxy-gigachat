# Feature Specification: Priority Proxy Service with Preemption

**Feature Branch**: `007-priority-proxy-service`
**Created**: 2026-03-12
**Status**: Draft
**Input**: User description: "Kotlin Spring Boot service that receives requests with priority p1/p2/p3 and forwards them with concurrency control: MAX_CONCURRENT total threads, p1 can preempt p2/p3 and take 2 threads, p3 throttled to max 1 thread"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - P1 Preemption (Priority: P1)

Как высокоприоритетный клиент, я хочу чтобы мои запросы (p1) обрабатывались немедленно, даже если все слоты заняты низкоприоритетными запросами.

**Why this priority**: Это ключевое отличие от обычной очереди - preemption обеспечивает гарантированную обработку критичных запросов.

**Independent Test**: Можно протестировать, заполнив все слоты p2 запросами, затем отправив p1 и проверив что p1 выполняется сразу.

**Acceptance Scenarios**:

1. **Given** все 3 слота заняты p2 запросами, **When** приходит p1 запрос, **Then** p1 preempt'ит 2 p2 запроса и выполняется немедленно
2. **Given** 2 слота заняты p1 запросами, **When** приходит ещё один p1, **Then** он встаёт в очередь (p1 max = 2)
3. **Given** p1 запрос в очереди, **When** освобождается слот, **Then** p1 выполняется первым

---

### User Story 2 - P3 Throttling (Priority: P2)

Как оператор системы, я хочу ограничить низкоприоритетные запросы (p3) максимум 1 параллельным выполнением, чтобы не блокировать ресурсы для более важных запросов.

**Why this priority**: Throttling p3 предотвращает monopolизацию ресурсов низкоприоритетными запросами.

**Independent Test**: Можно отправить несколько p3 запросов и проверить что только 1 выполняется одновременно.

**Acceptance Scenarios**:

1. **Given** p3 запрос выполняется, **When** приходит ещё один p3, **Then** он встаёт в очередь
2. **Given** p3 в очереди и слот свободен, **When** p3 max (1) достигнут, **Then** p3 ждёт
3. **Given** p3 в очереди, **When** текущий p3 завершается, **Then** следующий p3 из очереди начинает выполняться

---

### User Story 3 - Graceful Preemption (Priority: P2)

Как низкоприоритетный клиент, я хочу получить понятную ошибку когда мой запрос preempt'ится, а не бесконечное ожидание.

**Why this priority**: Graceful handling обеспечивает хороший UX даже при preemption.

**Independent Test**: Можно проверить что preempt'ed запрос получает корректный HTTP статус и сообщение.

**Acceptance Scenarios**:

1. **Given** p2 запрос выполняется, **When** p1 preempt'ит его, **Then** клиент получает HTTP 503 с сообщением "Request preempted by higher priority"
2. **Given** p2 запрос preempt'ится, **When** проверяем метрики, **Then** счётчик preemption увеличивается
3. **Given** запрос preempt'ится, **When** клиент повторяет запрос, **Then** он обрабатывается нормально

---

### User Story 4 - Concurrency Monitoring (Priority: P3)

Как оператор системы, я хочу видеть метрики по использованию слотов каждым приоритетом для мониторинга load balancing.

**Why this priority**: Метрики важны для operations, но не критичны для базовой функциональности.

**Independent Test**: Можно проверить что метрики доступны через /actuator/prometheus.

**Acceptance Scenarios**:

1. **Given** система работает, **When** проверяем метрики, **Then** видим current_usage по каждому приоритету
2. **Given** preemption произошёл, **When** проверяем метрики, **Then** счётчик preemption_events увеличен
3. **Given** p3 throttled, **When** проверяем метрики, **Then** видим p3_queue_length и p3_throttled_events

---

### Edge Cases

- Что происходит если p1 preempt'ит запрос который уже почти завершился? → Preemption происходит немедленно, запрос отменяется
- Что происходит при shutdown? → Active requests получают timeout, queued requests возвращаются с ошибкой
- Что если все слоты заняты p1? → Новые p1 встают в очередь, p2/p3 ждут
- Как работает с существующей timeout логикой? → Timeout проверяется для queued requests, preemption для running

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система ДОЛЖНА поддерживать конфигурируемый MAX_CONCURRENT (default=3)
- **FR-002**: Система ДОЛЖНА ограничивать p1 до P1_MAX_THREADS (default=2)
- **FR-003**: Система ДОЛЖНА ограничивать p3 до P3_MAX_THREADS (default=1)
- **FR-004**: Система ДОЛЖНА позволять p1 preempt'ировать p2/p3 requests
- **FR-005**: Система ДОЛЖНА возвращать HTTP 503 с понятным сообщением при preemption
- **FR-006**: Система ДОЛЖНА записывать метрики preemption events по приоритетам
- **FR-007**: Система НЕ ДОЛЖНА позволять p2/p3 preempt'ировать другие requests
- **FR-008**: Система ДОЛЖНА интегрироваться с существующей CoroutinePriorityQueue

### Key Entities

- **ConcurrencyConfig**: Конфигурация с maxConcurrent, p1MaxThreads, p3MaxThreads
- **PreemptionEvent**: Событие preemption с priority, preemptedBy, timestamp
- **ConcurrencySlot**: Слот выполнения с currentPriority, requestId, startTime

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: p1 запрос начинает выполняться в течение 100ms после arrival если есть capacity
- **SC-002**: p1 запрос начинает выполняться в течение 500ms после arrival даже при полной нагрузке (via preemption)
- **SC-003**: p3 throttling гарантирует max 1 concurrent execution
- **SC-004**: Metрики preemption доступны в Prometheus format
- **SC-005**: Все существующие тесты проходят после интеграции

## Assumptions

- Preemption выполняется через coroutine cancellation (cooperative)
- Preempted requests могут быть повторно отправлены клиентом
- Конфигурация загружается при startup (не hot-reload)
- Интеграция с существующей CoroutinePriorityQueue architecture
