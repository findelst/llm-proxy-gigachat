# Диаграммы LLM Proxy

Этот каталог содержит PlantUML диаграммы для визуализации архитектуры приоритизации и конкурентности.

## Файлы

- `priority-queue-diagrams.puml` - Полный набор диаграмм:
  - Обзор архитектуры
  - Детальная схема ConcurrencyController
  - Sequence diagram обработки запроса
  - Алгоритм прерывания (Preemption)
  - Retry механизм с backoff
  - Структура корутин
  - Состояние слотов

## Как просмотреть диаграммы

### VS Code
Установите расширение "PlantUML" и откройте .puml файл.

### Онлайн
1. Скопируйте содержимое файла
2. Откройте https://www.plantuml.com/plantuml/uml/
3. Вставьте код

### IntelliJ IDEA
Установите плагин "PlantUML integration".

## Генерация PNG/SVG

```bash
# Установка PlantUML (macOS)
brew install plantuml

# Генерация PNG
plantuml priority-queue-diagrams.puml

# Генерация SVG
plantuml -tsvg priority-queue-diagrams.puml
```

## Связанная документация

- [Приоритизация и конкурентность](../priority-and-concurrency.md) - полное описание алгоритмов
