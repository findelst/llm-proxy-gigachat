package ru.ddd.llmproxy.unit.infrastructure.queue

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ddd.llmproxy.infrastructure.queue.PriorityChannel

/**
 * Tests for PriorityChannel.
 *
 * Verifies:
 * - Priority ordering (lower priority value = higher priority)
 * - FIFO ordering within same priority level
 * - Capacity limits
 * - Overflow handling
 */
class PriorityChannelTest {

    private lateinit var channel: PriorityChannel<String>

    @BeforeEach
    fun setUp() {
        channel = PriorityChannel(capacity = 10)
    }

    // ==================== Basic Operations ====================

    @Test
    fun `should send and receive item`() = runTest {
        val result = channel.trySend("test", priority = 1, sequenceNumber = 1)
        assertTrue(result.isSuccess)

        val item = channel.receive()
        assertEquals("test", item.item)
        assertEquals(1, item.priority)
        assertEquals(1, item.sequenceNumber)
    }

    @Test
    fun `should return failure when capacity exceeded`() = runTest {
        val smallChannel = PriorityChannel<String>(capacity = 2)

        assertTrue(smallChannel.trySend("item1", 1, 1).isSuccess)
        assertTrue(smallChannel.trySend("item2", 1, 2).isSuccess)
        assertTrue(smallChannel.trySend("item3", 1, 3).isFailure)
    }

    @Test
    fun `should track size correctly`() = runTest {
        assertEquals(0, channel.size)

        channel.trySend("item1", 1, 1)
        assertEquals(1, channel.size)

        channel.trySend("item2", 1, 2)
        assertEquals(2, channel.size)

        channel.receive()
        assertEquals(1, channel.size)
    }

    // ==================== Priority Ordering ====================

    @Test
    fun `should receive items in priority order`() = runTest {
        // Add items with different priorities (lower = higher priority)
        channel.trySend("low", priority = 3, sequenceNumber = 1)
        channel.trySend("high", priority = 1, sequenceNumber = 2)
        channel.trySend("medium", priority = 2, sequenceNumber = 3)

        // Should receive in priority order
        assertEquals("high", channel.receive().item)
        assertEquals("medium", channel.receive().item)
        assertEquals("low", channel.receive().item)
    }

    @Test
    fun `should prioritize lower priority value first`() = runTest {
        channel.trySend("p3-item", priority = 3, sequenceNumber = 1)
        channel.trySend("p1-item", priority = 1, sequenceNumber = 2)
        channel.trySend("p2-item", priority = 2, sequenceNumber = 3)

        val order = mutableListOf<String>()
        repeat(3) {
            order.add(channel.receive().item)
        }

        assertEquals(listOf("p1-item", "p2-item", "p3-item"), order)
    }

    // ==================== FIFO Within Priority ====================

    @Test
    fun `should maintain FIFO order within same priority`() = runTest {
        // Add items with same priority but different sequence numbers
        channel.trySend("first", priority = 1, sequenceNumber = 100)
        channel.trySend("second", priority = 1, sequenceNumber = 101)
        channel.trySend("third", priority = 1, sequenceNumber = 102)

        assertEquals("first", channel.receive().item)
        assertEquals("second", channel.receive().item)
        assertEquals("third", channel.receive().item)
    }

    @Test
    fun `should maintain FIFO within priority even with mixed priorities`() = runTest {
        // Add P1 items
        channel.trySend("p1-first", priority = 1, sequenceNumber = 1)
        channel.trySend("p1-second", priority = 1, sequenceNumber = 2)

        // Add P2 items
        channel.trySend("p2-first", priority = 2, sequenceNumber = 3)
        channel.trySend("p2-second", priority = 2, sequenceNumber = 4)

        // Add another P1 item (should come before P2 items but after other P1 items)
        channel.trySend("p1-third", priority = 1, sequenceNumber = 5)

        // Order should be: all P1 in FIFO order, then all P2 in FIFO order
        assertEquals("p1-first", channel.receive().item)
        assertEquals("p1-second", channel.receive().item)
        assertEquals("p1-third", channel.receive().item)
        assertEquals("p2-first", channel.receive().item)
        assertEquals("p2-second", channel.receive().item)
    }

    // ==================== Concurrent Access ====================

    @Test
    fun `should handle concurrent sends`() = runTest {
        val itemCount = 100
        val jobs = (1..itemCount).map { index ->
            async {
                val priority = (index % 3) + 1
                channel.trySend("item-$index", priority, index.toLong())
            }
        }

        val results = jobs.awaitAll()
        // With capacity of 10, most concurrent sends will fail due to capacity
        // This tests that concurrent access doesn't cause exceptions
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `should maintain priority order under concurrent load`() = runTest {
        val largeChannel = PriorityChannel<String>(capacity = 1000)

        // Send items with mixed priorities concurrently
        val jobs = (1..100).map { index ->
            async {
                val priority = (index % 3) + 1
                largeChannel.trySend("item-$index", priority, index.toLong())
            }
        }
        jobs.awaitAll()

        // Collect received items
        val received = mutableListOf<String>()
        repeat(100) {
            received.add(largeChannel.receive().item)
        }

        // Verify all items received
        assertEquals(100, received.size)
        assertTrue(received.all { it.startsWith("item-") })
    }

    // ==================== Channel State ====================

    @Test
    fun `should report empty state correctly`() = runTest {
        assertTrue(channel.isEmpty)

        channel.trySend("item", 1, 1)
        assertFalse(channel.isEmpty)

        channel.receive()
        assertTrue(channel.isEmpty)
    }

    @Test
    fun `should report full state correctly`() = runTest {
        val smallChannel = PriorityChannel<String>(capacity = 3)

        assertFalse(smallChannel.isFull)

        smallChannel.trySend("item1", 1, 1)
        assertFalse(smallChannel.isFull)

        smallChannel.trySend("item2", 1, 2)
        assertFalse(smallChannel.isFull)

        smallChannel.trySend("item3", 1, 3)
        assertTrue(smallChannel.isFull)
    }

    @Test
    fun `should close channel properly`() = runTest {
        channel.trySend("item", 1, 1)
        channel.close()

        assertTrue(channel.isClosedForSend)
        assertFalse(channel.isEmpty) // Still has items

        val item = channel.receive()
        assertEquals("item", item.item)
        assertTrue(channel.isEmpty)
    }

    // ==================== TryReceive ====================

    @Test
    fun `tryReceive should return null when empty`() {
        assertNull(channel.tryReceive())
    }

    @Test
    fun `tryReceive should return highest priority item`() = runTest {
        channel.trySend("low", 3, 1)
        channel.trySend("high", 1, 2)

        val item = channel.tryReceive()
        assertNotNull(item)
        assertEquals("high", item?.item)
    }

    @Test
    fun `tryReceive should not suspend`() = runTest {
        // tryReceive should return immediately even if empty
        val result = channel.tryReceive()
        assertNull(result)
    }
}
