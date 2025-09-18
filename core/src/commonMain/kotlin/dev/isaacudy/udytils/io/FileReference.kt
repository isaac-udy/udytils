package dev.isaacudy.udytils.io

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

data class FileReference(
    private val fileSystem: FileSystem,
    val path: Path,
) {
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
        return@let metadata
    }

    val size: Long = metadata.size
    val name: String = path.name

    fun readBytes(offset: Long = 0, length: Long = size): ByteArray {
        require(length < Int.MAX_VALUE) {
            "Length is too large: $length"
        }
        val source = fileSystem.source(path)
            .buffered()
        source.skip(offset)
        return source.readByteArray(length.toInt())
    }
}

fun FileSystem.file(path: Path): FileReference = FileReference(this, path)