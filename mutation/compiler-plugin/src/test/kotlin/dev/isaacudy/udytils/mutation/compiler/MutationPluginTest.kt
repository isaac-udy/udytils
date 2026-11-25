package dev.isaacudy.udytils.mutation.compiler

import dev.isaacudy.udytils.mutation.Mutants
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutationPluginTest {
    @AfterTest
    fun reset() {
        Mutants.active = 0
    }

    @Test
    fun `each mutant negates one condition or boolean return while active`() {
        val compiled = compile(
            """
            package sample

            fun sign(n: Int): String = if (n > 0) "positive" else "other"

            fun isEven(n: Int): Boolean {
                return n % 2 == 0
            }

            fun countdown(from: Int): Int {
                var n = from
                var steps = 0
                while (n > 0) { n--; steps++ }
                return steps
            }
            """,
        )
        val sample = compiled.loader.loadClass("sample.SampleKt")
        fun sign(n: Int) = sample.getMethod("sign", Int::class.java).invoke(null, n)
        fun isEven(n: Int) = sample.getMethod("isEven", Int::class.java).invoke(null, n)
        fun countdown(n: Int) = sample.getMethod("countdown", Int::class.java).invoke(null, n)

        val byText = compiled.mutants.associateBy { it.text }
        assertEquals(setOf("n > 0", "n % 2 == 0"), byText.keys)
        assertEquals("negate-boolean-return", byText.getValue("n % 2 == 0").operator)
        assertEquals(2, compiled.mutants.count { it.text == "n > 0" })

        assertEquals("positive", sign(1))
        assertEquals(true, isEven(2))
        assertEquals(3, countdown(3))

        val signMutant = compiled.mutants.single { it.text == "n > 0" && it.declaration.startsWith("sample.sign") }
        Mutants.active = signMutant.id
        assertEquals("other", sign(1))
        assertEquals(true, isEven(2))

        Mutants.active = byText.getValue("n % 2 == 0").id
        assertEquals(false, isEven(2))
        assertEquals("positive", sign(1))

        Mutants.active = compiled.mutants.single { it.declaration.startsWith("sample.countdown") }.id
        assertEquals(0, countdown(3))
    }

    @Test
    fun `adding a site leaves the other ids unchanged`() {
        val before = compile(
            """
            package sample
            fun f(a: Boolean, b: Boolean): Int {
                if (a) return 1
                if (b) return 2
                return 0
            }
            """,
        ).mutants
        val after = compile(
            """
            package sample
            fun f(a: Boolean, b: Boolean): Int {
                if (a && b) return 3
                if (a) return 1
                if (b) return 2
                return 0
            }
            """,
        ).mutants
        assertEquals(2, before.size)
        assertEquals(3, after.size)
        assertTrue(after.map { it.id }.containsAll(before.map { it.id }))
    }

    @Test
    fun `an object property initialiser is marked as running once per process`() {
        val compiled = compile(
            """
            package sample
            object Config {
                val verbose: String = if (System.getenv("X") == null) "quiet" else "loud"
                fun loud(flag: Boolean) = if (flag) "yes" else "no"
            }
            """,
        )
        assertEquals(mapOf("System.getenv(\"X\") == null" to true, "flag" to false), compiled.mutants.associate { it.text to it.initScope })
    }

    private class Compiled(val loader: ClassLoader, val mutants: List<ManifestMutant>)

    private data class ManifestMutant(val id: Long, val operator: String, val declaration: String, val text: String, val initScope: Boolean)

    private fun compile(source: String): Compiled {
        val dir = Files.createTempDirectory("mutation-plugin").toFile()
        val file = File(dir, "Sample.kt").apply { writeText(source.trimIndent()) }
        val out = File(dir, "classes")
        val manifests = File(dir, "manifests")
        val messages = ByteArrayOutputStream()
        val exit = K2JVMCompiler().exec(
            PrintStream(messages),
            file.absolutePath,
            "-d", out.absolutePath,
            "-classpath", System.getProperty("java.class.path"),
            "-no-stdlib",
            "-jvm-target", "11",
            "-Xplugin=" + System.getProperty("mutation.plugin.classpath").split(File.pathSeparator).filter { File(it).exists() }.joinToString(","),
            "-P", "plugin:$PLUGIN_ID:manifestDir=${manifests.absolutePath}",
        )
        assertEquals(ExitCode.OK, exit, messages.toString())
        val manifest = manifests.listFiles()!!.single().readText()
        val mutants = Regex(
            "\\{\"id\":(\\d+),\"line\":\\d+,\"operator\":\"([^\"]+)\",\"declaration\":\"((?:[^\"\\\\]|\\\\.)*)\",\"text\":\"((?:[^\"\\\\]|\\\\.)*)\",\"initScope\":(true|false)}",
        ).findAll(manifest).map {
            ManifestMutant(
                it.groupValues[1].toLong(), it.groupValues[2], it.groupValues[3],
                it.groupValues[4].replace("\\\"", "\""), it.groupValues[5].toBoolean(),
            )
        }.toList()
        return Compiled(URLClassLoader(arrayOf(out.toURI().toURL()), javaClass.classLoader), mutants)
    }
}
