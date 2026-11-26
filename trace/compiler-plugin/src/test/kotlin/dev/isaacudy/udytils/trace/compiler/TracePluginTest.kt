package dev.isaacudy.udytils.trace.compiler

import dev.isaacudy.udytils.trace.InMemorySpanSink
import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.Selector
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TracePluginTest {
    private val sink = InMemorySpanSink()
    private lateinit var registration: AutoCloseable

    @BeforeTest
    fun addSink() {
        registration = Trace.addSink(sink)
    }

    @AfterTest
    fun removeSink() {
        registration.close()
    }

    @Test
    fun `a traced function reports one span and keeps its result`() {
        val compiled = compile(
            """
            fun double(n: Int): Int = n * 2
            fun outer(): Int { return double(2) + 1 }
            """,
        )
        assertEquals(5, compiled.call("outer"))
        val (inner, outer) = sink.spans
        assertEquals("sample.double", inner.site.name)
        assertSame(outer, inner.parent)
        assertEquals(Outcome.Returned, outer.outcome)
        assertNull(Trace.currentSpan)
    }

    @Test
    fun `a thrown error propagates and marks the span`() {
        val compiled = compile("fun fail(): Int = error(\"boom\")")
        val thrown = runCatching { compiled.call("fail") }.exceptionOrNull()
        assertIs<IllegalStateException>((thrown as InvocationTargetException).targetException)
        assertIs<Outcome.Threw>(sink.spans.single().outcome)
    }

    @Test
    fun `a site with no selecting sink runs unchanged and reports nothing`() {
        registration.close()
        registration = Trace.addSink(InMemorySpanSink { 0 })
        val compiled = compile("fun answer(): Int = 42")
        assertEquals(42, compiled.call("answer"))
        assertTrue(sink.spans.isEmpty())
    }

    @Test
    fun `an override and a fun interface lambda share the interface method's declaration ID`() {
        val compiled = compile(
            """
            fun interface Greet { fun invoke(name: String): String }
            class GreetImpl : Greet { override fun invoke(name: String): String = "hi " + name }
            class Repository { val greet = Greet { name -> "hello " + name } }
            fun viaOverride(): String = GreetImpl().invoke("a")
            fun viaLambda(): String = Repository().greet.invoke("b")
            """,
        )
        assertEquals("hi a", compiled.call("viaOverride"))
        assertEquals("hello b", compiled.call("viaLambda"))
        val declarationId = stableHash("sample.Greet.invoke(kotlin.String)")
        val bySite = compiled.sites.associateBy { it.name }
        assertEquals("override", bySite.getValue("sample.GreetImpl.invoke").kind)
        assertEquals("sam", bySite.getValue("sample.Repository.greet").kind)
        assertEquals(declarationId, bySite.getValue("sample.GreetImpl.invoke").declarationId)
        assertEquals(declarationId, bySite.getValue("sample.Repository.greet").declarationId)
        assertTrue(bySite.getValue("sample.GreetImpl.invoke").siteId != bySite.getValue("sample.Repository.greet").siteId)
        val lambdaSpan = sink.spans.single { it.site.name == "sample.Repository.greet" }
        assertEquals("sample.viaLambda", lambdaSpan.parent?.site?.name)
    }

    @Test
    fun `excluded declarations get no site`() {
        val compiled = compile(
            """
            import dev.isaacudy.udytils.trace.NotTraced
            private fun hidden() = 1
            inline fun inlined() = 2
            @NotTraced fun optedOut() = 3
            class Box(val value: Int) {
                val doubled: Int get() = value * 2
                override fun toString(): String = "Box"
            }
            private class Secret { fun reveal() = 4 }
            fun visible() = hidden() + inlined() + optedOut()
            """,
        )
        assertEquals(listOf("sample.visible"), compiled.sites.map { it.name })
    }

    @Test
    fun `a suspend function's span ends when the function completes after resuming`() {
        val compiled = compile(
            """
            import dev.isaacudy.udytils.trace.NotTraced
            import kotlin.coroutines.Continuation
            import kotlin.coroutines.suspendCoroutine
            var pending: Continuation<Int>? = null
            suspend fun waitForValue(): Int = suspendCoroutine { pending = it }
            @NotTraced fun resume(value: Int) { pending!!.resumeWith(Result.success(value)) }
            """,
        )
        var result: Int? = null
        val waitForValue = compiled.type.methods.single { it.name == "waitForValue" }
        val block: suspend () -> Int = { @Suppress("UNCHECKED_CAST") (suspendInvoke(waitForValue) as Int) }
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it.getOrThrow() })
        assertTrue(sink.spans.none { it.site.name == "sample.waitForValue" }, "the span is still open while suspended")
        compiled.call("resume", 7)
        assertEquals(7, result)
        assertEquals(Outcome.Returned, sink.spans.single { it.site.name == "sample.waitForValue" }.outcome)
    }

    @Test
    fun `a call made while another traced function is suspended starts a new trace`() {
        val compiled = compile(
            """
            import kotlin.coroutines.Continuation
            import kotlin.coroutines.suspendCoroutine
            var pending: Continuation<Int>? = null
            suspend fun waitForValue(): Int = suspendCoroutine { pending = it }
            fun onClick(): Int = 1
            """,
        )
        val waitForValue = compiled.type.methods.single { it.name == "waitForValue" }
        val block: suspend () -> Any? = { suspendInvoke(waitForValue) }
        block.startCoroutine(Continuation(EmptyCoroutineContext) {})
        compiled.call("onClick")
        assertNull(sink.spans.single { it.site.name == "sample.onClick" }.parent)
        assertNull(Trace.currentSpan)
    }

    @Test
    fun `declarationIdOf is rewritten in files outside the traced packages and in property initializers`() {
        val compiled = compile(
            """
            import dev.isaacudy.udytils.trace.declarationIdOf
            import dev.isaacudy.udytils.trace.rules.on
            fun interface Greet { fun invoke(name: String): String }
            val greetId: Long = declarationIdOf(Greet::invoke)
            fun greetIdFromBody(): Long = declarationIdOf(Greet::invoke)
            val greetSelector = on(Greet::invoke)
            """,
            include = "other",
        )
        val declarationId = stableHash("sample.Greet.invoke(kotlin.String)")
        assertEquals(declarationId, compiled.call("getGreetId"))
        assertEquals(declarationId, compiled.call("greetIdFromBody"))
        assertEquals(declarationId, (compiled.call("getGreetSelector") as Selector.Declaration).declarationId)
        assertTrue(compiled.sites.isEmpty())
    }

    @Test
    fun `declarationIdOf in another compilation matches the manifest ID of a declaration with nullable, generic and function parameters`() {
        val library = compile(
            """
            fun interface Lookup { fun find(key: String?, items: List<Int>, transform: (Int) -> String): String }
            class Catalog { val lookup = Lookup { key, items, transform -> key + items.map(transform) } }
            """,
        )
        val consumer = compile(
            """
            import dev.isaacudy.udytils.trace.declarationIdOf
            fun lookupId(): Long = declarationIdOf(Lookup::find)
            """,
            include = "other",
            fileName = "Consumer.kt",
            classpath = listOf(library.classes),
        )
        assertEquals(library.sites.single { it.kind == "sam" }.declarationId, consumer.call("lookupId"))
    }

    private class Compiled(val type: Class<*>, val sites: List<ManifestSite>, val classes: File) {
        fun call(name: String, vararg args: Any?): Any? =
            type.methods.single { it.name == name }.invoke(null, *args)
    }

    private data class ManifestSite(val siteId: Long, val declarationId: Long, val name: String, val kind: String)

    private fun compile(source: String, include: String? = null, fileName: String = "Sample.kt", classpath: List<File> = emptyList()): Compiled {
        val dir = Files.createTempDirectory("trace-plugin").toFile()
        val file = File(dir, fileName).apply { writeText("package sample\n\n" + source.trimIndent()) }
        val out = File(dir, "classes")
        val manifests = File(dir, "manifests")
        val messages = ByteArrayOutputStream()
        val args = listOf(
            file.absolutePath,
            "-d", out.absolutePath,
            "-classpath", (classpath.map { it.absolutePath } + System.getProperty("java.class.path")).joinToString(File.pathSeparator),
            "-no-stdlib",
            "-jvm-target", "11",
            "-Xplugin=" + System.getProperty("trace.plugin.classpath").split(File.pathSeparator).filter { File(it).exists() }.joinToString(","),
            "-P", "plugin:$PLUGIN_ID:manifestDir=${manifests.absolutePath}",
        ) + listOfNotNull(include).flatMap { listOf("-P", "plugin:$PLUGIN_ID:include=$it") }
        val exit = K2JVMCompiler().exec(PrintStream(messages), *args.toTypedArray())
        assertEquals(ExitCode.OK, exit, messages.toString())
        val manifest = manifests.listFiles { file -> file.extension == "json" }?.singleOrNull()?.readText().orEmpty()
        val sites = Regex("\\{\"siteId\":(\\d+),\"declarationId\":(\\d+),\"name\":\"([^\"]*)\",\"declaration\":\"[^\"]*\",\"kind\":\"(\\w+)\"")
            .findAll(manifest)
            .map { ManifestSite(it.groupValues[1].toLong(), it.groupValues[2].toLong(), it.groupValues[3], it.groupValues[4]) }
            .toList()
        val loader = URLClassLoader((listOf(out) + classpath).map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader)
        return Compiled(loader.loadClass("sample." + fileName.removeSuffix(".kt") + "Kt"), sites, out)
    }
}

/** Calls a compiled `suspend fun` with the caller's continuation. */
private suspend fun suspendInvoke(method: java.lang.reflect.Method): Any? =
    kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation -> method.invoke(null, continuation) }
