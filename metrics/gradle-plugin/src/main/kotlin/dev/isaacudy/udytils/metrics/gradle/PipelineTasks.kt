package dev.isaacudy.udytils.metrics.gradle

import dev.isaacudy.udytils.metrics.GitBranchStore
import dev.isaacudy.udytils.metrics.IntegrationOutput
import dev.isaacudy.udytils.metrics.MetricsRun
import dev.isaacudy.udytils.metrics.metricsJson
import dev.isaacudy.udytils.metrics.renderMetricsReport
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.TaskAction
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Merges every integration's output into one [MetricsRun] stamped with commit, branch, and time. */
abstract class CollectMetricsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val integrationOutputs: ConfigurableFileCollection

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val runFile: RegularFileProperty

    @TaskAction
    fun run() {
        val root = repoRoot.get().asFile
        val outputs = integrationOutputs.files
            .filter { it.isFile }
            .map { metricsJson.decodeFromString(IntegrationOutput.serializer(), it.readText()) }
        val run = MetricsRun(
            commit = git(root, "rev-parse", "HEAD").ifEmpty { "unknown" },
            branch = git(root, "rev-parse", "--abbrev-ref", "HEAD").ifEmpty { "unknown" },
            timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            records = outputs.flatMap { it.records },
            findings = outputs.flatMap { it.findings },
        )
        val file = runFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(metricsJson.encodeToString(MetricsRun.serializer(), run))
        logger.lifecycle("Collected ${run.records.size} metric(s) and ${run.findings.size} finding(s) for ${run.commit.take(9)}.")
    }
}

/** Appends the collected run to the store branch. The branch is never checked out. */
abstract class PublishMetricsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runFile: RegularFileProperty

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:Input
    abstract val storeBranch: Property<String>

    @TaskAction
    fun run() {
        val run = metricsJson.decodeFromString(MetricsRun.serializer(), runFile.get().asFile.readText())
        GitBranchStore(repoRoot.get().asFile, storeBranch.get()).append(run)
        logger.lifecycle("Published metrics run ${run.commit.take(9)} to branch '${storeBranch.get()}'. Push it with: git push origin ${storeBranch.get()}")
    }
}

/** Renders the stored series (plus the local unpublished run, if any) to a self-contained HTML page. */
abstract class GenerateMetricsReportTask : DefaultTask() {
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:Input
    abstract val storeBranch: Property<String>

    @get:Input
    abstract val reportTitle: Property<String>

    @get:Internal
    abstract val localRunFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun run() {
        val stored = GitBranchStore(repoRoot.get().asFile, storeBranch.get()).readRuns()
        val local = localRunFile.get().asFile.takeIf { it.isFile }
            ?.let { metricsJson.decodeFromString(MetricsRun.serializer(), it.readText()) }
            ?.takeIf { candidate -> stored.none { it.commit == candidate.commit && it.timestamp == candidate.timestamp } }
        val runs = stored + listOfNotNull(local)
        val file = reportFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(renderMetricsReport(runs, reportTitle.get()))
        logger.lifecycle("Metrics report (${runs.size} run(s)): file://${file.absolutePath}")
    }
}
