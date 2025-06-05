package ringbuffer

import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.jetbrains.kotlinx.lincheck.verifier.quiescent.QuiescentConsistencyVerifier
import org.jetbrains.kotlinx.lincheck.verifier.quiescent.QuiescentConsistent
import kotlin.test.Test

class ManyToOneRingBufferLincheckTest {
    private val buffer = ManyToOneRingBuffer<Int>(2)

    @QuiescentConsistent
    @Operation
    fun send(e: Int): Boolean {
        return buffer.trySend(e)
    }

    @QuiescentConsistent
    @Operation(nonParallelGroup = "consumers")
    fun receive(): Int? {
        return buffer.receiveOrNull()
    }

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .verifier(QuiescentConsistencyVerifier::class.java)
        .check(this::class)

    @Test
    fun stressTest() = StressOptions()
        .verifier(QuiescentConsistencyVerifier::class.java)
        .check(this::class)
}