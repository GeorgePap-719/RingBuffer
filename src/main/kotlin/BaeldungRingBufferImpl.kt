package ringbuffer

/**
 * A ring-buffer implementation using a fixed-size array.
 * See: https://www.baeldung.com/java-ring-buffer.
 */
class BaeldungRingBufferImpl<T>(override val capacity: Int) : RingBuffer<T> {
    private val buffer = Array<Any?>(capacity) { null }

    private var readSequence: Int = 0
    private var writeSequence: Int = 0

    override val size: Int get() = writeSequence - readSequence

    override fun trySend(element: T): Boolean {
        if (isFull) return false
        buffer[writeSequence % capacity] = element
        writeSequence++
        return true
    }

    override fun receiveOrNull(): T? {
        if (isEmpty) return null
        @Suppress("UNCHECKED_CAST")
        val element = buffer[readSequence % capacity] as T
        readSequence++
        return element
    }
}

