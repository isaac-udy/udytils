package dev.isaacudy.udytils.mutation.gradle

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SourceDigestTest {
    @Test
    fun `the digest hashes each Kotlin file once and skips build and hidden directories`() {
        val repository = Files.createTempDirectory("repository").toFile()
        fun write(path: String, text: String) = File(repository, path).apply { parentFile.mkdirs(); writeText(text) }
        write("feature/a/client/src/commonMain/kotlin/A.kt", "class A")
        write("feature/a/client/src/jvmTest/kotlin/ATest.kt", "class ATest")
        write("feature/a/client/build/generated/G.kt", "class G")
        write("feature/a/client/.idea/I.kt", "class I")
        write("feature/a/client/src/commonMain/resources/notes.txt", "x")

        val digest = SourceDigest.of(repository, listOf("feature/a", "feature/a/client"))
        assertEquals(listOf("feature/a"), digest.roots)
        assertEquals(
            listOf("feature/a/client/src/commonMain/kotlin/A.kt", "feature/a/client/src/jvmTest/kotlin/ATest.kt"),
            digest.files.keys.toList(),
        )
        write("feature/a/client/src/commonMain/kotlin/A.kt", "class A2")
        assertNotEquals(digest.files, SourceDigest.of(repository, listOf("feature/a")).files)
    }
}
