# Implementation Plan: LLM Proxy

**Branch**: `1-llm-proxy` | **Date**: 2026-03-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/1-llm-proxy/spec.md`

## Summary

LLM Proxy — это прокси-сервер между клиентами и GigaChat API, предоставляющий:
- Очередь запросов с приоритетами для управления параллельной нагрузкой
- Кэширование ответов с TTL для сокращения вызовов API
- Метрики в формате Prometheus для наблюдаемости
- Переопределение модели для тестирования/миграции

Технический подход: Spring Boot приложение на Kotlin с использованием langchain4j-gigachat
для интеграции с GigaChat, Caffeine для кэширования, и корутин для асинхронной обработки.

## Technical Context

**Language/Version**: Kotlin 2.x / JVM 21
**Primary Dependencies**: Spring Boot 3.x, langchain4j-gigachat 0.1.17, Caffeine 3.x
**Storage**: SQLite (для кэша) / Caffeine (in-memory опция)
**Testing**: JUnit 5, Spring Test, Kotest assertions
**Target Platform**: Linux server (containerized)
**Project Type**: web-service (REST API)
**Performance Goals**: <50ms overhead, 100 concurrent queued requests
**Constraints**: Bounded parallelism per priority, TTL-based cache invalidation
**Scale/Scope**: Single instance, ~1000 req/min

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. DDD & Clean Architecture | ✅ PASS | Domain layer isolated from infrastructure, repository pattern for cache |
| II. Technology Stack Discipline | ✅ PASS | Kotlin, Spring Boot, langchain4j-gigachat, Caffeine - all compliant |
| III. Testing Standards | ✅ PASS | JUnit 5 + Spring Test + Kotest specified |
| IV. Concurrency Model | ✅ PASS | Coroutines with bounded parallelism per priority |
| V. Caching Strategy | ✅ PASS | Caffeine with TTL-based invalidation |

**Gate Result**: ✅ All gates passed

## Project Structure

### Documentation (this feature)

```text
specs/1-llm-proxy/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── chat-api.yaml    # OpenAPI spec for chat endpoints
│   └── metrics-api.yaml # OpenAPI spec for metrics/health
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── kotlin/ru/ddd/llmproxy/
│   │   ├── domain/                    # Domain layer (no dependencies)
│   │   │   ├── model/
│   │   │   │   ├── Priority.kt        # Priority value object
│   │   │   │   ├── QueueMetrics.kt    # Metrics entity
│   │   │   │   └── CacheKey.kt        # Cache key value object
│   │   │   ├── repository/
│   │   │   │   └── CacheRepository.kt # Cache repository interface
│   │   │   └── service/
│   │   │       └── QueueService.kt    # Queue domain service interface
│   │   │
│   │   ├── application/               # Application layer
│   │   │   ├── service/
│   │   │   │   ├── ChatApplicationService.kt
│   │   │   │   └── PriorityResolver.kt
│   │   │   └── port/
│   │   │       ├── ChatProviderPort.kt
│   │   │       └── MetricsPort.kt
│   │   │
│   │   ├── infrastructure/            # Infrastructure layer
│   │   │   ├── config/
│   │   │   │   ├── LlmProxyProperties.kt
│   │   │   │   └── CoroutineConfig.kt
│   │   │   ├── cache/
│   │   │   │   ├── CaffeineCacheRepository.kt
│   │   │   │   └── CacheKeyGenerator.kt
│   │   │   ├── queue/
│   │   │   │   └── CoroutinePriorityQueue.kt
│   │   │   ├── gigachat/
│   │   │   │   └── GigaChatProvider.kt
│   │   │   └── metrics/
│   │   │       └── PrometheusMetrics.kt
│   │   │
│   │   └── presentation/              # Presentation layer
│   │       ├── controller/
│   │       │   ├── ChatController.kt
│   │       │   ├── HealthController.kt
│   │       │   └── MetricsController.kt
│   │       ├── middleware/
│   │       │   └── RequestIdMiddleware.kt
│   │       └── exception/
│   │           └── GlobalExceptionHandler.kt
│   │
│   └── resources/
│       └── application.yml
│
└── test/
    └── kotlin/ru/ddd/llmproxy/
        ├── domain/
        ├── application/
        ├── infrastructure/
        └── presentation/
```

**Structure Decision**: Single project with Clean Architecture layers.
Domain layer has no external dependencies. Infrastructure implements interfaces defined in domain.

## Complexity Tracking

> No constitution violations detected. No complexity justification required.

## Dependencies

### Build Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.x")

    // langchain4j-gigachat
    implementation("chat.giga:langchain4j-gigachat:0.1.17")

    // Caffeine Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.x")

    // Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-assertions-core:5.x")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.x")
}
```

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| langchain4j-gigachat API changes | Pin version, integration tests |
| GigaChat rate limits | Bounded parallelism, queue overflow handling |
| Cache memory pressure | Caffeine eviction policy, max size config |
| Coroutine debugging complexity | Structured logging, coroutine name tracing |

## Next Steps

1. Run `/speckit.tasks` to generate implementation tasks
2. Implement domain layer first (no external dependencies)
3. Add infrastructure implementations
4. Wire everything in presentation layer
