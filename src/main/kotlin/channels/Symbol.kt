package ringbuffer.channels

/**
 * A symbol class that is used to define unique constants that are self-explanatory in debugger.
 */
internal class Symbol(val symbol: String) {
    override fun toString(): String = "<$symbol>"
}