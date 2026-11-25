package dev.isaacudy.udytils.mutation.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Writes the launch settings of one JVM test task, so the runner can start the tests outside Gradle. */
abstract class WriteTestSpecTask : DefaultTask() {
    @get:Input abstract val projectPath: Property<String>
    @get:Input abstract val testTask: Property<String>
    @get:Classpath abstract val classpath: ConfigurableFileCollection
    @get:Classpath abstract val testClassesDirs: ConfigurableFileCollection
    @get:Input abstract val systemProperties: MapProperty<String, String>
    @get:Input abstract val jvmArgs: ListProperty<String>
    @get:Input abstract val environment: MapProperty<String, String>
    @get:Input abstract val workingDirectory: Property<String>
    @get:Input abstract val javaExecutable: Property<String>
    @get:OutputFile abstract val specFile: RegularFileProperty

    @TaskAction
    fun write() {
        val spec = TestSpec(
            projectPath = projectPath.get(),
            testTask = testTask.get(),
            classpath = classpath.files.map { it.absolutePath },
            testClassesDirs = testClassesDirs.files.map { it.absolutePath },
            systemProperties = systemProperties.get(),
            jvmArgs = jvmArgs.get(),
            environment = environment.get(),
            workingDirectory = workingDirectory.get(),
            javaExecutable = javaExecutable.get(),
        )
        specFile.get().asFile.writeText(Json.encodeToString(TestSpec.serializer(), spec))
    }
}

@Serializable
internal data class TestSpec(
    val projectPath: String,
    val testTask: String,
    val classpath: List<String>,
    val testClassesDirs: List<String>,
    val systemProperties: Map<String, String>,
    val jvmArgs: List<String>,
    val environment: Map<String, String>,
    val workingDirectory: String,
    val javaExecutable: String,
)
