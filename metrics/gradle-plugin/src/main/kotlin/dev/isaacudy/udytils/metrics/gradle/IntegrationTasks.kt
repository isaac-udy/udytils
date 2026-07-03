package dev.isaacudy.udytils.metrics.gradle

import dev.isaacudy.udytils.metrics.IntegrationOutput
import dev.isaacudy.udytils.metrics.MetricFinding
import dev.isaacudy.udytils.metrics.MetricRecord
import dev.isaacudy.udytils.metrics.metricsJson
import kotlinx.serialization.Serializable
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Base for the built-in integrations: each writes one [IntegrationOutput] JSON file. */
abstract class IntegrationTask : DefaultTask() {
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    protected fun write(records: List<MetricRecord>, findings: List<MetricFinding>) {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(metricsJson.encodeToString(IntegrationOutput.serializer(), IntegrationOutput(records, findings)))
    }

    /** Module directories: any directory containing `build.gradle.kts`, excluding [excluded] roots. */
    protected fun modules(excluded: Set<String>): List<Pair<String, File>> {
        val root = repoRoot.get().asFile
        val result = mutableListOf<Pair<String, File>>()
        fun walk(dir: File) {
            if (dir.name in setOf("build", "src", ".git", ".gradle")) return
            if (dir != root && dir.name in excluded) return
            if (dir.resolve("build.gradle.kts").isFile) {
                // Gradle-style module notation: ":" for the root project, ":app:client:web", …
                result += (":" + dir.relativeTo(root).path.replace('/', ':')) to dir
            }
            dir.listFiles()?.filter { it.isDirectory }?.forEach { walk(it) }
        }
        walk(root)
        return result
    }
}

/** Default roots that aren't this project's own code. */
internal val DEFAULT_EXCLUDES = listOf("build-logic", "embedded-enro", "embedded-udytils")

abstract class LinesOfCodeTask : IntegrationTask() {
    /** Directory names skipped entirely (composite builds, build logic). */
    @get:Input
    abstract val excludes: ListProperty<String>

    init {
        excludes.convention(DEFAULT_EXCLUDES)
    }

    @TaskAction
    fun run() {
        val records = modules(excludes.get().toSet()).mapNotNull { (name, dir) ->
            val lines = dir.resolve("src").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sumOf { file -> file.useLines { it.count() } }
            if (lines == 0) null else MetricRecord("loc", name, "loc.kotlin", lines.toDouble())
        }
        write(records + MetricRecord("loc", "project", "loc.kotlin", records.sumOf { it.value }), emptyList())
    }
}

abstract class ReadmeHealthTask : IntegrationTask() {
    /** Frontmatter keys every module README must declare; empty disables the check. */
    @get:Input
    abstract val requiredFrontmatter: ListProperty<String>

    /** Days since the last commit touching a README before it counts as stale; 0 disables. */
    @get:Input
    abstract val staleAfterDays: Property<Int>

    @get:Input
    abstract val excludes: ListProperty<String>

    init {
        requiredFrontmatter.convention(emptyList())
        staleAfterDays.convention(0)
        excludes.convention(DEFAULT_EXCLUDES)
    }

    @TaskAction
    fun run() {
        val records = mutableListOf<MetricRecord>()
        val findings = mutableListOf<MetricFinding>()
        val root = repoRoot.get().asFile
        modules(excludes.get().toSet()).forEach { (name, dir) ->
            val readme = dir.resolve("README.md")
            records += MetricRecord("readme", name, "readme.exists", if (readme.isFile) 1.0 else 0.0)
            if (!readme.isFile) {
                findings += MetricFinding("readme", name, "module has no README.md")
                return@forEach
            }
            val required = requiredFrontmatter.get()
            if (required.isNotEmpty()) {
                val frontmatter = readme.readText().let { text ->
                    if (!text.startsWith("---")) "" else text.removePrefix("---").substringBefore("---")
                }
                required.filterNot { key -> frontmatter.lineSequence().any { it.trim().startsWith("$key:") } }
                    .forEach { findings += MetricFinding("readme", name, "README.md is missing frontmatter key `$it`") }
            }
            val lastCommitEpoch = git(root, "log", "-1", "--format=%ct", "--", readme.relativeTo(root).path)
                .toLongOrNull() ?: return@forEach
            val staleDays = (System.currentTimeMillis() / 1000 - lastCommitEpoch) / 86_400
            records += MetricRecord("readme", name, "readme.stale.days", staleDays.toDouble())
            val threshold = staleAfterDays.get()
            if (threshold > 0 && staleDays > threshold) {
                findings += MetricFinding("readme", name, "README.md last updated $staleDays days ago (threshold $threshold)")
            }
        }
        write(records, findings)
    }
}

abstract class BuildWarningsTask : IntegrationTask() {
    /** A captured build log; when absent, the integration contributes nothing. */
    @get:Internal
    abstract val logFile: RegularFileProperty

    @TaskAction
    fun run() {
        val log = logFile.get().asFile
        if (!log.isFile) {
            write(listOf(MetricRecord("build", "project", "warnings.count", 0.0)), emptyList())
            return
        }
        val root = repoRoot.get().asFile.path
        val warnings = log.readLines()
            .filter { it.startsWith("w: ") || it.contains(" warning: ") }
            .distinct()
        val perModule = warnings.groupingBy { line ->
            val rel = Regex("""file://(\S+?\.kts?)""").find(line)
                ?.groupValues?.get(1)
                ?.removePrefix(root)?.trimStart('/')
            val moduleDir = when {
                rel == null -> null
                rel.contains("/src/") -> rel.substringBefore("/src/")
                else -> rel.substringBeforeLast('/', "")
            }
            if (moduleDir == null) "project" else ":" + moduleDir.replace('/', ':')
        }.eachCount()
        val records = perModule.map { (module, count) ->
            MetricRecord("build", module, "warnings.count", count.toDouble())
        } + MetricRecord("build", "project", "warnings.total", warnings.size.toDouble())
        val findings = warnings.take(200).map { MetricFinding("build", "project", it.removePrefix("w: ").take(400)) }
        write(records, findings)
    }
}

abstract class ArchitectureAdapterTask : IntegrationTask() {
    @get:Internal
    abstract val summaryFile: RegularFileProperty

    @Serializable
    private data class Summary(
        val rules: List<RuleEntry> = emptyList(),
        val census: List<CensusEntry> = emptyList(),
        val exceptions: List<ExceptionEntry> = emptyList(),
        val audits: List<AuditEntry> = emptyList(),
    )

    @Serializable
    private data class RuleEntry(val id: String, val tag: String)

    @Serializable
    private data class CensusEntry(val construct: String, val module: String, val count: Int)

    @Serializable
    private data class ExceptionEntry(val ruleId: String, val module: String, val where: String)

    @Serializable
    private data class AuditEntry(val ruleId: String, val module: String, val where: String, val message: String)

    @TaskAction
    fun run() {
        val summary = metricsJson.decodeFromString(Summary.serializer(), summaryFile.get().asFile.readText())
        val records = buildList {
            summary.rules.groupingBy { it.tag }.eachCount().forEach { (tag, count) ->
                add(MetricRecord("architecture", "project", "rules.count", count.toDouble(), mapOf("tag" to tag)))
            }
            summary.census.forEach {
                add(MetricRecord("architecture", it.module, "census.count", it.count.toDouble(), mapOf("construct" to it.construct)))
            }
            add(MetricRecord("architecture", "project", "exceptions.total", summary.exceptions.size.toDouble()))
            summary.exceptions.groupingBy { it.module }.eachCount().forEach { (module, count) ->
                add(MetricRecord("architecture", module, "exceptions.count", count.toDouble()))
            }
            add(MetricRecord("architecture", "project", "audit.findings.total", summary.audits.size.toDouble()))
            summary.audits.groupingBy { it.ruleId }.eachCount().forEach { (ruleId, count) ->
                add(MetricRecord("architecture", "project", "audit.findings.count", count.toDouble(), mapOf("ruleId" to ruleId)))
            }
        }
        val findings = summary.exceptions.map {
            MetricFinding("architecture", it.module, "exception: ${it.where}", mapOf("ruleId" to it.ruleId))
        } + summary.audits.map {
            MetricFinding("architecture", it.module, "audit: ${it.message}", mapOf("ruleId" to it.ruleId))
        }
        write(records, findings)
    }
}

internal fun git(workDir: File, vararg args: String): String {
    val process = ProcessBuilder(listOf("git") + args).directory(workDir).start()
    process.outputStream.close()
    val out = process.inputStream.bufferedReader().readText().trim()
    process.errorStream.bufferedReader().readText()
    process.waitFor()
    return out
}
