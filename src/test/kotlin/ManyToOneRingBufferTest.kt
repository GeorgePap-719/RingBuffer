package ringbuffer

import kotlin.test.Test

class ManyToOneRingBufferTest {

    @Test
    fun basicTest() {
        val buffer = ManyToOneRingBuffer<Int>(1)
        assert(buffer.isEmpty)
        assert(buffer.size == 0)
        assert(buffer.receiveOrNull() == null)
        assert(buffer.trySend(10))
        assert(!buffer.trySend(10))
        assert(buffer.receiveOrNull() == 10)
        assert(buffer.receiveOrNull() == null)
    }
}