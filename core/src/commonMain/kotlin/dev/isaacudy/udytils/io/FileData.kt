package dev.isaacudy.udytils.io

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source

interface FileData {
    val size: Long
    val name: String
    fun readBytes(offset: Long = 0, length: Long = size): ByteArray
    fun source(): RawSource

    class InMemory(
        override val name: String,
        private val data: ByteArray,
    ) : FileData {
        override val size: Long = data.size.toLong()
        override fun readBytes(offset: Long, length: Long): ByteArray {
            return data.copyOfRange(offset.toInt(), (offset + length).toInt())
        }

        override fun source(): Source {
            val buffer = Buffer()
            buffer.write(data)
            return buffer
        }
    }

    companion object {
        fun fromBytes(name: String, data: ByteArray): FileData {
            return InMemory(
                name = name,
                data = data,
            )
        }
    }
}
