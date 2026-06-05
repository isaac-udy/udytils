package dev.isaacudy.udytils.postgres.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.util.Properties

/**
 * Wires the udytils Postgres codegen into a Kotlin/JVM (server) module:
 *
 *  * registers `exportPostgresSchema` (writes the committed `schema.sql`) and
 *    `generatePostgresTables` (emits Exposed `Table`/`Row` sources),
 *  * resolves the build-only codegen engine + Zonky binaries onto a detached
 *    configuration (so they never reach the production or buildscript classpath),
 *  * adds the generated sources to the main source set and the runtime
 *    `dev.isaacudy.udytils:postgres` library to `implementation` so they compile,
 *  * hooks both tasks into `compileKotlin` so a plain build regenerates them.
 *
 * Intended for `kotlin("jvm")` modules only (it assumes a `compileKotlin` task
 * and a `main` source set). Apply it to a library module whose jar other
 * modules depend on, so generated symbols are visible across module boundaries.
 */
class PostgresCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("postgresCodegen", PostgresCodegenExtension::class.java)
        val version = bakedVersion()

        ext.migrationsDir.convention(project.layout.projectDirectory.dir("src/main/resources/db/migration"))
        ext.schemaSnapshotFile.convention(project.layout.projectDirectory.file("schema.sql"))
        ext.generatedSourcesDir.convention(project.layout.buildDirectory.dir("generated/source/postgres-tables/kotlin"))
        ext.columnTypesPackage.convention("dev.isaacudy.udytils.postgres")
        ext.schemaName.convention("public")
        ext.excludedTables.convention(setOf("flyway_schema_history"))
        ext.tableSuffix.convention("Table")
        ext.rowSuffix.convention("Row")
        ext.hookIntoCompile.convention(true)
        ext.runtimeDependency.convention(true)
        ext.engineVersion.convention(version)
        ext.zonkyBinaries.convention(DEFAULT_ZONKY_BINARIES)

        // Build-only engine classpath: the codegen engine + Zonky binaries.
        // Not consumable, not on any compile/runtime classpath.
        val engine = project.configurations.create("postgresCodegenEngine") { c ->
            c.isCanBeConsumed = false
            c.isCanBeResolved = true
        }
        engine.dependencies.addLater(
            ext.engineVersion.map { project.dependencies.create("dev.isaacudy.udytils:postgres-codegen:$it") }
        )
        engine.dependencies.addAllLater(
            ext.zonkyBinaries.map { coords -> coords.map { project.dependencies.create(it) } }
        )

        // Generated code imports the column types from the postgres-core runtime.
        project.configurations.getByName("implementation").dependencies.addAllLater(
            ext.runtimeDependency.zip(ext.engineVersion) { enabled, v ->
                if (enabled) listOf(project.dependencies.create("dev.isaacudy.udytils:postgres-core:$v")) else emptyList()
            }
        )

        val export = project.tasks.register("exportPostgresSchema", PostgresCodegenTask::class.java) { t ->
            t.group = "database"
            t.description = "Applies Flyway migrations to an embedded Postgres and writes a normalised schema snapshot."
            t.mainClass.set("dev.isaacudy.udytils.postgres.codegen.SchemaExportMainKt")
            wireCommon(t, ext, engine)
            t.schemaSnapshotFile.set(ext.schemaSnapshotFile)
            t.propsFile.set(project.layout.buildDirectory.file("postgres-codegen/export.properties"))
        }

        val generate = project.tasks.register("generatePostgresTables", PostgresCodegenTask::class.java) { t ->
            t.group = "database"
            t.description = "Generates Exposed Table + Row sources from the migrated schema."
            t.mainClass.set("dev.isaacudy.udytils.postgres.codegen.TableGenMainKt")
            wireCommon(t, ext, engine)
            t.outputPackage.set(ext.outputPackage)
            t.generatedSourcesDir.set(ext.generatedSourcesDir)
            t.propsFile.set(project.layout.buildDirectory.file("postgres-codegen/generate.properties"))
        }

        // Generated sources join the main source set; the Kotlin/JVM plugin
        // compiles .kt files found under the main source set's java srcDirs.
        project.extensions.getByType(SourceSetContainer::class.java)
            .getByName("main").java.srcDir(ext.generatedSourcesDir)

        project.afterEvaluate {
            if (ext.hookIntoCompile.get()) {
                project.tasks.named("compileKotlin").configure { it.dependsOn(export, generate) }
            }
        }
    }

    private fun wireCommon(
        t: PostgresCodegenTask,
        ext: PostgresCodegenExtension,
        engine: org.gradle.api.artifacts.Configuration,
    ) {
        t.migrationsDir.set(ext.migrationsDir)
        t.schemaName.set(ext.schemaName)
        t.excludedTables.set(ext.excludedTables)
        t.columnTypesPackage.set(ext.columnTypesPackage)
        t.tableSuffix.set(ext.tableSuffix)
        t.rowSuffix.set(ext.rowSuffix)
        t.rowNameOverrides.set(ext.rowNameOverrides)
        t.sqlTypeOverrides.set(ext.sqlTypeOverrides)
        t.schemaBanner.set(ext.schemaBanner)
        t.engineClasspath.from(engine)
    }

    private fun bakedVersion(): String {
        val stream = javaClass.getResourceAsStream(VERSION_RESOURCE) ?: return "unspecified"
        return Properties().apply { stream.use { load(it) } }.getProperty("version") ?: "unspecified"
    }

    companion object {
        private const val VERSION_RESOURCE =
            "/dev/isaacudy/udytils/postgres/gradle/version.properties"

        private const val ZONKY_BINARIES_VERSION = "16.4.0"

        private val DEFAULT_ZONKY_BINARIES = listOf(
            "io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8:$ZONKY_BINARIES_VERSION",
            "io.zonky.test.postgres:embedded-postgres-binaries-darwin-amd64:$ZONKY_BINARIES_VERSION",
            "io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:$ZONKY_BINARIES_VERSION",
            "io.zonky.test.postgres:embedded-postgres-binaries-linux-arm64v8:$ZONKY_BINARIES_VERSION",
        )
    }
}
