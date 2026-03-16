package ru.ddd.llmproxy.infrastructure.queue

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.MetricsPort
import ru.ddd.llmproxy.domain.model.*
import ru.ddd.llmproxy.domain.service.QueueService
import ru.ddd.llmproxy.domain.service.QueueOverflowException
import ru.ddd.llmproxy.infrastructure.config.QueueConfig
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

private val log = KotlinLogging.logger {}

/**
 * Простая приоритетная очередь с контролем параллелизма.
 *
 * - Одна очередь с приоритетом (PriorityBlockingQueue)
 * - Лимит потоков на приоритет (P1 max 2, P3 max 1)
 * - FIFO внутри одного приоритета
 */
@Component
class PriorityQueue<T : Any, R : Any>(
    private val config: QueueConfig,
    private val metricsPort: MetricsPort,
    @Qualifier("ioDispatcher") private val dispatcher: CoroutineDispatcher
) : QueueService<T, R> {

    // === Очередь и счётчики ===
    private val queue = PriorityBlockingQueue<Item<QueuedRequest<T, R>>>()
    private val sequenceCounter = AtomicLong(0)

    // Счётчики активных запросов по приоритетам
    private val p1Active = AtomicInteger(0)
    private val p2Active = AtomicInteger(0)
    private val p3Active = AtomicInteger(0)

    // Запросы в обработке (для прерывания)
    private val runningJobs = ConcurrentHashMap<String, Job>()

    // Сигнал для ожидания новых элементов
    private val signal = Channel<Unit>(Channel.UNLIMITED)

    // Coroutine scope
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Мониторинг
    private val _stats = MutableStateFlow(QueueStats())
    val stats: StateFlow<QueueStats> = _stats.asStateFlow()

    @Volatile
    private var isShutdown = false

    init {
        log.info {
            "PriorityQueue: maxLength=${config.maxLength}, maxConcurrent=${config.maxConcurrent}, " +
                    "p1Max=${config.p1MaxThreads}, p3Max=${config.p3MaxThreads}"
        }
    }

    // === QueueService ===

    override suspend fun enqueue(request: QueuedRequest<T, R>): R {
        if (isShutdown) throw QueueOverflowException("Queue is shutting down")
        if (queue.size >= config.maxLength) {
            metricsPort.recordQueueOverflow(request.priority)
            throw QueueOverflowError(priority = request.priority)
        }

        val seq = sequenceCounter.getAndIncrement()
        queue.offer(Item(request, request.priority.level, seq))
        signal.trySend(Unit)

        updateStats()
        log.debug { "Enqueued ${request.id} [${request.priority.value}, seq=$seq]" }

        return request.deferred.await()
    }

    override suspend fun tryEnqueue(request: QueuedRequest<T, R>): Result<R> = try {
        Result.success(enqueue(request))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun queueLength(priority: Priority): Int = queue.count { it.request.priority == priority }

    override fun totalQueueLength(): Int = queue.size

    override fun inFlight(priority: Priority): Int = when (priority) {
        Priority.P1 -> p1Active.get()
        Priority.P2 -> p2Active.get()
        Priority.P3 -> p3Active.get()
    }

    override fun hasCapacity(): Boolean = queue.size < config.maxLength

    override fun maxCapacity(): Int = config.maxLength

    override suspend fun shutdown() {
        isShutdown = true
        signal.close()
    }

    // === Обработка ===

    /**
     * Запускает обработку очереди.
     * Читает элементы по приоритету, учитывая лимиты потоков.
     */
    fun startProcessing(processor: suspend (T) -> R) {
        scope.launch {
            log.info { "Started queue processing" }
            while (!isShutdown) {
                try {
                    // Ждём сигнал или таймаут
                    signal.tryReceive().getOrNull()

                    // Сначала отклоняем P3 сверх лимита
                    throttleExcessP3()

                    // Пытаемся найти следующий запрос по приоритету
                    val item = findNextItem()
                    if (item == null) {
                        delay(10)
                        continue
                    }

                    launch {
                        processItem(item.request, processor)
                    }
                } catch (e: Exception) {
                    if (!isShutdown) log.error(e) { "Processing error" }
                }
            }
        }
    }

    /**
     * Отклоняет P3 запросы, если лимит P3 уже достигнут.
     * Это обеспечивает немедленный throttling вместо ожидания в очереди.
     */
    private fun throttleExcessP3() {
        if (p3Active.get() >= config.p3MaxThreads) {
            // P3 лимит достигнут - отклоняем все P3 в очереди
            val p3Items = queue.toList().filter { it.request.priority == Priority.P3 }
            for (item in p3Items) {
                queue.remove(item)
                metricsPort.recordP3Throttled()
                item.request.completeExceptionally(
                    P3ThrottledError(
                        queuePosition = 0,
                        estimatedWaitSeconds = 30
                    )
                )
            }
            if (p3Items.isNotEmpty()) {
                updateStats()
            }
        }
    }

    /**
     * Находит следующий запрос по приоритету и сразу резервирует слот.
     * Учитывает глобальный лимит и лимиты P1/P3.
     */
    private fun findNextItem(): Item<QueuedRequest<T, R>>? {
        val total = p1Active.get() + p2Active.get() + p3Active.get()
        if (total >= config.maxConcurrent) return null

        val items = queue.toList().sorted()
        for (item in items) {
            when (item.request.priority) {
                Priority.P1 -> if (p1Active.get() < config.p1MaxThreads) {
                    queue.remove(item)
                    incrementActive(Priority.P1) // Резервируем слот СРАЗУ
                    return item
                }
                Priority.P2 -> {
                    queue.remove(item)
                    incrementActive(Priority.P2) // Резервируем слот СРАЗУ
                    return item
                }
                Priority.P3 -> if (p3Active.get() < config.p3MaxThreads) {
                    queue.remove(item)
                    incrementActive(Priority.P3) // Резервируем слот СРАЗУ
                    return item
                }
            }
        }
        return null
    }

    /**
     * Обрабатывает один запрос.
     */
    private suspend fun processItem(
        request: QueuedRequest<T, R>,
        processor: suspend (T) -> R
    ) {
        val priority = request.priority
        // Слот уже занят в findNextItem()

        updateStats()

        val job = currentCoroutineContext().job
        runningJobs[request.id] = job

        try {
            request.startProcessing()
            log.debug { "Processing ${request.id} [${priority.value}]" }

            val result = executeWithRetry(request, processor)
            request.completeProcessing()
            request.complete(result)

        } catch (e: CancellationException) {
            log.info { "Request ${request.id} preempted" }
            request.completeExceptionally(
                PreemptionError(
                    priority = priority,
                    preemptedBy = Priority.P1,
                    elapsedMs = Duration.between(request.startedAt ?: Instant.now(), Instant.now()).toMillis()
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Error processing ${request.id}" }
            request.completeExceptionally(e)
        } finally {
            runningJobs.remove(request.id)
            decrementActive(priority)
            updateStats()
        }
    }

    /**
     * Выполняет запрос с retry.
     */
    private suspend fun executeWithRetry(
        request: QueuedRequest<T, R>,
        processor: suspend (T) -> R
    ): R {
        if (!config.retryEnabled) {
            return processor(request.payload)
        }

        var delay = config.retryInitialDelayMs
        repeat(config.retryMaxAttempts) { attempt ->
            try {
                return processor(request.payload)
            } catch (e: ProviderError) {
                if (!config.isRetryable(e.providerStatus)) throw e
                if (attempt == config.retryMaxAttempts - 1) throw e

                request.recordRetry()
                metricsPort.recordRetryAttempt(request.priority, attempt + 1)

                val jitter = Random.nextLong(-delay / 5, delay / 5 + 1)
                delay((delay + jitter).coerceAtLeast(0))
                delay = min((delay * config.retryBackoffMultiplier).toLong(), config.retryMaxDelayMs)
            }
        }
        throw IllegalStateException("Should not reach here")
    }

    // === Вспомогательные методы ===

    private fun incrementActive(priority: Priority) = when (priority) {
        Priority.P1 -> p1Active.incrementAndGet()
        Priority.P2 -> p2Active.incrementAndGet()
        Priority.P3 -> p3Active.incrementAndGet()
    }

    private fun decrementActive(priority: Priority) = when (priority) {
        Priority.P1 -> p1Active.decrementAndGet()
        Priority.P2 -> p2Active.decrementAndGet()
        Priority.P3 -> p3Active.decrementAndGet()
    }

    private fun updateStats() {
        val s = QueueStats(
            queueSize = queue.size,
            p1Queue = queue.count { it.request.priority == Priority.P1 },
            p2Queue = queue.count { it.request.priority == Priority.P2 },
            p3Queue = queue.count { it.request.priority == Priority.P3 },
            p1Active = p1Active.get(),
            p2Active = p2Active.get(),
            p3Active = p3Active.get(),
            availableSlots = config.maxConcurrent - p1Active.get() - p2Active.get() - p3Active.get()
        )
        _stats.value = s

        // Обновляем метрики
        Priority.entries.forEach { p ->
            metricsPort.setQueueLength(p, when (p) {
                Priority.P1 -> s.p1Queue
                Priority.P2 -> s.p2Queue
                Priority.P3 -> s.p3Queue
            })
            metricsPort.setInFlight(p, when (p) {
                Priority.P1 -> s.p1Active
                Priority.P2 -> s.p2Active
                Priority.P3 -> s.p3Active
            })
            metricsPort.setConcurrentRequests(p, when (p) {
                Priority.P1 -> s.p1Active
                Priority.P2 -> s.p2Active
                Priority.P3 -> s.p3Active
            })
        }
        metricsPort.setAvailableSlots(s.availableSlots)
    }

    @PreDestroy
    fun destroy() {
        isShutdown = true
        signal.close()
        scope.cancel()
        log.info { "PriorityQueue destroyed" }
    }

    // === Вложенные классы ===

    /** Элемент очереди с приоритетом и порядковым номером для FIFO */
    data class Item<T>(
        val request: T,
        val priorityLevel: Int,
        val sequence: Long
    ) : Comparable<Item<T>> {
        override fun compareTo(other: Item<T>): Int {
            val cmp = priorityLevel.compareTo(other.priorityLevel)
            return if (cmp != 0) cmp else sequence.compareTo(other.sequence)
        }
    }

    /** Статистика очереди */
    data class QueueStats(
        val queueSize: Int = 0,
        val p1Queue: Int = 0,
        val p2Queue: Int = 0,
        val p3Queue: Int = 0,
        val p1Active: Int = 0,
        val p2Active: Int = 0,
        val p3Active: Int = 0,
        val availableSlots: Int = 0
    )
}
