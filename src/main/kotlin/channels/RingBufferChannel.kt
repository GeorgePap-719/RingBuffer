package ringbuffer.channels

import kotlinx.coroutines.channels.Channel

interface RingBufferChannel<T>: Channel<T>