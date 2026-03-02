# LLM Proxy

LLM Proxy — это прокси-сервер между клиентами и GigaChat API, предоставляющий:

- **Очередь запросов с приоритетами** для управления параллельной нагрузкой
- **Кэширование ответов** с TTL для сокращения вызовов API
- **Метрики в формате Prometheus** для наблюдаемости
- **Переопределение модели** для тестирования/миграции

## Технологии

- Kotlin 2.x / JVM 21
- Spring Boot 3.x
- langchain4j-gigachat 0.1.17
- Caffeine Cache
- Micrometer/Prometheus

## Быстрый старт

### Требования

- JDK 21+
- Gradle 8.x
- GigaChat API ключ авторизации

### Конфигурация

```bash
# GigaChat API
export LLM_API_KEY="your-auth-key-here"

# Опционально
export LLM_DEFAULT_MODEL_NAME="GigaChat"
export LOG_LEVEL="INFO"
```

### Сборка и запуск

```bash
# Сборка
./gradlew build

# Запуск
./gradlew bootRun
```

## API Endpoints

### Chat Completion

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-Priority: p1" \
  -d '{
    "messages": [
      {"role": "user", "content": "Привет, расскажи о Kotlin"}
    ]
  }'
```

### Chat Invoke (с метриками в теле)

```bash
curl -X POST http://localhost:8080/v1/chat/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Hello"}
    ]
  }'
```

### Health Check

```bash
curl http://localhost:8080/health
# {"status":"ok","env":"development"}
```

### Metrics

```bash
curl http://localhost:8080/metrics
# Prometheus format output
```

## Приоритеты

| Priority | Description | Use Case |
|----------|-------------|----------|
| p1 / highest | Максимальный приоритет | Критические запросы, SLA-bound |
| p2 | По умолчанию | Обычные запросы |
| p3 / lowest | Низший приоритет | Background, batch processing |

## Конфигурация

Основные параметры в `application.yml`:

```yaml
llm:
  proxy:
    api:
      base-url: ${LLM_API_BASE:https://gigachat.devices.sberbank.ru/v1}
      key: ${LLM_API_KEY}
    model:
      default-name: ${LLM_DEFAULT_MODEL_NAME:GigaChat}
    queue:
      priority-slots:
        p1: 2
        p2: 1
        p3: 1
      default-priority: p2
      max-length: 100
    cache:
      enabled: true
      ttl: 10m
      max-size: 1000
```

## Архитектура

Проект следует принципам Clean Architecture:

- **Domain Layer** - модели и интерфейсы без внешних зависимостей
- **Application Layer** - сервисы и порты для бизнес-логики
- **Infrastructure Layer** - реализации (GigaChat, Caffeine, Prometheus)
- **Presentation Layer** - REST контроллеры и middleware

## Метрики

Доступные метрики Prometheus:

| Метрика | Тип | Описание |
|---------|-----|----------|
| `llm_proxy_requests_total` | Counter | Счётчик запросов |
| `llm_proxy_queue_overflow_total` | Counter | Счётчик переполнений очереди |
| `llm_proxy_queue_wait_seconds` | Histogram | Время ожидания в очереди |
| `llm_proxy_provider_latency_seconds` | Histogram | Задержка провайдера |
| `llm_proxy_queue_length` | Gauge | Длина очереди |
| `llm_proxy_in_flight` | Gauge | Запросы в обработке |

## License

MIT
