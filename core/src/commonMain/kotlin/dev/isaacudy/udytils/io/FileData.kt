package dev.isaacudy.udytils.io

interface FileData {
    val size: Long
    val name: String
    fun readBytes(offset: Long = 0, length: Long = size): ByteArray

    class InMemory(
        override val name: String,
        private val data: ByteArray,
    ) : FileData {
        override val size: Long = data.size.toLong()
        override fun readBytes(offset: Long, length: Long): ByteArray {
            return data.copyOfRange(offset.toInt(), (offset + length).toInt())
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
