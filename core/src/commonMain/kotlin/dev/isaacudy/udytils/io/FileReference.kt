package dev.isaacudy.udytils.io

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

data class FileReference(
    private val fileSystem: FileSystem,
    val path: Path,
) : FileData {
    private val metadata = fileSystem.metadataOrNull(path).let { metadata ->
        requireNotNull(metadata) {
            "File not found at path: $path"
        }
        require(!metadata.isDirectory) {
            "Path is a directory: $path"
        }
        require(metadata.isRegularFile) {
            "Path is not a file: $path"
        }
        require(path.parent != null) {
            "Path does not have a parent: $path"
        }
        return@let metadata
    }

    val parent by lazy {
        DirectoryReference(fileSystem, requireNotNull(path.parent))
    }

    override val size: Long = metadata.size
    override val name: String = path.name

    init {
        require(size < Int.MAX_VALUE) {
            "File size exceeds maximum allowed size: $size"
        }
    }

    override fun readBytes(offset: Long, length: Long): ByteArray {
        require(length <= size) {
            "readBytes length is greater than file size: length = $length, size = $size"
        }
        require(offset >= 0) {
            "readBytes offset is negative: offset = $offset"
        }
        require(offset + length <= size) {
            "readBytes offset and length exceed file size: offset = $offset, length = $length, size = $size"
        }
        return fileSystem.source(path).use { source ->
            source.buffered().use { buffered ->
                buffered.skip(offset)
                buffered.readByteArray(length.toInt())
            }
        }
    }

    fun writeBytes(bytes: ByteArray) {
        fileSystem.sink(path).use { sink ->
            sink.buffered().use { buffered ->
                buffered.write(bytes)
            }
        }
    }

    fun bufferedSource(): Source {
        return fileSystem.source(path).buffered()
    }

    fun delete() {
        fileSystem.delete(path)
    }
}

fun FileSystem.file(path: Path): FileReference = FileReference(this, path)