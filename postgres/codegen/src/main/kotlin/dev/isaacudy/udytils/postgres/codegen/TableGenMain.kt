package dev.isaacudy.udytils.postgres.codegen

import java.io.File

/**
 * Generates Exposed `Table` objects (with `Row` data classes + mappers) from
 * the live schema produced by applying the Flyway migrations to an ephemeral
 * embedded Postgres. One Kotlin file per table.
 *
 * Single argument: the path to the `.properties` file written by the Gradle
 * plugin (see [CodegenConfig]).
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: TableGenMain <config.properties>" }
    val config = CodegenConfig.load(File(args[0]))
    val sourcesRoot = requireNotNull(config.generatedSourcesDir) {
        "generatedSourcesDir is not configured"
    }
    val outputPackage = requireNotNull(config.outputPackage) {
        "outputPackage is required — set postgresCodegen { outputPackage.set(\"...\") }"
    }

    val nameMapper = NameMapper(config.tableSuffix, config.rowSuffix, config.rowNameOverrides)
    val typeRegistry = TypeRegistry(config.columnTypesPackage, config.sqlTypeOverrides)

    withMigratedDatabase(config.migrationsDir, "generatePostgresTables") { conn ->
        val tables = TableIntrospector(conn, config.schemaName, config.excludedTables).readTables()

        // Write into the package sub-directory of the source root. Clear that
        // directory each run so removed tables don't leave stale files behind.
        val packageDir = File(sourcesRoot, outputPackage.replace('.', '/'))
        if (packageDir.exists()) packageDir.listFiles()?.forEach { it.delete() }
        packageDir.mkdirs()

        for (table in tables) {
            val writer = TableSourceWriter(
                table = table,
                nameMapper = nameMapper,
                typeRegistry = typeRegistry,
                outputPackage = outputPackage,
                banner = config.schemaBanner,
            )
            File(packageDir, "${writer.fileName}.kt").writeText(writer.render())
        }
        println("[generatePostgresTables] wrote ${tables.size} table files to ${packageDir.absolutePath}")
    }
}
