package ringbuffer

import kotlin.test.Test

class BaeldungRingBufferTest {

    @Test
    fun basicTest() {
        val buffer = BaeldungRingBufferImpl<Int>(1)
        assert(buffer.isEmpty)
        assert(buffer.size == 0)
        assert(buffer.receiveOrNull() == null)
        assert(buffer.trySend(10))
        assert(!buffer.trySend(10))
        assert(buffer.receiveOrNull() == 10)
        assert(buffer.receiveOrNull() == null)
    }
}