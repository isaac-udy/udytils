package dev.isaacudy.udytils.postgres.codegen

import java.io.File

/**
 * Applies the Flyway migrations to an ephemeral embedded Postgres and writes a
 * normalised schema snapshot to the configured file. Fails the build if any
 * migration fails to apply — that's the validation hook.
 *
 * Single argument: the path to the `.properties` file written by the Gradle
 * plugin (see [CodegenConfig]).
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: SchemaExportMain <config.properties>" }
    val config = CodegenConfig.load(File(args[0]))
    val output = requireNotNull(config.schemaSnapshotFile) {
        "schemaSnapshotFile is not configured"
    }

    withMigratedDatabase(config.migrationsDir, "exportPostgresSchema") { conn ->
        val snapshot = SchemaExporter(
            conn = conn,
            schemaName = config.schemaName,
            excludedTables = config.excludedTables,
            banner = config.schemaBanner,
        ).export()
        output.parentFile?.mkdirs()
        output.writeText(snapshot)
        println("[exportPostgresSchema] wrote ${output.absolutePath} (${snapshot.length} bytes)")
    }
}
