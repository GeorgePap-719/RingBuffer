package ringbuffer

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import kotlin.test.Test

class OneToOneRingBufferLincheckTest {
    private val buffer = OneToOneRingBuffer<Int>(2)

    @Operation(nonParallelGroup = "producers")
    fun send(e: Int): Boolean {
        return buffer.trySend(e)
    }

    @Operation(nonParallelGroup = "consumers")
    fun receive(): Int? {
        return buffer.receiveOrNull()
    }

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)

    @Test
    fun stressTest() = StressOptions().check(this::class)
}