package dev.isaacudy.udytils.metrics.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import javax.inject.Inject

/**
 * Codebase health metrics for a repository. Apply at the root project:
 *
 * ```
 * metrics {
 *     integrations {
 *         architecture(":platform:common:architecture")
 *         linesOfCode()
 *         readmeHealth { requiredFrontmatter.set(listOf("owner")); staleAfterDays.set(90) }
 *         buildWarnings()
 *         custom("my-metrics", taskPath = ":tools:myMetrics", outputFile = file("tools/build/my-metrics.json"))
 *     }
 * }
 * ```
 *
 * Tasks:
 *  - `collectMetrics` — runs every integration and merges their outputs into one run
 *    (`build/metrics/run.json`), stamped with the current commit, branch, and time.
 *  - `publishMetrics` — appends the run to the store branch (default `metrics`) using git
 *    plumbing; the branch is never checked out. Push it like any other branch.
 *  - `generateMetricsReport` — renders the stored series (plus the local unpublished run, if
 *    any) to `build/metrics/report.html`.
 *
 * An integration is any task that writes an `IntegrationOutput` JSON file (`records` +
 * `findings`); `custom(...)` wires one in. Collection never fails the build on what it finds —
 * metrics are a report, not a gate.
 */
class MetricsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        require(project == project.rootProject) { "The metrics plugin must be applied to the root project." }
        val extension = project.extensions.create("metrics", MetricsExtension::class.java, project)

        val collect = project.tasks.register("collectMetrics", CollectMetricsTask::class.java) { task ->
            task.group = "metrics"
            task.description = "Collects every metrics integration into a single run file."
            task.integrationOutputs.from(extension.integrationOutputs)
            task.repoRoot.set(project.rootDir)
            task.runFile.set(project.layout.buildDirectory.file("metrics/run.json"))
            task.outputs.upToDateWhen { false }
        }
        project.tasks.register("publishMetrics", PublishMetricsTask::class.java) { task ->
            task.group = "metrics"
            task.description = "Appends the collected run to the metrics store branch."
            task.runFile.set(collect.flatMap { it.runFile })
            task.repoRoot.set(project.rootDir)
            task.storeBranch.set(extension.storeBranch)
            task.outputs.upToDateWhen { false }
        }
        project.tasks.register("generateMetricsReport", GenerateMetricsReportTask::class.java) { task ->
            task.group = "metrics"
            task.description = "Renders the metrics series to a self-contained HTML report."
            task.repoRoot.set(project.rootDir)
            task.storeBranch.set(extension.storeBranch)
            task.reportTitle.set(extension.reportTitle)
            task.localRunFile.set(project.layout.buildDirectory.file("metrics/run.json"))
            task.reportFile.set(project.layout.buildDirectory.file("metrics/report.html"))
            task.outputs.upToDateWhen { false }
        }
    }
}

open class MetricsExtension @Inject constructor(private val project: Project) {
    /** The branch runs are appended to. It is never checked out. */
    val storeBranch = project.objects.property(String::class.java).convention("metrics")

    /** The report's title; defaults to the root project name. */
    val reportTitle = project.objects.property(String::class.java).convention("${project.name} health")

    internal val integrationOutputs = project.objects.fileCollection()

    fun integrations(action: Action<Integrations>) = action.execute(Integrations())

    inner class Integrations {

        /** The architecture framework's run summary: rules, per-Construct census, exceptions, audit findings. */
        fun architecture(modulePath: String) {
            val module = project.project(modulePath)
            val task = project.tasks.register("metricsArchitecture", ArchitectureAdapterTask::class.java) { task ->
                task.group = "metrics"
                task.summaryFile.set(module.layout.buildDirectory.file("architecture/metrics.json"))
                task.outputFile.set(project.layout.buildDirectory.file("metrics/integrations/architecture.json"))
                task.dependsOn("$modulePath:architectureMetrics")
            }
            integrationOutputs.from(task.flatMap { it.outputFile })
        }

        /** Kotlin lines of code per module (a directory containing `build.gradle.kts`). */
        fun linesOfCode(configure: Action<LinesOfCodeTask> = Action {}) {
            val task = project.tasks.register("metricsLinesOfCode", LinesOfCodeTask::class.java) { task ->
                task.group = "metrics"
                task.repoRoot.set(project.rootDir)
                task.outputFile.set(project.layout.buildDirectory.file("metrics/integrations/loc.json"))
                configure.execute(task)
            }
            integrationOutputs.from(task.flatMap { it.outputFile })
        }

        /** Module README presence, required frontmatter keys, and staleness (via git history). */
        fun readmeHealth(configure: Action<ReadmeHealthTask> = Action {}) {
            val task = project.tasks.register("metricsReadmeHealth", ReadmeHealthTask::class.java) { task ->
                task.group = "metrics"
                task.repoRoot.set(project.rootDir)
                task.outputFile.set(project.layout.buildDirectory.file("metrics/integrations/readme.json"))
                configure.execute(task)
            }
            integrationOutputs.from(task.flatMap { it.outputFile })
        }

        /**
         * Compiler warnings parsed from a captured build log (`./gradlew build 2>&1 | tee <file>`).
         * Contributes nothing when the log file doesn't exist, so it is safe to configure
         * unconditionally and only produce the log in CI.
         */
        fun buildWarnings(logFile: Any = project.layout.buildDirectory.file("metrics/build.log")) {
            val task = project.tasks.register("metricsBuildWarnings", BuildWarningsTask::class.java) { task ->
                task.group = "metrics"
                task.repoRoot.set(project.rootDir)
                task.logFile.set(project.file(logFile))
                task.outputFile.set(project.layout.buildDirectory.file("metrics/integrations/build-warnings.json"))
            }
            integrationOutputs.from(task.flatMap { it.outputFile })
        }

        /** Any task that writes an `IntegrationOutput` JSON file. */
        fun custom(name: String, taskPath: String, outputFile: Any) {
            integrationOutputs.from(project.files(outputFile).builtBy(taskPath))
        }
    }
}
