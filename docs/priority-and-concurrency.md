# Приоритизация и многопоточность в LLM Proxy

Документация описывает архитектуру и алгоритмы работы приоритизации запросов и управления конкурентностью в системе.

## Содержание

1. [Обзор архитектуры](#обзор-архитектуры)
2. [Уровни приоритета](#уровни-приоритета)
3. [Поток обработки запроса](#поток-обработки-запроса)
4. [Приоритетная очередь](#приоритетная-очередь)
5. [Контроллер конкурентности](#контроллер-конкурентности)
6. [Механизм прерывания (Preemption)](#механизм-прерывания-preemption)
7. [Retry механизм](#retry-механизм)
8. [Многопоточность и корутины](#многопоточность-и-корутины)

---

## Обзор архитектуры

```mermaid
graph TB
    subgraph "Входящие запросы"
        A[HTTP Request] --> B[ChatApplicationService]
    end

    subgraph "Приоритизация"
        B --> C[PriorityResolver]
        C --> D{Определение приоритета}
        D -->|X-Priority header| E[P1/P2/P3]
        D -->|body.priority| E
        D -->|default| E
    end

    subgraph "Очередь"
        E --> F[PriorityChannel]
        F --> G[PriorityBlockingQueue]
    end

    subgraph "Обработка"
        G --> H[CoroutinePriorityQueue]
        H --> I[ConcurrencyController]
        I --> J{Доступен слот?}
        J -->|Да| K[Выполнение запроса]
        J -->|Нет P1| L[Preemption]
        J -->|Нет P3| M[Throttle]
        K --> N[RetryExecutor]
        N --> O[Provider API]
    end

    L --> K
```

---

## Уровни приоритета

Система поддерживает три уровня приоритета:

```mermaid
graph LR
    subgraph "Приоритеты"
        P1["**P1** (Highest)<br/>level=1<br/>max threads: 2"]
        P2["**P2** (Normal)<br/>level=2<br/>max threads: unlimited"]
        P3["**P3** (Lowest)<br/>level=3<br/>max threads: 1"]
    end

    P1 --> |"может прервать"| P2
    P1 --> |"может прервать"| P3
```

### Конфигурация приоритетов

| Приоритет | Level | Max Threads | Может прерывать | Throttle |
|-----------|-------|-------------|-----------------|----------|
| P1 | 1 | 2 | P2, P3 | Нет |
| P2 | 2 | все доступные | - | Нет |
| P3 | 3 | 1 | - | Да |

### Разрешение приоритета

```kotlin
// Источники приоритета (в порядке убывания приоритета):
// 1. Header X-Priority: "p1" | "p2" | "p3" | "highest" | "lowest"
// 2. Body field: { "priority": "p1" }
// 3. Default: P2

fun resolve(headerPriority: String?, bodyPriority: String?): Priority {
    return Priority.resolve(headerPriority)
        ?: Priority.resolve(bodyPriority)
        ?: config.defaultPriority
}
```

---

## Поток обработки запроса

```mermaid
sequenceDiagram
    participant C as Client
    participant S as ChatApplicationService
    participant R as PriorityResolver
    participant Q as PriorityChannel
    participant P as CoroutinePriorityQueue
    participant CC as ConcurrencyController
    participant E as RetryExecutor
    participant API as Provider API

    C->>S: POST /chat/completions
    S->>R: resolvePriority(header, body)
    R-->>S: Priority.P2

    S->>Q: trySend(request, priority, seqNum)

    alt Queue Full
        Q-->>S: Failure(Overflow)
        S-->>C: 429 Queue Overflow
    else Success
        Q-->>S: Success
        Q->>P: receive()

        P->>CC: tryAcquire(requestId, priority, job)

        alt Slot Available
            CC-->>P: true
        else No Slot P1
            CC->>CC: preemptSlots(P1, 1)
            CC-->>P: true (after preemption)
        else No Slot P3
            CC-->>P: false
            P-->>C: 429 P3 Throttled
        end

        P->>E: executeWithRetry(operation, priority)

        loop Retry attempts
            E->>API: API Call
            alt Success
                API-->>E: Response
            else Retryable Error (429, 5xx)
                E->>E: delay(backoff + jitter)
            else Non-retryable Error
                API-->>E: Error
            end
        end

        E-->>P: Result
        P->>CC: release(requestId)
        P-->>S: Result
        S-->>C: Response
    end
```

---

## Приоритетная очередь

### Архитектура PriorityChannel

```mermaid
classDiagram
    class PriorityChannel~T~ {
        -PriorityBlockingQueue~PrioritizedItem~ queue
        -Mutex mutex
        -Channel signalChannel
        +trySend(item, priority, seqNum) ChannelResult
        +receive() PrioritizedItem
        +removeExpired(maxAge, getAge, onExpired) Int
    }

    class PrioritizedItem~T~ {
        +T item
        +Int priority
        +Long sequenceNumber
        +compareTo(other) Int
    }

    class CoroutinePriorityQueue~T, R~ {
        -PriorityChannel queue
        -AtomicLong sequenceCounter
        -ConcurrencyController controller
        +enqueue(request) R
        +startProcessing(processor)
        -processRequest(request, processor)
    }

    PriorityChannel --> PrioritizedItem
    CoroutinePriorityQueue --> PriorityChannel
```

### Алгоритм упорядочивания

Элементы сортируются по двум критериям:

1. **Priority level** (по возрастанию) - меньшее значение = выше приоритет
2. **Sequence number** (по возрастанию) - FIFO внутри одного приоритета

```mermaid
graph TB
    subgraph "PriorityBlockingQueue"
        direction TB
        A["P1, seq=1"] --> B["P1, seq=5"]
        B --> C["P2, seq=2"]
        C --> D["P2, seq=8"]
        D --> E["P3, seq=3"]
        E --> F["P3, seq=7"]
    end

    style A fill:#ff6b6b
    style B fill:#ff6b6b
    style C fill:#ffd93d
    style D fill:#ffd93d
    style E fill:#6bcb77
    style F fill:#6bcb77
```

### Удаление просроченных запросов

```mermaid
flowchart TD
    A[Start Timeout Cleanup] --> B{isShutdown?}
    B -->|No| C[delay checkInterval]
    C --> D[cleanupExpiredRequests]
    D --> E[Iterate queue]
    E --> F{Item age > maxAge?}
    F -->|Yes| G[Remove from queue]
    G --> H[completeExceptionally QueueTimeoutError]
    H --> I[recordQueueTimeout metric]
    I --> E
    F -->|No| E
    E -->|Done| B
    B -->|Yes| J[End]
```

---

## Контроллер конкурентности

### Архитектура ConcurrencyController

```mermaid
classDiagram
    class ConcurrencyController {
        -Semaphore globalSemaphore
        -AtomicInteger p1Counter
        -AtomicInteger p2Counter
        -AtomicInteger p3Counter
        -List~ExecutionSlot~ slots
        -ConcurrentHashMap runningRequests
        +tryAcquire(requestId, priority, job) Boolean
        +release(requestId)
        +preemptSlots(forPriority, slotsNeeded) List
        +stats ConcurrencyStats
    }

    class ExecutionSlot {
        +Int slotId
        -RunningRequest currentRequest
        +allocate(request)
        +release()
        +canBePreemptedBy(priority, config) Boolean
    }

    class RunningRequest {
        +String requestId
        +Priority priority
        +Job job
        +Instant startTime
        +Instant queuedAt
        +cancel(reason)
    }

    ConcurrencyController --> ExecutionSlot
    ExecutionSlot --> RunningRequest
```

### Логика выделения слота

```mermaid
flowchart TD
    A[tryAcquire] --> B{canAcceptPriority?}
    B -->|P1: counter < 2| C{globalSemaphore.tryAcquire?}
    B -->|P2: always true| C
    B -->|P3: counter < 1| C

    B -->|Limit reached| Z[return false]

    C -->|Success| D[synchronized slots]
    C -->|Fail| Z

    D --> E[Find available slot]
    E --> F{Slot found?}
    F -->|No| G[release semaphore]
    G --> Z
    F -->|Yes| H[Create RunningRequest]
    H --> I[Allocate slot]
    I --> J[incrementCounter]
    J --> K[updateMetrics]
    K --> L[return true]
```

### Состояние слотов

```mermaid
graph TB
    subgraph "Execution Slots (maxConcurrent=3)"
        S1["Slot 1<br/>P1 request<br/>running: 500ms"]
        S2["Slot 2<br/>P2 request<br/>running: 200ms"]
        S3["Slot 3<br/>P3 request<br/>running: 100ms"]
    end

    subgraph "Counters"
        C1["p1Counter = 1"]
        C2["p2Counter = 1"]
        C3["p3Counter = 1"]
    end

    subgraph "Semaphore"
        SEM["globalSemaphore<br/>available: 0 / 3"]
    end

    S1 -.-> C1
    S2 -.-> C2
    S3 -.-> C3
```

---

## Механизм прерывания (Preemption)

### Условия для прерывания

```mermaid
flowchart TD
    A[P1 request needs slot] --> B{All slots occupied?}
    B -->|No| C[Normal acquisition]
    B -->|Yes| D{preemptionEnabled?}
    D -->|No| E[Return false]
    D -->|Yes| F[Find preemptable slots]

    F --> G[Sort by:<br/>1. Priority - P3 first<br/>2. Start time - oldest first]

    G --> H[Select slots to preempt]
    H --> I[Cancel coroutine job]
    I --> J[Record preemption metric]
    J --> K[Return preempted IDs]
```

### Приоритеты для прерывания

```mermaid
flowchart TB
    subgraph rules["Preemption Rules"]
        direction LR
        P1["P1 (Highest)"] -->|"can preempt"| P2a["P2"]
        P1 -->|"can preempt"| P3a["P3"]
    end

    subgraph cannot["Cannot Preempt"]
        direction LR
        P2["P2"] -.->|"cannot preempt"| X1["P2 cannot preempt"]
        P3["P3"] -.->|"cannot preempt"| X2["P3 cannot preempt"]
    end

    style P1 fill:#ff6b6b
    style P2 fill:#ffd93d
    style P3 fill:#6bcb77
    style P2a fill:#ffd93d
    style P3a fill:#6bcb77
    style X1 fill:#ccc
    style X2 fill:#ccc
```

### Алгоритм выбора жертвы для прерывания

```kotlin
fun preemptSlots(forPriority: Priority, slotsNeeded: Int): List<String> {
    val preemptable = slots
        .filter { it.canBePreemptedBy(forPriority, config) }
        .sortedWith(compareBy(
            // Higher level (P3) first - меньший приоритет прерывается первым
            { -(it.currentRequest?.priority?.level ?: Int.MIN_VALUE) },
            // Older requests first
            { it.currentRequest?.startTime ?: Instant.MAX }
        ))
        .take(slotsNeeded)

    for (slot in preemptable) {
        request.job.cancel(CancellationException("Preempted by ${forPriority.value}"))
        metricsPort.recordPreemption(request.priority, forPriority)
    }

    return preemptedIds
}
```

### Пример прерывания

```mermaid
sequenceDiagram
    participant P1 as P1 Request
    participant CC as ConcurrencyController
    participant P3 as P3 Request (running)
    participant Job as P3 Coroutine Job

    Note over CC: All 3 slots occupied<br/>P1=1, P2=1, P3=1

    P1->>CC: tryAcquire(id, P1, job)
    CC->>CC: p1Counter(1) < p1Max(2) ✓
    CC->>CC: globalSemaphore.tryAcquire() ✗

    Note over CC: Trigger preemption

    CC->>CC: preemptSlots(P1, 1)
    CC->>CC: Find P3 slot (lowest priority)
    CC->>Job: cancel("Preempted by p1")
    Job-->>P3: CancellationException
    P3->>CC: release(requestId)

    CC->>CC: globalSemaphore.release()
    CC-->>P1: tryAcquire returns true
```

---

## Retry механизм

### Конфигурация Retry

```kotlin
data class RetryConfig(
    val enabled: Boolean = true,
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0,
    val retryableStatusCodes: Set<Int> = setOf(429, 500, 502, 503, 504)
)
```

### Алгоритм Retry с Exponential Backoff

```mermaid
flowchart TD
    A[executeWithRetry] --> B{config.enabled?}
    B -->|No| C[Execute operation once]
    B -->|Yes| D[attempt = 0]

    D --> E[Execute operation]
    E --> F{Success?}
    F -->|Yes| G[Return result]

    F -->|No| H{ProviderError?}
    H -->|No| I[Throw exception]

    H -->|Yes| J{isRetryable status?}
    J -->|No| I

    J -->|Yes| K{attempt < maxAttempts - 1?}
    K -->|No| L[Throw last error]

    K -->|Yes| M[Calculate delay with jitter]
    M --> N[delay delayMs]
    N --> O[currentDelay *= backoffMultiplier]
    O --> P[attempt++]
    P --> E

    style M fill:#ffd93d
    style N fill:#6bcb77
```

### Формула задержки с Jitter

```mermaid
graph LR
    subgraph "Delay Calculation"
        A["baseDelay"] --> B["jitter = random(-20%, +20%)"]
        B --> C["delay = baseDelay + jitter"]
        C --> D["delay = max(0, delay)"]
    end
```

```kotlin
private fun calculateDelay(baseDelay: Long): Long {
    // Add jitter: +/- 20% of base delay
    val jitterRange = (baseDelay * 0.2).toLong()
    val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
    return (baseDelay + jitter).coerceAtLeast(0)
}
```

### Пример retry последовательности

```mermaid
gantt
    title Retry Sequence (maxAttempts=3, initialDelay=1000ms, multiplier=2.0)
    dateFormat X
    axisFormat %s ms

    section Request
    Attempt 1           :a1, 0, 1s
    Wait (1000±200ms)   :w1, after a1, 1s
    Attempt 2           :a2, after w1, 1s
    Wait (2000±400ms)   :w2, after a2, 2s
    Attempt 3           :a3, after w2, 1s
```

---

## Многопоточность и корутины

### Архитектура корутин

```mermaid
graph TB
    subgraph "Spring Application"
        APP[Spring Boot App]
    end

    subgraph "CoroutineConfig"
        IO[ioDispatcher<br/>Dispatchers.IO.limitedParallelism 3]
        DEF[defaultDispatcher<br/>Dispatchers.Default]
    end

    subgraph "CoroutinePriorityQueue"
        SCOPE[CoroutineScope<br/>SupervisorJob]
        CONSUMER[Consumer Coroutine<br/>receives from PriorityChannel]
        PROCESSOR[Processor Coroutines<br/>launch per request]
        CLEANUP[Cleanup Coroutine<br/>periodic timeout check]
    end

    subgraph "ConcurrencyController"
        SEM[Semaphore<br/>maxConcurrent permits]
        SLOTS[Execution Slots<br/>synchronized access]
    end

    APP --> IO
    APP --> DEF
    SCOPE --> IO
    CONSUMER --> SCOPE
    PROCESSOR --> SCOPE
    CLEANUP --> SCOPE
    PROCESSOR --> SEM
    PROCESSOR --> SLOTS
```

### Dispatcher конфигурация

```kotlin
@Configuration
class CoroutineConfig {
    // IO dispatcher для блокирующих операций (API calls)
    @Bean
    fun ioDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(3)

    // Default dispatcher для CPU-bound операций
    @Bean
    fun defaultDispatcher(): CoroutineDispatcher =
        Dispatchers.Default
}
```

### Обработка запроса в корутине

```mermaid
sequenceDiagram
    participant Main as Main Consumer
    participant Scope as CoroutineScope
    participant Job as Request Job
    participant CC as ConcurrencyController
    participant API as Provider API

    Main->>Scope: launch { processRequest() }

    Note over Scope: Consumer coroutine continues<br/>to receive next item

    Scope->>CC: tryAcquire(requestId, priority, job)

    alt Slot acquired
        CC-->>Scope: true
        Scope->>API: processor(payload)

        alt Success
            API-->>Scope: Result
            Scope->>CC: release(requestId)
        else Preempted
            Note over Job: job.cancel() called
            Job-->>Scope: CancellationException
            Scope->>Scope: completeExceptionally(PreemptionError)
        end
    else No slot
        CC-->>Scope: false
        Scope->>Scope: Handle based on priority
    end
```

### Потокобезопасность

| Компонент | Механизм синхронизации | Обоснование |
|-----------|------------------------|-------------|
| PriorityChannel | Mutex (kotlinx.coroutines.sync) | Корутино-безопасная блокировка |
| ConcurrencyController.slots | synchronized(slots) | Атомарное выделение слота |
| ConcurrencyController.runningRequests | ConcurrentHashMap | Потокобезопасный доступ |
| Per-priority counters | AtomicInteger | Атомарные счётчики |
| Global semaphore | Semaphore (java.util.concurrent) | Глобальный лимит конкурентности |

### Структура корутин

```mermaid
graph TB
    subgraph "Coroutine Hierarchy"
        ROOT[SupervisorJob]

        ROOT --> CONSUMER[Consumer Job]
        ROOT --> CLEANUP[Cleanup Job]

        CONSUMER --> REQ1[Request Job 1]
        CONSUMER --> REQ2[Request Job 2]
        CONSUMER --> REQ3[Request Job 3]

        REQ1 --> P1A[P1 Processor]
        REQ2 --> P2A[P2 Processor]
        REQ3 --> P3A[P3 Processor]
    end

    subgraph "Lifecycle"
        direction LR
        START[Application Start] --> RUN[Processing]
        RUN --> SHUTDOWN[PreDestroy]
        SHUTDOWN --> CANCEL[scope.cancel]
    end
```

---

## Ключевые файлы

| Файл | Назначение |
|------|------------|
| `domain/model/Priority.kt` | Enum с уровнями приоритета |
| `domain/model/ConcurrencyConfig.kt` | Конфигурация лимитов |
| `infrastructure/queue/PriorityChannel.kt` | Приоритетный канал для корутин |
| `infrastructure/queue/CoroutinePriorityQueue.kt` | Основная реализация очереди |
| `infrastructure/queue/ConcurrencyController.kt` | Управление слотами и прерываниями |
| `infrastructure/queue/RetryExecutor.kt` | Retry с exponential backoff |
| `infrastructure/config/CoroutineConfig.kt` | Конфигурация диспетчеров |

---

## Конфигурация (application.yml)

```yaml
llm:
  proxy:
    queue:
      max-length: 100
      max-concurrent: 3
      p1-max-threads: 2
      p3-max-threads: 1
      preemption-enabled: true
      timeout-minutes: 5
      timeout-check-interval-ms: 60000
    retry:
      enabled: true
      max-attempts: 3
      initial-delay-ms: 1000
      max-delay-ms: 10000
      backoff-multiplier: 2.0
      retryable-status-codes:
        - 429
        - 500
        - 502
        - 503
        - 504
```

---

## Тестирование

Проект содержит комплексный набор тестов для проверки корректности работы приоритизации и конкурентности.

### Структура тестов

```mermaid
graph TB
    subgraph "Unit Tests"
        UT1[PriorityChannelTest]
        UT2[ConcurrencyControllerTest]
        UT3[QueueTimeoutTest]
        UT4[RetryExecutorTest]
    end

    subgraph "Integration Tests"
        IT1[PriorityQueueConcurrencyTest]
    end

    UT1 --> UT1_desc["Priority ordering<br/>FIFO within priority<br/>Capacity limits"]
    UT2 --> UT2_desc["Slot acquisition<br/>Preemption logic<br/>Stats reporting"]
    UT3 --> UT3_desc["Timeout cleanup<br/>Expired request handling"]
    UT4 --> UT4_desc["Retry with backoff<br/>Retryable status codes"]
    IT1 --> IT1_desc["End-to-end priority<br/>Concurrent load<br/>Preemption scenarios"]

    style UT1 fill:#e3f2ff
    style UT2 fill:#e3f2ff
    style UT3 fill:#e3f2ff
    style UT4 fill:#e3f2ff
    style IT1 fill:#bbdefb
```

### Unit Tests

#### PriorityChannelTest

Файл: `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/PriorityChannelTest.kt`

**Проверяет:**
- Уорядочивание по приоритету (lower value = higher priority)
- FIFO порядок внутри одного приоритета
- Ограничение capacity
- Обработка overflow
- Корректность состояния empty/full

```kotlin
@Test
fun `should receive items in priority order`() = runTest {
    channel.trySend("low", priority = 3, sequenceNumber = 1)
    channel.trySend("high", priority = 1, sequenceNumber = 2)
    channel.trySend("medium", priority = 2, sequenceNumber = 3)

    assertEquals("high", channel.receive().item)   // P1 first
    assertEquals("medium", channel.receive().item) // P2 second
    assertEquals("low", channel.receive().item)   // P3 last
}
```

#### ConcurrencyControllerTest

Файл: `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/ConcurrencyControllerTest.kt`

**Проверяет:**
- Выделение слотов с per-priority лимитами
- Освобождение слотов и обновление счётчиков
- Логика прерывания для P1 приоритета
- Отчётность статистики

```kotlin
@Test
fun `should reject P3 when max 1 reached`() = runTest {
    // Given - one P3 already running
    controller.tryAcquire("p3-1", Priority.P3, Job(), Instant.now())

    // When - try second P3
    val result = controller.tryAcquire("p3-2", Priority.P3, Job(), Instant.now())

    // Then - should be rejected (max 1 P3 concurrent)
    assertFalse(result)
}

@Test
fun `should preempt P3 before P2`() = runTest {
    controller.tryAcquire("p2-1", Priority.P2, Job(), Instant.now())
    controller.tryAcquire("p3-1", Priority.P3, Job(), Instant.now())

    // When P1 needs slot - P3 should be preempted first
    val preempted = controller.preemptSlots(Priority.P1, 1)

    assertEquals("p3-1", preempted.first())
}
```

#### QueueTimeoutTest

Файл: `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/QueueTimeoutTest.kt`

**Проверяет:**
- Удаление просроченных запросов из очереди
- Генерация QueueTimeoutError для timed out запросов
- Запись метрик timeout
- Отключение timeout (timeoutMinutes=0)

```kotlin
@Test
fun `should remove expired items from queue`() = runTest {
    val oldRequest = createRequest("old", Priority.P3, Instant.now().minusMillis(11 * 60 * 1000))
    val newRequest = createRequest("new", Priority.P3, Instant.now())

    channel.trySend(oldRequest, Priority.P3.level, 1)
    channel.trySend(newRequest, Priority.P3.level, 2)

    val removedCount = channel.removeExpired(
        maxAgeMs = 10 * 60 * 1000L,
        getAgeMs = { Instant.now().toEpochMilli() - it.metrics.queuedAt.toEpochMilli() },
        onExpired = { }
    )

    assertEquals(1, removedCount)
    assertEquals("new", channel.receive().item.payload)
}
```

#### RetryExecutorTest

Файл: `src/test/kotlin/ru/ddd/llmproxy/unit/infrastructure/queue/RetryExecutorTest.kt`

**Проверяет:**
- Retry логика с exponential backoff
- Обработка non-retryable ошибок
- Лимит max attempts
- Поведение при отключенном retry

```kotlin
@Test
fun `should retry and succeed on second attempt`() = runTest {
    var callCount = 0

    val result = retryExecutor.executeWithRetry(
        operation = {
            callCount++
            if (callCount == 1) {
                throw ProviderError("Temporary error", providerStatus = 500)
            }
            "success"
        },
        priority = Priority.P1
    )

    assertEquals("success", result)
    assertEquals(2, callCount)
}

@Test
fun `should not retry non-retryable status codes`() = runTest {
    var callCount = 0

    try {
        retryExecutor.executeWithRetry<String>(
            operation = {
                callCount++
                throw ProviderError("Bad request", providerStatus = 400)
            },
            priority = Priority.P1
        )
    } catch (e: ProviderError) {
        assertEquals(1, callCount) // No retries for 400
    }
}
```

### Integration Tests

#### PriorityQueueConcurrencyTest

Файл: `src/test/kotlin/ru/ddd/llmproxy/integration/PriorityQueueConcurrencyTest.kt`

**Проверяет:**
- End-to-end обработка запросов с приоритизацией
- Обработка смешанной нагрузки разных приоритетов
- Сценарии прерывания (preemption)
- P3 throttling (ограничение до 1 concurrent)
- Обработка queue overflow

```kotlin
@Test
fun `should process higher priority requests first`() = runTest {
    val processingOrder = mutableListOf<String>()

    // Submit requests in random order
    val requests = listOf(
        "low-1" to Priority.P3,
        "low-2" to Priority.P3,
        "high-1" to Priority.P1,
        "high-2" to Priority.P1,
        "medium-1" to Priority.P2
    )

    // Process and collect order
    // Verify: P1 requests processed first, then P2, then P3
}

@Test
fun `should preempt P2 requests when P1 arrives and slots full`() = runTest {
    // Fill all slots with P2 requests
    // Submit P1 request - should preempt P2
    // Verify P1 is processed despite full slots
}

@Test
fun `should limit P3 to max 1 concurrent`() = runTest {
    // Submit multiple P3 requests
    // Verify only 1 P3 running at a time
    // Others should be throttled with P3ThrottledError
}
```

### Запуск тестов

```bash
# Запуск всех тестов
./gradlew test

# Запуск конкретного тестового класса
./gradlew test --tests "PriorityChannelTest"

# Запуск интеграционных тестов
./gradlew test --tests "PriorityQueueConcurrencyTest"

# Запуск с детальным выводом
./gradlew test --info
```

### Метрики тестового покрытия

| Компонент | Unit Tests | Integration Tests | Coverage |
|-----------|------------|-------------------|---------|
| PriorityChannel | 14 | - | Ordering, capacity, concurrency |
| ConcurrencyController | 12 | - | Acquisition, release, preemption |
| QueueTimeout | 10 | - | Expiration, cleanup |
| RetryExecutor | 15 | - | Backoff, retryable codes |
| Full System | - | 6 | End-to-end scenarios |
