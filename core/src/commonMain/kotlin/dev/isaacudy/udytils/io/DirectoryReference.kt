package dev.isaacudy.udytils.io

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

class DirectoryReference(
    private val fileSystem: FileSystem,
    val path: Path,
) {
    private val metadata = fileSystem.metadataOrNull(path).let { metadata ->
        requireNotNull(metadata) {
            "Directory not found at path: $path"
        }
        require(!metadata.isRegularFile) {
            "Path is a file: $path"
        }
        return@let metadata
    }

    val name: String = path.name

    fun ensureExists() {
        fileSystem.createDirectories(path)
    }

    fun createDirectory(name: String): DirectoryReference {
        val directoryPath = Path(path, name)
        fileSystem.createDirectories(directoryPath)
        return DirectoryReference(fileSystem, directoryPath)
    }

    fun writeFile(name: String, content: ByteArray): FileReference {
        val filePath = Path(path, name)
        fileSystem.sink(filePath)
            .buffered()
            .use { it.write(content) }
        return FileReference(fileSystem, filePath)
    }

    val directories
        get() = fileSystem.list(path)
            .mapNotNull {
                val metadata = fileSystem.metadataOrNull(it)
                when {
                    metadata == null -> null
                    metadata.isDirectory -> DirectoryReference(fileSystem, it)
                    else -> null
                }
            }

    val files
        get() = fileSystem.list(path)
            .mapNotNull {
                val metadata = fileSystem.metadataOrNull(it)
                when {
                    metadata == null -> null
                    metadata.isDirectory -> null
                    metadata.isRegularFile -> FileReference(fileSystem, it)
                    else -> null
                }
            }
}