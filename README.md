# RingBuffer 

Various implementations of [ring-buffer](https://en.wikipedia.org/wiki/Circular_buffer) structure.

- [OneToOne](src/main/kotlin/OneToOneRingBuffer.kt)
- [ManyToOne](src/main/kotlin/ManyToOneRingBuffer.kt)

This project is mostly exploratory. While the correctness of the structures is verified using the lincheck framework,
they are not optimized for production.

## Example

```kotlin
val buffer = ManyToOneRingBuffer<String>(capacity = 128)
val success = buffer.trySend("Hello")
val value = buffer.receiveOrNull()
```