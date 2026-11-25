package dev.isaacudy.udytils.mutation.gradle

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A discovered JVM test and its baseline result. */
internal data class TestCase(
    val uid: String,
    val className: String,
    val tags: Set<String>,
    var passed: Boolean = false,
    var millis: Long = 0,
    var hits: Set<Long> = emptySet(),
)

internal data class RunReply(val status: Status, val failingTest: String?, val millis: Long, val failure: String?)

internal class DriverTimeout(message: String) : Exception(message)

/** One JUnit driver JVM, started from a [TestSpec]. The runner restarts the driver after a hang. */
internal class Driver(
    private val spec: TestSpec,
    private val driverClasspath: List<File>,
    private val workDirectory: File,
    private val activeMutant: Long = 0,
) : AutoCloseable {
    private val process: Process
    private val input: BufferedWriter
    private val lines = LinkedBlockingQueue<String>()

    init {
        workDirectory.mkdirs()
        val argFile = File(workDirectory, "driver.args")
        val classpath = (driverClasspath.map { it.absolutePath } + spec.classpath).joinToString(File.pathSeparator)
        val args = spec.jvmArgs + spec.systemProperties.map { (k, v) -> "-D$k=$v" } +
            listOf("-Dudytils.mutant=$activeMutant", "-cp", classpath)
        argFile.writeText(args.joinToString("\n") { quote(it) })
        val log = File(workDirectory, "driver.log")
        process = ProcessBuilder(spec.javaExecutable, "@${argFile.absolutePath}", MAIN_CLASS, log.absolutePath)
            .directory(File(spec.workingDirectory).takeIf { it.isDirectory } ?: workDirectory)
            .redirectErrorStream(true)
            .also { it.environment().putAll(spec.environment) }
            .start()
        input = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        Thread({
            output.useLines { seq -> seq.filter { it.startsWith(PREFIX) }.forEach { lines.put(it.removePrefix(PREFIX)) } }
            lines.put(EOF)
        }, "mutation-driver-reader").apply { isDaemon = true }.start()
    }

    fun discover(roots: List<String>): List<TestCase> =
        command("discover\t${roots.joinToString(File.pathSeparator)}", DISCOVERY_TIMEOUT_MILLIS).map { line ->
            val parts = line.split('\t')
            TestCase(uid = parts[1], className = parts[2], tags = parts[3].split(',').filter(String::isNotBlank).toSet())
        }

    /** Runs [tests] with no mutant active and records each test's result, duration and hits. */
    fun baseline(tests: Map<String, TestCase>, tags: Set<String>, timeoutMillis: Long) {
        for (line in command("baseline\t${tags.joinToString(",")}", timeoutMillis)) {
            val parts = line.split('\t')
            val test = tests[parts[1]] ?: continue
            test.passed = parts[2] == "passed"
            test.millis = parts[3].toLong()
            test.hits = parts.getOrNull(4).orEmpty().split(',').filter(String::isNotBlank).map(String::toLong).toSet()
        }
    }

    /** Runs [tests] (timeout, uid) in order with [mutant] active, stopping at the first failure. */
    fun run(mutant: Long, tests: List<Pair<Long, String>>): RunReply {
        val header = "run\t$mutant\t${tests.size}"
        val body = tests.joinToString("") { (timeout, uid) -> "\n$timeout\t$uid" }
        val deadline = tests.sumOf { it.first } + RUN_GRACE_MILLIS
        val reply = command(header + body, deadline).single()
        val parts = reply.split('\t')
        return RunReply(
            status = when (parts[1]) {
                "killed" -> Status.Killed
                "timed-out" -> Status.TimedOut
                else -> Status.Survived
            },
            failingTest = parts[2].takeIf { it != "-" },
            millis = parts[3].toLong(),
            failure = parts.getOrNull(4)?.takeIf { it != "-" },
        )
    }

    private fun command(text: String, timeoutMillis: Long): List<String> {
        input.write(text)
        input.newLine()
        input.flush()
        val replies = mutableListOf<String>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (true) {
            val remaining = deadline - System.nanoTime()
            val line = lines.poll(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS)
                ?: throw DriverTimeout("The JUnit driver for ${spec.projectPath}:${spec.testTask} sent no reply within $timeoutMillis ms.")
            if (line == EOF) error(
                "The JUnit driver for ${spec.projectPath}:${spec.testTask} exited. Log: ${File(workDirectory, "driver.log")}",
            )
            if (line == "end") return replies
            replies += line
        }
    }

    override fun close() {
        runCatching {
            input.write("exit")
            input.newLine()
            input.flush()
        }
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    fun kill() {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
    }

    private fun quote(arg: String): String =
        if (arg.any { it.isWhitespace() || it == '"' || it == '\\' }) "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else arg

    private companion object {
        const val MAIN_CLASS = "dev.isaacudy.udytils.mutation.driver.JUnitDriver"
        const val PREFIX = "@@mutation "
        const val EOF = "\u0000eof"
        const val DISCOVERY_TIMEOUT_MILLIS = 300_000L
        const val RUN_GRACE_MILLIS = 30_000L
    }
}
