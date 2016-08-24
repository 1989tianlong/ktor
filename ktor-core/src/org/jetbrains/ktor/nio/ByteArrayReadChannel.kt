package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.nio.*

class ByteArrayReadChannel(val source: ByteBuffer, val maxReadSize: Int = Int.MAX_VALUE) : SeekableChannel {
    private val initialPosition = source.position()

    constructor(source: ByteArray, maxReadSize: Int = Int.MAX_VALUE) : this(ByteBuffer.wrap(source), maxReadSize)

    init {
        require(maxReadSize > 0) { "maxReadSize should be positive: $maxReadSize" }
    }

    override val position: Long
        get() = (source.position() - initialPosition).toLong()

    override fun seek(position: Long, handler: AsyncHandler) {
        val newPosition = initialPosition + Math.min(Int.MAX_VALUE.toLong(), position).toInt()
        if (newPosition > source.limit()) {
            handler.failed(IllegalArgumentException("Seek to $position failed for buffer size ${source.limit() - initialPosition}"))
        } else {
            source.position(initialPosition + position.toInt())
            handler.successEnd()
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!source.hasRemaining()) {
            handler.successEnd()
            return
        }

        val size = source.putTo(dst, maxReadSize)
        handler.success(size)
    }

    override fun close() {
    }
}