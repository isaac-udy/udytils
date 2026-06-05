package dev.isaacudy.udytils.postgres.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.util.Properties
import javax.inject.Inject

/**
 * Runs one codegen engine entry point against an embedded Postgres. Writes the
 * engine's config to a `.properties` file, then forks a JVM with the engine on
 * the classpath (Zonky's ~30 MB binaries stay on this detached classpath and
 * never touch the production or buildscript classpath).
 *
 * Configuration-cache safe: no `Project` access in the action — execution goes
 * through the injected [ExecOperations], and every input is a resolved
 * provider value.
 */
@DisableCachingByDefault(because = "Boots an embedded Postgres; output is cheap to regenerate and incremental up-to-date checks already cover it.")
abstract class PostgresCodegenTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationsDir: DirectoryProperty

    @get:Input
    abstract val schemaName: Property<String>

    @get:Input
    abstract val excludedTables: SetProperty<String>

    @get:Input
    abstract val columnTypesPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val outputPackage: Property<String>

    @get:Input
    abstract val tableSuffix: Property<String>

    @get:Input
    abstract val rowSuffix: Property<String>

    @get:Input
    abstract val rowNameOverrides: MapProperty<String, String>

    @get:Input
    abstract val sqlTypeOverrides: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val schemaBanner: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:Classpath
    abstract val engineClasspath: ConfigurableFileCollection

    @get:OutputFile
    @get:Optional
    abstract val schemaSnapshotFile: RegularFileProperty

    @get:OutputDirectory
    @get:Optional
    abstract val generatedSourcesDir: DirectoryProperty

    /** Transport file for engine config; not an output worth tracking. */
    @get:Internal
    abstract val propsFile: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun run() {
        val props = Properties()
        props["migrationsDir"] = migrationsDir.get().asFile.absolutePath
        props["schemaName"] = schemaName.get()
        props["excludedTables"] = excludedTables.get().joinToString(",")
        props["columnTypesPackage"] = columnTypesPackage.get()
        props["tableSuffix"] = tableSuffix.get()
        props["rowSuffix"] = rowSuffix.get()
        if (schemaBanner.isPresent) props["schemaBanner"] = schemaBanner.get()
        if (outputPackage.isPresent) props["outputPackage"] = outputPackage.get()
        if (schemaSnapshotFile.isPresent) props["schemaSnapshotFile"] = schemaSnapshotFile.get().asFile.absolutePath
        if (generatedSourcesDir.isPresent) props["generatedSourcesDir"] = generatedSourcesDir.get().asFile.absolutePath
        rowNameOverrides.get().forEach { (k, v) -> props["rowNameOverride.$k"] = v }
        sqlTypeOverrides.get().forEach { (k, v) -> props["sqlTypeOverride.$k"] = v }

        val pf = propsFile.get().asFile
        pf.parentFile?.mkdirs()
        pf.outputStream().use { props.store(it, "udytils postgres codegen") }

        execOps.javaexec { spec ->
            spec.classpath = engineClasspath
            spec.mainClass.set(mainClass)
            spec.args(pf.absolutePath)
        }
    }
}
