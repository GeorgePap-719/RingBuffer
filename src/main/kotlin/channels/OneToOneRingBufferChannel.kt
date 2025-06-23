package ringbuffer.channels

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume


/**
 * A FIFO single-producer single-consumer ring-buffer channel implementation using a fixed-size array.
 */
// Notes:
// We can fuse buffer with waiters probably,
// We can store in buffer state/waiter + element,
// then after we exceed the capacity we can still you the counters
// like: counter - capacity and storing the waiter in the list.
//
// Another idea: is like the buffer channel, we do not need extra counters.
// We will simply inc the current ones.
// When we exceed capacity we will store the waiters in an array with [state, index]
// This way we can "look" for the proper cell.
@OptIn(InternalCoroutinesApi::class)
class OneToOneRingBufferChannel<E>(private val capacity: Int) {
    private val segments = SegmentArray<E>(capacity)

    @Volatile
    private var receiversCounter: Int = 0

    @Volatile
    private var sendersCounter: Int = 0

    // -------------------------------- Channel send implementation --------------------------------

    suspend fun send(element: E) {
        sendImpl(
            element = element,
            // Do not create a continuation until it is required;
            // it is created later via [onNoWaiterSuspend], if needed.
            waiter = null,
            onRendezvousOrBuffered = {},
            // As no waiter is provided, suspension is impossible.
            onSuspend = { _, _, _ -> assert(false) },
            onNoWaiterSuspend = { segment, element, i, s ->
                sendOnNoWaiterSuspend(segment, i, element, s)
            }
        )
    }

    fun trySend(element: E): ChannelResult<Unit> {
        TODO()
    }

    private inline fun <R> sendImpl(
        element: E,
        waiter: Waiter?,
        onRendezvousOrBuffered: () -> R,
        onSuspend: (segment: SegmentArray<E>, element: E, i: Int) -> R,
        onNoWaiterSuspend: (
            segment: SegmentArray<E>,
            element: E,
            i: Int,
            s: Int
        ) -> R
    ): R {
        val sendersCounter = sendersCounter
        val index = getIndex(sendersCounter)
        val seg = segments
        when (val result = updateCellSend(seg, index, element, waiter)) {
            RESULT_RENDEZVOUS -> return onRendezvousOrBuffered()
            RESULT_BUFFERED -> return onRendezvousOrBuffered()
            RESULT_SUSPEND -> {
                // The operation has decided to suspend
                // and installed the specified waiter.
                return onSuspend(seg, element, index)
            }

            RESULT_SUSPEND_NO_WAITER -> {
                // The operation has decided to suspend,
                // but no waiter has been provided.
                return onNoWaiterSuspend(seg, element, index, sendersCounter)
            }

            else -> error("Invalid result:$result")
        }
    }

    private fun updateCellSend(
        segment: SegmentArray<E>,
        index: Int,
        element: E,
        waiter: Waiter?
    ): Int {
        val state = segment.getState(index)
        // All states for SP:
        // Cell is empty --> store element.
        // Cell is a Waiter --> store and resume waiter
        // Cell is BUFFERED (slot is not ready for write) --> store waiter in queue
        when {
            state === null -> {
                // For SP implementations, the element should always be buffered, or a rendezvous should happen.
                check(bufferOrRendezvousSend())
                segment.storeElement(index, element)
                segment.setState(index, BUFFERED)
                // Publish element.
                this.sendersCounter++
                // The element has been successfully buffered, finish.
                return RESULT_BUFFERED
            }
            // A waiting receiver is stored in the cell.
            state is Waiter -> {
                // As the element will be passed directly to the waiter,
                // the algorithm just moves the cursor.
                this.sendersCounter++
                if (state.tryResumeReceiver(element)) {
                    // Rendezvous! Clean the cell state and finish.
                    segment.setState(index, null)
                    return RESULT_RENDEZVOUS
                } else {
                    // The resumption has failed; for SP should not be possible.
                    error("Resume failed for state:$state")
                }
            }

            state == BUFFERED -> {
                // The waiter is not specified; return the corresponding result.
                if (waiter == null) {
                    return RESULT_SUSPEND_NO_WAITER
                } else {
                    segment.install(waiter)
                    return RESULT_SUSPEND
                }
            }

            else -> error("Corrupted state:$state")
        }
    }

    private suspend fun sendOnNoWaiterSuspend(
        segment: SegmentArray<E>,
        index: Int,
        element: E,
        s: Int, // Global index of cell.
    ) = suspendCancellableCoroutine { cont ->
        println("Going inside:sendOnNoWaiterSuspend")
        // Update the cell again, now with the non-null waiter,
        // restarting the operation from the beginning on failure.
        val waiter = Waiter(cont, s)
        // Let's assert that no waiter is not stored twice.
        // This should not be possible for SPSC implementations.
        segment.getWaiterOrNull(s)?.let {
            assert(it != waiter)
        }
        when (val result = updateCellSend(segment, index, element, waiter)) {
            RESULT_BUFFERED, RESULT_RENDEZVOUS -> cont.resume(Unit)
            RESULT_SUSPEND -> {
                //TODO: here we should prepare for suspension.
                println("Preparing for suspension:$waiter")
            }

            else -> error("Invalid result:$result")
        }
    }

    private fun bufferOrRendezvousSend(): Boolean {
        return sendersCounter - receiversCounter < capacity
    }

    private fun shouldSendSuspend(): Boolean {
        return sendersCounter - receiversCounter == capacity
    }

    // -------------------------------- Channel receive implementation --------------------------------

    suspend fun receive(): E? {

        segments.getWaiterOrNull(2)?.let {
            @Suppress("UNCHECKED_CAST")
            it.cont as CancellableContinuation<Unit>
            it.cont.resume(Unit)
        }
        return null
    }

    private fun <R> receiveImpl(
        waiter: Waiter?,
        onElementRetrieved: (element: E) -> R,
        onSuspend: (seg: SegmentArray<E>, i: Int, r: Int) -> R,
        onNoWaiterSuspend: (seg: SegmentArray<E>, i: Int, r: Int) -> R
    ): R {
        TODO()
    }

    private fun updateCellReceive(
        segment: SegmentArray<E>,
        index: Int,
        r: Int,
        waiter: Any?
    ): Any? {
        TODO()
    }

    private fun moveSenderInCell(): Boolean {
        //TODO;
        return true
    }

    private fun getIndex(counter: Int): Int = counter % capacity

    @Suppress("UNCHECKED_CAST")
    private fun Waiter.tryResumeReceiver(element: E): Boolean {
        cont as CancellableContinuation<E>
        return cont.tryResume0(element, null)
    }
}

// Cell states stored in segment.
//sealed class Cell(state: Any?)
//class Empty : Cell(null)
//class CellWaiter(waiter: Waiter) : Cell(waiter)
//object Buffered : Cell(Buffered)


data class Waiter(
    // States:
    // EMPTY == null
    // DONE
    // BUFFERED == Symbol
    // Continuation == Waiter
    val cont: CancellableContinuation<*>,
    // The global index.
    val s: Int
)

/**
 * Similar to "The Buffered Channel" algorithm, but using a fixed-size array.
 */
private class SegmentArray<E>(capacity: Int) {
    private val waiters = ConcurrentLinkedDeque<Waiter>()
    private val data = Array<Any?>(capacity * 2) { null } // 2 registers per slot: element + state

    fun storeElement(index: Int, element: E) {
        data[index * 2] = element
    }

    @Suppress("UNCHECKED_CAST")
    fun getElement(index: Int): E = data[index * 2] as E
    fun cleanElement(index: Int) {
        data[index * 2] = null
    }

    fun getState(index: Int): Any? = data[index * 2 + 1]
    fun setState(index: Int, state: Any?) {
        data[index * 2 + 1] = state
    }

    /*
     * We provide a bit misleading naming here,
     * since we do not do any actual CAS operation.
     * Nevertheless, we actually check if the expected case matches.
     */
    fun casState(index: Int, expect: Any?, update: Any?): Boolean {
        val cur = data[index * 2 + 1]
        if (cur != expect) return false
        data[index * 2 + 1] = update
        return true
    }

    // Separate path to install waiters when there is no "space" in buffer.
    fun install(waiter: Waiter) {
        waiters.add(waiter)
    }

    fun retrieveFirstWaiter(index: Int): Waiter {
        val waiter = waiters.removeFirst()
        check(waiter.s == index)
        return waiter
    }

    fun getWaiterOrNull(index: Int): Waiter? {
        val waiter = waiters.peekFirst() ?: return null
        check(waiter.s == index)
        return waiter
    }
}

/**
 * Tries to resume this continuation with the specified
 * value. Returns `true` on success and `false` on failure.
 */
@OptIn(InternalCoroutinesApi::class)
private fun <T> CancellableContinuation<T>.tryResume0(
    value: T,
    onCancellation: ((cause: Throwable, value: T, context: CoroutineContext) -> Unit)? = null
): Boolean =
    tryResume(value, null, onCancellation).let { token ->
        if (token != null) {
            completeResume(token)
            true
        } else {
            false
        }
    }


private val DONE = Symbol("DONE")
private val BUFFERED = Symbol("BUFFERED")

//private val DONE_RCV = Symbol("DONE_RCV")
// Cancelled receiver.
private val INTERRUPTED_RCV = Symbol("INTERRUPTED_RCV")

// Results for updateCellSend()
private const val RESULT_RENDEZVOUS = 0
private const val RESULT_BUFFERED = 1
private const val RESULT_SUSPEND = 2
private const val RESULT_SUSPEND_NO_WAITER = 3

//private const val RESULT_CLOSED = 4
//private const val RESULT_FAILED = 5


