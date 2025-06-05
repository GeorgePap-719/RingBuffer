package ringbuffer

interface RingBuffer<T> {
    val capacity: Int
    val size: Int

    fun trySend(element: T): Boolean
    fun receiveOrNull(): T?
}

val <T> RingBuffer<T>.isFull: Boolean get() = size == capacity
val <T> RingBuffer<T>.isEmpty: Boolean get() = size == 0