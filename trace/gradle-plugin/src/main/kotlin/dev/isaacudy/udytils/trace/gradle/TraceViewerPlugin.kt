package dev.isaacudy.udytils.trace.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Runs a local Jaeger v2 for viewing traces, without Docker. Apply to the root project.
 *
 * - `traceViewerStart` downloads the Jaeger release for this machine into the Gradle user home,
 *   checks the binary against the release's SHA-256 sums, and starts Jaeger in the background:
 *   the UI on http://localhost:16686 and OTLP/HTTP on http://localhost:4318.
 * - `traceViewerStop` stops the Jaeger process that `traceViewerStart` started.
 * - `traceViewerOpen` opens the Jaeger UI in the browser.
 *
 * Jaeger stores traces in memory, so stopping Jaeger discards them.
 */
class TraceViewerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("traceViewer", TraceViewerExtension::class.java)
        extension.version.convention("2.22.0")
        extension.installDirectory.convention(project.layout.dir(project.provider { File(project.gradle.gradleUserHomeDir, "caches/udytils-trace/jaeger") }))
        val pidFile = project.layout.buildDirectory.file("trace-viewer/jaeger.pid")
        val logFile = project.layout.buildDirectory.file("trace-viewer/jaeger.log")
        project.tasks.register("traceViewerStart", TraceViewerStartTask::class.java) { task ->
            task.group = "trace viewer"
            task.description = "Downloads and starts a local Jaeger: the UI on port 16686, OTLP/HTTP on port 4318."
            task.version.set(extension.version)
            task.installDirectory.set(extension.installDirectory)
            task.pidFile.set(pidFile)
            task.logFile.set(logFile)
        }
        project.tasks.register("traceViewerStop", TraceViewerStopTask::class.java) { task ->
            task.group = "trace viewer"
            task.description = "Stops the Jaeger that traceViewerStart started."
            task.pidFile.set(pidFile)
        }
        project.tasks.register("traceViewerOpen", TraceViewerOpenTask::class.java) { task ->
            task.group = "trace viewer"
            task.description = "Opens the Jaeger UI in the browser."
        }
    }
}

abstract class TraceViewerExtension {
    abstract val version: Property<String>
    abstract val installDirectory: DirectoryProperty
}

const val TRACE_VIEWER_UI = "http://localhost:16686"

@DisableCachingByDefault(because = "Starts a process")
abstract class TraceViewerStartTask : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Internal abstract val installDirectory: DirectoryProperty
    @get:Internal abstract val pidFile: RegularFileProperty
    @get:Internal abstract val logFile: RegularFileProperty

    @TaskAction
    fun start() {
        runningPid(pidFile.get().asFile)?.let {
            logger.lifecycle("Jaeger is running (pid $it): $TRACE_VIEWER_UI")
            return
        }
        val binary = install()
        val log = logFile.get().asFile.apply { parentFile.mkdirs() }
        val process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        pidFile.get().asFile.writeText(process.pid().toString())
        repeat(60) {
            if (!process.isAlive) throw GradleException("Jaeger exited with code ${process.exitValue()}. Log: $log")
            if (responds(TRACE_VIEWER_UI)) {
                logger.lifecycle("Jaeger started (pid ${process.pid()}): UI $TRACE_VIEWER_UI, OTLP/HTTP http://localhost:4318/v1/traces")
                return
            }
            Thread.sleep(250)
        }
        throw GradleException("Jaeger did not answer on $TRACE_VIEWER_UI within 15 seconds. Log: $log")
    }

    private fun install(): File {
        val name = "jaeger-${version.get()}-${platform()}"
        val directory = installDirectory.get().asFile.resolve(version.get())
        val binary = directory.resolve("$name/jaeger" + if (isWindows()) ".exe" else "")
        if (binary.isFile) return binary
        directory.mkdirs()
        val base = "https://github.com/jaegertracing/jaeger/releases/download/v${version.get()}"
        val archive = directory.resolve("$name.tar.gz")
        logger.lifecycle("Downloading $base/$name.tar.gz")
        download("$base/$name.tar.gz", archive)
        val sums = directory.resolve("$name.sha256sum.txt")
        download("$base/$name.sha256sum.txt", sums)
        val tar = ProcessBuilder("tar", "xzf", archive.absolutePath, "-C", directory.absolutePath).inheritIO().start()
        if (tar.waitFor() != 0) throw GradleException("tar could not extract $archive")
        val expected = sums.readLines().firstOrNull { it.trim().endsWith("$name/${binary.name}") }?.substringBefore(' ')
        val actual = MessageDigest.getInstance("SHA-256").digest(binary.readBytes()).joinToString("") { "%02x".format(it) }
        if (expected != actual) {
            directory.deleteRecursively()
            throw GradleException("The Jaeger binary's SHA-256 $actual does not match the release's $expected. The download was deleted; run traceViewerStart again.")
        }
        binary.setExecutable(true)
        archive.delete()
        return binary
    }

    private fun platform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osName = when {
            "mac" in os -> "darwin"
            "linux" in os -> "linux"
            "windows" in os -> "windows"
            else -> throw GradleException("Jaeger publishes no release for $os")
        }
        val archName = if (arch == "aarch64" || arch == "arm64") "arm64" else "amd64"
        return "$osName-$archName"
    }

    private fun isWindows() = "windows" in System.getProperty("os.name").lowercase()

    private fun download(url: String, target: File) {
        URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    }
}

@DisableCachingByDefault(because = "Stops a process")
abstract class TraceViewerStopTask : DefaultTask() {
    @get:Internal abstract val pidFile: RegularFileProperty

    @TaskAction
    fun stop() {
        val file = pidFile.get().asFile
        val pid = runningPid(file)
        if (pid == null) {
            logger.lifecycle("No Jaeger started by traceViewerStart is running.")
        } else {
            ProcessHandle.of(pid).ifPresent { it.destroy() }
            logger.lifecycle("Stopped Jaeger (pid $pid).")
        }
        file.delete()
    }
}

@DisableCachingByDefault(because = "Opens a browser")
abstract class TraceViewerOpenTask : DefaultTask() {
    @TaskAction
    fun open() {
        if (!responds(TRACE_VIEWER_UI)) throw GradleException("Jaeger is not running on $TRACE_VIEWER_UI. Run traceViewerStart first.")
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            "mac" in os -> listOf("open", TRACE_VIEWER_UI)
            "windows" in os -> listOf("cmd", "/c", "start", TRACE_VIEWER_UI)
            else -> listOf("xdg-open", TRACE_VIEWER_UI)
        }
        ProcessBuilder(command).start()
        logger.lifecycle("Opened $TRACE_VIEWER_UI")
    }
}

private fun runningPid(pidFile: File): Long? {
    val pid = pidFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: return null
    return pid.takeIf { ProcessHandle.of(it).map { handle -> handle.isAlive }.orElse(false) }
}

private fun responds(url: String): Boolean = runCatching {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 500
    connection.readTimeout = 500
    connection.responseCode in 200..399
}.getOrDefault(false)
