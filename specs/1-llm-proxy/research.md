# Research: LLM Proxy

**Feature**: 1-llm-proxy
**Date**: 2026-03-02

## 1. langchain4j-gigachat Library

### Decision
Использовать библиотеку `chat.giga:langchain4j-gigachat:0.1.17` для интеграции с GigaChat API.

### Rationale
- Официальная библиотека от команды GigaChat (ai-forever)
- Предоставляет готовые модели: `GigaChatChatModel`, `GigaChatStreamingChatModel`, `GigaChatEmbeddingModel`
- Поддерживает аутентификацию через OAuth и API ключи
- Интеграция с LangChain4j ecosystem

### Key Classes

```kotlin
// Инициализация модели
GigaChatChatModel model = GigaChatChatModel.builder()
    .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
        .modelName(ModelName.GIGA_CHAT_PRO)
        .build())
    .authClient(AuthClient.builder()
        .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
            .scope(Scope.GIGACHAT_API_PERS)
            .authKey("<auth_key>")
            .build())
        .build())
    .logRequests(true)
    .logResponses(true)
    .build();
```

### Alternatives Considered
- **Прямой HTTP клиент**: Отклонено - требует больше кода, нет типобезопасности
- **Spring AI**: Отклонено - нет нативной поддержки GigaChat

### Integration Points
- Использовать `GigaChatChatModel.generate()` для синхронных запросов
- DTO из библиотеки использовать напрямую в контроллерах

---

## 2. Priority Queue with Coroutines

### Decision
Реализовать очередь приоритетов на Kotlin Coroutines с использованием `Channel` и `Semaphore`.

### Rationale
- Coroutines обеспечивают эффективную работу с I/O-bound операциями
- `Semaphore` позволяет ограничить параллелизм per-priority
- `Channel` обеспечивает FIFO порядок в рамках приоритета

### Implementation Pattern

```kotlin
class PrioritySlot(
    val name: String,
    val maxConcurrency: Int,
    private val semaphore: Semaphore = Semaphore(maxConcurrency)
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        return withPermit(semaphore) { block() }
    }
}
```

### Alternatives Considered
- **Java ExecutorService**: Отклонено - блокирующий API
- **Reactor/RxJava**: Отклонено - constitution требует coroutines
- **Ktor Client**: Рассматривается только для HTTP, не для очереди

---

## 3. Caffeine Cache Integration

### Decision
Использовать Caffeine cache с TTL и конфигурируемым максимальным размером.

### Rationale
- Высокая производительность (best-in-class benchmark)
- Автоматическая eviction политика
- Поддержка TTL и weak references
- Интеграция со Spring Boot через starter

### Configuration

```kotlin
val cache: Cache<CacheKey, ChatCompletionResponse> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()
    .build()
```

### Cache Key Generation
- SHA-256 hash от детерминированного JSON представления запроса
- JSON с отсортированными ключами для консистентности
- Включать `base_url` для различения провайдеров

### Alternatives Considered
- **Redis**: Отклонено - out of scope для single-instance
- **Ehcache**: Отклонено - Caffeine быстрее и проще

---

## 4. Metrics with Micrometer/Prometheus

### Decision
Использовать Micrometer с Prometheus registry для метрик.

### Rationale
- Spring Boot Actuator имеет нативную интеграцию
- Prometheus - стандарт для Kubernetes environments
- Micrometer абстрагирует разные backend'ы

### Metrics to Expose

| Metric | Type | Labels |
|--------|------|--------|
| `llm_proxy_requests_total` | Counter | endpoint, priority, status, error_code |
| `llm_proxy_queue_overflow_total` | Counter | priority |
| `llm_proxy_queue_wait_seconds` | Histogram | endpoint, priority |
| `llm_proxy_provider_latency_seconds` | Histogram | endpoint, priority |
| `llm_proxy_queue_length` | Gauge | priority |
| `llm_proxy_in_flight` | Gauge | priority |

### Buckets for Histograms
```
0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10
```

---

## 5. Request/Response Headers

### Decision
Использовать custom headers для передачи метрик в ответах.

### Headers

| Header | Description |
|--------|-------------|
| `x-llm-proxy-request-id` | UUID запроса |
| `x-llm-proxy-queue-wait-ms` | Время ожидания в очереди (ms) |
| `x-llm-proxy-provider-latency-ms` | Время ответа провайдера (ms) |
| `x-llm-proxy-priority` | Использованный приоритет |
| `x-request-id` | Echo из запроса или сгенерированный UUID |

### Middleware
- `RequestIdMiddleware` для генерации/echo `x-request-id`
- Добавляется ко всем ответам

---

## 6. Error Handling

### Decision
Унифицированный JSON формат для всех ошибок.

### Error Response Format

```json
{
  "error": {
    "code": "invalid_request",
    "http_status": 400,
    "message": "Human readable message",
    "request_id": "uuid"
  }
}
```

### Error Codes Mapping

| Code | HTTP Status | Scenario |
|------|-------------|----------|
| `invalid_request` | 400 | Bad JSON, missing fields, invalid priority |
| `queue_overflow` | 429 | Queue full |
| `provider_error` | 500/502 | GigaChat API error |
| `internal` | 500 | Unexpected error |

---

## 7. Configuration Properties

### Decision
Использовать Spring Boot configuration properties с prefix `llm.proxy`.

### Properties

```yaml
llm:
  proxy:
    api:
      base-url: https://gigachat.devices.sberbank.ru/v1
      key: ${LLM_API_KEY}
      timeout: 300s
    model:
      default-name: GigaChat
      override: ${LLM_MODEL_NAME:}
      temperature: 0.0
      top-p: 1.0
      max-tokens: 50000
    queue:
      priority-slots:
        p1: 2
        p2: 1
        p3: 1
      default-priority: p2
      max-length: 100
    cache:
      enabled: true
      ttl: 600000ms
      max-size: 1000
```

---

## 8. Kotlin + Spring Boot Best Practices

### Coroutines Configuration

```kotlin
@Configuration
class CoroutineConfig {
    @Bean
    fun dispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(10)
}
```

### Controller Pattern

```kotlin
@RestController
@RequestMapping("/v1/chat")
class ChatController(
    private val chatService: ChatApplicationService
) {
    @PostMapping("/completions")
    suspend fun completions(
        @RequestBody request: ChatCompletionRequest,
        @RequestHeader("X-Priority", required = false) priority: String?
    ): ResponseEntity<ChatCompletionResponse> {
        // Use langchain4j-gigachat types directly
    }
}
```

### Testing with Kotest

```kotlin
class ChatServiceTest : DescribeSpec({
    describe("ChatApplicationService") {
        it("should process request with correct priority") {
            // Kotest assertions
            result.priority shouldBe Priority.P1
        }
    }
})
```

---

## Summary

All technical decisions align with constitution principles:
- DDD & Clean Architecture: Domain isolated from infrastructure
- Technology Stack: Kotlin, Spring Boot, langchain4j-gigachat, Caffeine
- Testing: JUnit 5 + Kotest
- Concurrency: Coroutines with bounded parallelism
- Caching: Caffeine with TTL
