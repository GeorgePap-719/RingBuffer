package ringbuffer.channels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OneToOneRingBufferChannelTest {
    @Test
    fun send() = runBlocking(Dispatchers.Default) {
        val channel = OneToOneRingBufferChannel<Int>(1)
        with(Dispatchers.Default) {
            channel.send(1)
            channel.send(1)
        }
        println("Hello")
        println(channel.receive())
    }
}