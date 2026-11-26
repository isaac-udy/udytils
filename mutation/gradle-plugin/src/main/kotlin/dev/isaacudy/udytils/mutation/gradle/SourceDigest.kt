package dev.isaacudy.udytils.mutation.gradle

import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/**
 * The Kotlin sources, production and test, of every module when a run started: a reader of
 * `results.json` hashes the same files again to find the files that changed after the run.
 * Directories named `build` and directories whose name starts with `.` are skipped.
 */
@Serializable
internal data class SourceDigest(
    /** Module directories from the repository root, without nested module directories. */
    val roots: List<String>,
    /** Path from the repository root to the first 16 hex digits of the file's SHA-256. */
    val files: Map<String, String>,
) {
    companion object {
        fun of(repository: File, moduleDirectories: Collection<String>): SourceDigest {
            val roots = moduleDirectories.filter { it.isNotEmpty() }.sorted()
                .fold(mutableListOf<String>()) { kept, dir -> kept.apply { if (none { dir.startsWith("$it/") }) add(dir) } }
            val files = sortedMapOf<String, String>()
            for (root in roots) {
                repository.resolve(root).walkTopDown()
                    .onEnter { it.name != "build" && !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { files[it.relativeTo(repository).invariantSeparatorsPath] = hash(it) }
            }
            return SourceDigest(roots, files)
        }

        private fun hash(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).take(8).joinToString("") { "%02x".format(it) }
    }
}
