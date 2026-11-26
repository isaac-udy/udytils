package dev.isaacudy.udytils.mutation.compiler

import dev.isaacudy.udytils.mutation.Mutants
import dev.isaacudy.udytils.mutation.compiler.operators.MutationOperators
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MutationPluginTest {
    @AfterTest
    fun reset() {
        Mutants.active = 0
    }

    @Test
    fun `negate-condition negates if, when and while conditions`() {
        val compiled = compile(
            """
            fun sign(n: Int): String = if (n > 0) "positive" else "other"
            fun kind(n: Int): String = when { n == 0 -> "zero"; else -> "other" }
            fun countdown(from: Int): Int {
                var n = from
                var steps = 0
                while (n > 0) { n--; steps++ }
                return steps
            }
            """,
            "negate-condition",
        )
        compiled.expect("sign", listOf(1), "positive", "negated" to "other")
        compiled.expect("kind", listOf(0), "zero", "negated" to "other")
        compiled.expect("countdown", listOf(3), 3, "negated" to 0)
    }

    @Test
    fun `boolean-return returns true and false`() {
        val compiled = compile("fun isEven(n: Int): Boolean { return n % 2 == 0 }", "boolean-return")
        compiled.expect("isEven", listOf(2), true, "→ true" to true, "→ false" to false)
    }

    @Test
    fun `empty-return returns the empty value of the return type`() {
        val compiled = compile(
            """
            fun names(): List<String> = listOf("a")
            fun ids(): Set<Int> = setOf(1)
            fun find(): String? = "x"
            fun label(): String = "x"
            fun count(): Int = 3
            fun none(): String? = null
            fun ids2(): Collection<Int> = hashSetOf(1)
            fun list(): List<Int> { val l = ArrayList<Int>(); l.add(1); return l }
            fun flag(): Boolean = TODO()
            """,
            "empty-return", "boolean-return",
        )
        compiled.expect("ids2", emptyList(), hashSetOf(1), "→ emptyList()" to emptyList<Int>())
        compiled.expect("list", emptyList(), listOf(1), "→ emptyList()" to emptyList<Int>())
        assertTrue(compiled.mutantsOf("flag").isEmpty())
        compiled.expect("names", emptyList(), listOf("a"), "→ emptyList()" to emptyList<String>())
        compiled.expect("ids", emptyList(), setOf(1), "→ emptySet()" to emptySet<Int>())
        compiled.expect("find", emptyList(), "x", "→ null" to null)
        compiled.expect("label", emptyList(), "x", "→ \"\"" to "")
        compiled.expect("count", emptyList(), 3, "→ 0" to 0)
        assertTrue(compiled.mutantsOf("none").isEmpty())
    }

    @Test
    fun `remove-call removes unused calls and assignments`() {
        val compiled = compile(
            """
            class Counter { var n = 0 }
            fun record(): List<Int> {
                val list = mutableListOf<Int>()
                list.add(1)
                return list
            }
            fun increment(): Int {
                val counter = Counter()
                counter.n += 2
                return counter.n
            }
            fun assign(): Int {
                val counter = Counter()
                counter.n = 5
                return counter.n
            }
            fun failing(flag: Boolean): Int {
                if (flag) error("x")
                return 1
            }
            sealed interface Update { data class Add(val n: Int) : Update; data object Clear : Update }
            fun branch(flag: Boolean): List<Int> {
                val list = mutableListOf<Int>()
                if (flag) list.add(1)
                return list
            }
            fun apply(update: Update): List<Int> {
                val list = mutableListOf(0)
                when (update) {
                    is Update.Add -> list.add(update.n)
                    Update.Clear -> list.clear()
                }
                return list
            }
            fun valueBlock(flag: Boolean): Int {
                val list = mutableListOf<Int>()
                val x = if (flag) { list.add(1); 5 } else 6
                return x + list.size
            }
            """,
            "remove-call",
        )
        compiled.expect("record", emptyList(), listOf(1), "removed" to emptyList<Int>())
        compiled.expect("increment", emptyList(), 2, "removed" to 0)
        compiled.expect("assign", emptyList(), 5, "removed" to 0)
        assertTrue(compiled.mutantsOf("failing").isEmpty())
        compiled.expect("branch", listOf(true), listOf(1), "removed" to emptyList<Int>())
        assertEquals(listOf("list.add(update.n)", "list.clear()"), compiled.mutantsOf("apply").map { it.text })
        assertEquals(listOf("list.add(1)"), compiled.mutantsOf("valueBlock").map { it.text }, compiled.mutants.toString())
        compiled.expect("valueBlock", listOf(true), 6, "removed" to 5)
        assertEquals(
            listOf("kotlin.collections.MutableList.add", "sample.Counter.n", "sample.Counter.n"),
            listOf("record", "increment", "assign").map { compiled.mutantsOf(it).single().callee },
        )
    }

    @Test
    fun `conditionals-boundary moves the comparison boundary`() {
        val compiled = compile(
            """
            class Box(val v: Int) : Comparable<Box> { override fun compareTo(other: Box) = v - other.v }
            fun lt(a: Int, b: Int) = a < b
            fun ge(a: Int, b: Int) = a >= b
            fun boxGt(a: Int, b: Int) = Box(a) > Box(b)
            """,
            "conditionals-boundary",
        )
        compiled.expect("lt", listOf(2, 2), false, "< → <=" to true)
        compiled.expect("ge", listOf(2, 2), true, ">= → >" to false)
        compiled.expect("boxGt", listOf(2, 2), false, "> → >=" to true)
    }

    @Test
    fun `logical-connector swaps the connector and keeps one operand`() {
        val compiled = compile(
            """
            fun and(a: Boolean, b: Boolean) = a && b
            fun or(a: Boolean, b: Boolean) = a || b
            """,
            "logical-connector",
        )
        compiled.expect("and", listOf(true, false), false, "&& → ||" to true, "left operand only" to true, "right operand only" to false)
        compiled.expect("or", listOf(false, true), true, "|| → &&" to false, "left operand only" to false, "right operand only" to true)
    }

    @Test
    fun `elvis replaces the expression with its right side`() {
        val compiled = compile(
            """
            fun orDefault(a: String?) = a ?: "default"
            fun lengthOrZero(a: String?): Int {
                val s = a ?: return 0
                return s.length
            }
            """,
            "elvis",
        )
        compiled.expect("orDefault", listOf("value"), "value", "→ right side" to "default")
        compiled.expect("lengthOrZero", listOf("abc"), 3, "→ right side" to 0)
    }

    @Test
    fun `each mutant of nested sites changes only its own site`() {
        val compiled = compile(
            """
            fun check(a: Int, b: Int, items: List<Int>): String =
                if (a < b && items.any { it > a }) "yes" else "no"
            """,
        )
        val args = listOf(1, 2, listOf(1))
        assertEquals("no", compiled.call("check", args))
        val outcomes = compiled.mutantsOf("check").associate { mutant ->
            Mutants.active = mutant.id
            "${mutant.operator} ${mutant.variant} `${mutant.text}`" to compiled.call("check", args)
        }
        Mutants.active = 0
        val condition = "a < b && items.any { it > a }"
        assertEquals(
            mapOf(
                "negate-condition negated `$condition`" to "yes",
                "logical-connector && → || `$condition`" to "yes",
                "logical-connector left operand only `$condition`" to "yes",
                "logical-connector right operand only `$condition`" to "no",
                "conditionals-boundary < → <= `a < b`" to "no",
                "conditionals-boundary > → >= `it > a`" to "yes",
                "empty-return → \"\" `if ($condition) \"yes\" else \"no\"`" to "",
            ),
            outcomes,
        )
        assertEquals("no", compiled.call("check", args))
    }

    @Test
    fun `adding a site leaves the other ids unchanged`() {
        val before = compile(
            """
            fun f(a: Boolean, b: Boolean): Int {
                if (a) return 1
                if (b) return 2
                return 0
            }
            """,
        ).mutants
        val after = compile(
            """
            fun f(a: Boolean, b: Boolean): Int {
                if (a && b) return 3
                if (a) return 1
                if (b) return 2
                return 0
            }
            """,
        ).mutants
        assertTrue(after.size > before.size)
        assertTrue(after.map { it.id }.containsAll(before.map { it.id }))
        assertEquals(after.size, after.map { it.id }.toSet().size)
    }

    @Test
    fun `an object property initialiser is marked as running once per process`() {
        val compiled = compile(
            """
            object Config {
                val verbose: String = if (System.getenv("X") == null) "quiet" else "loud"
                fun loud(flag: Boolean) = if (flag) "yes" else "no"
            }
            """,
            "negate-condition",
        )
        assertEquals(mapOf("System.getenv(\"X\") == null" to true, "flag" to false), compiled.mutants.associate { it.text to it.initScope })
    }

    @Test
    fun `a mutant's text in the manifest is capped and its id hashes the full text`() {
        val long = (1..40).joinToString(" && ") { "a > $it" }
        val compiled = compile("fun f(a: Int) = if ($long) 1 else 0", "negate-condition")
        assertEquals(long.take(160), compiled.mutants.single().text)
    }

    @Test
    fun `an unknown operator id fails with the known ids`() {
        val failure = assertFailsWith<IllegalArgumentException> { MutationOperators.select(listOf("remov-call")) }
        assertTrue("\"remov-call\"" in failure.message!! && "remove-call" in failure.message!!, failure.message)
    }

    private class Compiled(val loader: ClassLoader, val mutants: List<ManifestMutant>) {
        private val type by lazy { loader.loadClass("sample.SampleKt") }

        fun call(name: String, args: List<Any?>): Any? =
            type.methods.single { it.name == name }.invoke(null, *args.toTypedArray())

        fun mutantsOf(name: String) = mutants.filter { it.declaration.startsWith("sample.$name(") }

        /** Checks that [name] returns [original] with no mutant active, and each variant's value with its mutant active. */
        fun expect(name: String, args: List<Any?>, original: Any?, vararg variants: Pair<String, Any?>) {
            Mutants.active = 0
            assertEquals(original, call(name, args), "$name with no mutant active")
            val own = mutantsOf(name)
            assertEquals(variants.map { it.first }.sorted(), own.map { it.variant }.sorted(), "$name variants")
            for ((variant, expected) in variants) {
                Mutants.active = own.single { it.variant == variant }.id
                assertEquals(expected, call(name, args), "$name with $variant active")
            }
            Mutants.active = 0
        }
    }

    private data class ManifestMutant(
        val id: Long, val operator: String, val variant: String, val declaration: String, val text: String, val initScope: Boolean,
        val callee: String?,
    )

    private fun compile(source: String, vararg operators: String): Compiled {
        val dir = Files.createTempDirectory("mutation-plugin").toFile()
        val file = File(dir, "Sample.kt").apply { writeText("package sample\n\n" + source.trimIndent()) }
        val out = File(dir, "classes")
        val manifests = File(dir, "manifests")
        val messages = ByteArrayOutputStream()
        val args = listOf(
            file.absolutePath,
            "-d", out.absolutePath,
            "-classpath", System.getProperty("java.class.path"),
            "-no-stdlib",
            "-jvm-target", "11",
            "-Xplugin=" + System.getProperty("mutation.plugin.classpath").split(File.pathSeparator).filter { File(it).exists() }.joinToString(","),
            "-P", "plugin:$PLUGIN_ID:manifestDir=${manifests.absolutePath}",
        ) + operators.flatMap { listOf("-P", "plugin:$PLUGIN_ID:operator=$it") }
        val exit = K2JVMCompiler().exec(PrintStream(messages), *args.toTypedArray())
        assertEquals(ExitCode.OK, exit, messages.toString())
        val manifest = manifests.listFiles()!!.single().readText()
        val string = "\"((?:[^\"\\\\]|\\\\.)*)\""
        val mutants = Regex(
            "\\{\"id\":(\\d+),\"line\":\\d+,\"operator\":$string,\"variant\":$string,\"declaration\":$string,\"text\":$string,\"initScope\":(true|false)(?:,\"callee\":$string)?}",
        ).findAll(manifest).map {
            fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
            ManifestMutant(
                it.groupValues[1].toLong(), it.groupValues[2], unescape(it.groupValues[3]), unescape(it.groupValues[4]),
                unescape(it.groupValues[5]), it.groupValues[6].toBoolean(), it.groupValues[7].ifEmpty { null },
            )
        }.toList()
        return Compiled(URLClassLoader(arrayOf(out.toURI().toURL()), javaClass.classLoader), mutants)
    }
}
