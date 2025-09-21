package dev.isaacudy.udytils.io

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A file-based cache that can serialize and deserialize objects of type T to/from disk.
 *
 * @param T The type of object to cache
 * @param fileSystem The file system to use for storage operations
 * @param path The path where the cached object will be stored
 * @param serializer The KSerializer for type T
 * @param json The Json instance to use for serialization (defaults to a basic configuration)
 */
class FileCache<T : Any> internal constructor(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
) {
    /**
     * Stores the given value in the cache by serializing it to the file path.
     *
     * @param value The value to cache
     * @throws SerializationException if serialization fails
     * @throws Exception if file write operations fail
     */
    fun set(value: T) {
        try {
            // Ensure parent directory exists
            val parentDir = path.parent
            if (parentDir != null && !fileSystem.exists(parentDir)) {
                fileSystem.createDirectories(parentDir)
            }

            // Serialize the value to JSON
            val jsonString = json.encodeToString(serializer, value)

            // Write to file
            fileSystem.sink(path).buffered().use { sink ->
                sink.writeString(jsonString)
            }
        } catch (e: SerializationException) {
            throw SerializationException("Failed to serialize value for cache", e)
        } catch (e: Exception) {
            throw Exception("Failed to write cache file at path: $path", e)
        }
    }

    /**
     * Retrieves and deserializes the cached value from the file path.
     *
     * @return The cached value, or null if the file doesn't exist or deserialization fails
     */
    fun get(): T? {
        return try {
            if (!fileSystem.exists(path)) {
                return null
            }

            val metadata = fileSystem.metadataOrNull(path)
            if (metadata == null || !metadata.isRegularFile) {
                return null
            }

            // Read the file content
            val jsonString = fileSystem.source(path).buffered().use { source ->
                source.readString()
            }

            // Deserialize from JSON
            json.decodeFromString(serializer, jsonString)
        } catch (e: SerializationException) {
            // Log or handle serialization errors - for now return null
            null
        } catch (e: Exception) {
            // Log or handle file read errors - for now return null
            null
        }
    }

    /**
     * Checks if a cached value exists at the file path.
     *
     * @return true if the cache file exists and is a regular file, false otherwise
     */
    fun exists(): Boolean {
        val metadata = fileSystem.metadataOrNull(path)
        return metadata?.isRegularFile == true
    }

    /**
     * Deletes the cached file.
     *
     * @return true if the file was successfully deleted or didn't exist, false otherwise
     */
    fun clear(): Boolean {
        return try {
            if (fileSystem.exists(path)) {
                fileSystem.delete(path)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

}
