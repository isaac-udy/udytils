package dev.isaacudy.udytils.io

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

open class FileCacheProvider(
    private val fileSystem: FileSystem,
    private val root: Path,
) {
    fun <T : Any> cache(
        name: String,
        serializer: KSerializer<T>,
    ): FileCache<T> {
        return FileCache(
            fileSystem = fileSystem,
            path = Path(root, name),
            serializer = serializer,
        )
    }
}

inline fun <reified T : Any> FileCacheProvider.cache(
    name: String,
): FileCache<T> = cache(name, serializer<T>())