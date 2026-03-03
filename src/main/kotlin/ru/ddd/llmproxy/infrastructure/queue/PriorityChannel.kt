package ru.ddd.llmproxy.infrastructure.queue

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue

/**
 * A suspendable priority channel that maintains strict priority ordering
 * while supporting coroutine-based operations.
 *
 * Features:
 * - Priority-based ordering (lower priority value = higher priority)
 * - FIFO ordering within same priority level
 * - Bounded capacity with overflow detection
 * - Coroutine-friendly suspend operations
 *
 * @param T The type of items in the channel
 * @param capacity Maximum number of items in the channel
 */
class PriorityChannel<T>(
    private val capacity: Int = Int.MAX_VALUE
) {
    private val queue = PriorityBlockingQueue<PrioritizedItem<T>>()
    private val mutex = Mutex()
    private val signalChannel = Channel<Unit>(Channel.UNLIMITED)

    /**
     * Current size of the channel.
     */
    val size: Int
        get() = queue.size

    /**
     * Whether the channel is empty.
     */
    val isEmpty: Boolean
        get() = queue.isEmpty()

    /**
     * Whether the channel is full.
     */
    val isFull: Boolean
        get() = queue.size >= capacity

    /**
     * Tries to send an item to the channel without suspending.
     *
     * @param item The item to send
     * @param priority The priority level (lower = higher priority)
     * @param sequenceNumber The sequence number for FIFO ordering
     * @return ChannelResult indicating success or failure
     */
    suspend fun trySend(
        item: T,
        priority: Int,
        sequenceNumber: Long
    ): ChannelResult<Unit> {
        return mutex.withLock {
            if (queue.size >= capacity) {
                ChannelResult.failure(ChannelResult.Cause.Overflow)
            } else {
                queue.offer(PrioritizedItem(item, priority, sequenceNumber))
                signalChannel.trySend(Unit)
                ChannelResult.success(Unit)
            }
        }
    }

    /**
     * Receives the highest priority item from the channel,
     * suspending if the channel is empty.
     *
     * @return The highest priority item with its metadata
     */
    suspend fun receive(): PrioritizedItem<T> {
        while (true) {
            val item = queue.poll()
            if (item != null) {
                return item
            }
            signalChannel.receive()
        }
    }

    /**
     * Tries to receive the highest priority item without suspending.
     *
     * @return The highest priority item or null if channel is empty
     */
    fun tryReceive(): PrioritizedItem<T>? {
        return queue.poll()
    }

    /**
     * Closes the channel, preventing further sends.
     */
    fun close() {
        signalChannel.close()
    }

    /**
     * Whether the channel is closed.
     */
    val isClosedForSend: Boolean
        get() = signalChannel.isClosedForSend

    /**
     * Whether the channel is closed for receive (empty and closed).
     */
    val isClosedForReceive: Boolean
        get() = signalChannel.isClosedForReceive && queue.isEmpty()
}

/**
 * Result of a channel operation.
 */
sealed class ChannelResult<out T> {
    data class Success<T>(val value: T) : ChannelResult<T>()
    data class Failure<T>(val cause: Cause) : ChannelResult<T>()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw cause.toException()
    }

    companion object {
        fun <T> success(value: T): ChannelResult<T> = Success(value)
        fun <T> failure(cause: Cause): ChannelResult<T> = Failure(cause)
    }

    sealed class Cause {
        abstract fun toException(): Exception

        object Overflow : Cause() {
            override fun toException(): Exception = ChannelOverflowException()
        }

        object Closed : Cause() {
            override fun toException(): Exception = ChannelClosedException()
        }
    }
}

class ChannelOverflowException : Exception("Channel is full")
class ChannelClosedException : Exception("Channel is closed")
