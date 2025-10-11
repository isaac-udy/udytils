package dev.isaacudy.udytils.io

import kotlinx.io.Source
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
        return fileSystem.source(path).use { source ->
            source.buffered().use { buffered ->
                buffered.skip(offset)
                buffered.readByteArray(length.toInt())
            }
        }
    }

    fun bufferedSource(): Source {
        return fileSystem.source(path).buffered()
    }
}

fun FileSystem.file(path: Path): FileReference = FileReference(this, path)