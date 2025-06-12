package ringbuffer

import kotlinx.atomicfu.atomic

/**
 * FIFO Multiple-producer single-consumer ring-buffer implementation using a fixed-size array,
 * based on the [Disruptor](https://lmax-exchange.github.io/disruptor/disruptor.html) paper.
 *
 * Note: This RingBuffer is not linearizable. Since we follow FIFO ordering the following execution is acceptable:
 *
 * ```
 * Thread 1: trySend(1) = true, receiveOrNull() = null
 * Thread 2: trySend(2) = 2 // this operation is concurrent with both operations in the first thread
 * ```
 */
class ManyToOneRingBuffer<T>(override val capacity: Int) : RingBuffer<T> {
    init {
        requirePowerOfTwo(capacity)
    }

    /*
        Logical structure of the ring buffer (capacity = 2)

                Slot array (circular buffer)
                +---------------------------+
                | Slot[0] | Slot[1]         |
                +---------------------------+
                    ^         ^
                    |         |
           index = head % 2   index = (head + 1) % 2
                    |
                read position

        Slot lifecycle (per slot):

            [Ready to Write]
                ↓ (Producer stores value & sequence++)
            [Ready to Read]
                ↓ (Consumer reads value & resets slot and updates sequence)
            [Ready to Write] (again with updated sequence)

        Example scenario with 3 threads:
            Initial State:
                head = 0, tail = 0
                Slot[0] = sequence 0 --> writable

            Thread 1:
                - Reads tail = 0
                - Slot[0].sequence == tail --> OK
                - CAS(tail, tail + 1) --> succeeds --> tail = 1
                - Thread is slow and starts again after thread-3 finishes
                - Writes value --> Slot[0].sequence = 1

            Thread 2:
                - Reads tail = 1
                - Slot[1].sequence == tail --> OK
                - CAS(tail, tail + 1) --> succeeds --> tail = 2
                - Writes value --> Slot[1].sequence = 2

            Thread 3:
                - Reads tail = 2
                - Slot[0] (index = 2 % 2 = 0)
                - We have two scenarios here: either writer has not finished yet (our case) or no one has read this slot.
                - Slot[0].sequence != tail --> can't write --> spins or returns false

        Invariants:
            - Slot[i].sequence == tail --> writable
            - Slot[i].sequence == head + 1 --> readable
            - At most `capacity` elements in the buffer at any time
            - slot.sequence increases monotonically to prevent ABA problems

        Visualization of sequence progression:

            sequence:   [ 0 --> 1 ] --> write done --> ready to be read
                        [ 1 --> 2 ] --> read done --> ready to be written
                        [ 2 --> 3 ] --> write done --> ready to be read

       Note: Only one thread can win the CAS(tail, tail+1) to reserve a slot.
    */
    private val slots = Array(capacity) { Slot<T>(it) }

    private val head = atomic(0)
    private val tail = atomic(0)

    private val mask = capacity - 1

    override val size: Int get() = tail.value - head.value

    override fun trySend(element: T): Boolean {
        while (true) {
            val curTail = tail.value
            val curHead = head.value
            if (curTail - curHead == capacity) return false
            val index = getIndex(curTail)
            val slot = slots[index]
            // Check first for expected sequence.
            if (slot.getSeqNumber() != curTail) {
                // Slot is not ready for writing.
                return false
            }
            // Try to reserve the slot by advancing the tail.
            if (!tail.compareAndSet(curTail, curTail + 1)) {
                // CAS failed - try again.
                continue
            }
            // This will always succeed.
            slot.allocate(curTail, element)
            return true
        }
    }

    override fun receiveOrNull(): T? {
        val curTail = tail.value
        val curHead = head.value
        if (curTail - curHead == 0) return null
        val index = getIndex(curHead)
        val slot = slots[index]
        val element = slot.getOrNull(curHead)
        // Not ready for read.
            ?: return null
        head.value = curHead + 1 // memory barrier is enough
        slot.free(capacity)
        return element
    }

    private fun getIndex(pointer: Int): Int = pointer and mask
}

/**
 * Each slot in the array is initialized with the corresponding index, e.g., the first slot is expected to be created with index zero.
 * It is the reader's responsibility to advance the sequence at the next position.
 * Essentially, this is a slot in the disruptor's ring-buffer algorithm.
 */
private class Slot<T>(initialIndex: Int) {
    // seq == tail -> ready to write
    // seq == pos + 1 -> ready to read
    private val sequence = atomic(initialIndex)

    private var value: T? = null

    fun getOrNull(head: Int): T? {
        val seq = sequence.value
        if (seq != head + 1) return null
        // Sequence has marked this slot as ready to read,
        // therefore, this should always be not-null.
        assert(value != null)
        return value
    }

    fun getSeqNumber(): Int = sequence.value

    fun allocate(pos: Int, value: T) {
        // First update value,
        this.value = value
        // Then inform slot is ready.
        val expected = pos
        while (sequence.value != expected) {
            // Busy spin.
        }
        sequence.value = pos + 1
    }

    fun free(capacity: Int) {
        val seq = sequence.value
        assert(value != null)
        value = null // free value
        // Mark slot ready to write.
        sequence.value = seq - 1 + capacity
    }

    override fun toString(): String {
        return "Slot(seq:${sequence.value}, value:$value)"
    }
}

private fun requirePowerOfTwo(capacity: Int) {
    val isPowerOfTwo = capacity > 0 && ((capacity and (capacity.inv() + 1)) == capacity)
    require(isPowerOfTwo) { "Capacity must be a positive power of 2, but got:$capacity" }
}