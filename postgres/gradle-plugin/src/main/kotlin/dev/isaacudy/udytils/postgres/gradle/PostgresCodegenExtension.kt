package dev.isaacudy.udytils.postgres.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * DSL for the `dev.isaacudy.udytils.postgres` plugin (the `postgresCodegen { }`
 * block). All fields are lazy [org.gradle.api.provider.Provider]s for
 * configuration-cache safety.
 */
abstract class PostgresCodegenExtension {
    /** Flyway migrations directory. Default: `src/main/resources/db/migration`. */
    abstract val migrationsDir: DirectoryProperty

    /** Committed, diffable schema snapshot. Default: `<project>/schema.sql`. Owned by the consumer. */
    abstract val schemaSnapshotFile: RegularFileProperty

    /** Root of the generated Kotlin sources (gitignored). The output package path is appended under this. */
    abstract val generatedSourcesDir: DirectoryProperty

    /** REQUIRED. Package for the generated `Table`/`Row` files, e.g. "app.example.db.tables". */
    abstract val outputPackage: Property<String>

    /** Package the generated code imports the column-type helpers from. Default: the udytils runtime artifact. */
    abstract val columnTypesPackage: Property<String>

    /** Postgres schema to introspect. Default: "public". */
    abstract val schemaName: Property<String>

    /** Tables to skip during generation/snapshot. Default: {"flyway_schema_history"}. */
    abstract val excludedTables: SetProperty<String>

    abstract val tableSuffix: Property<String>
    abstract val rowSuffix: Property<String>

    /** sqlTable → singular PascalCase base (e.g. "people" → "Person"). Use [rowNameOverride]. */
    abstract val rowNameOverrides: MapProperty<String, String>

    /** Encoded SQL-type overrides. Use [sqlTypeOverride] rather than putting raw strings. */
    abstract val sqlTypeOverrides: MapProperty<String, String>

    /** Zonky embedded-postgres binary coordinates added to the codegen worker classpath. */
    abstract val zonkyBinaries: ListProperty<String>

    /** Version of `dev.isaacudy.udytils:postgres(-codegen)` to resolve. Defaults to the plugin's own version. */
    abstract val engineVersion: Property<String>

    /** Hook the two codegen tasks into `compileKotlin`. Default: true. */
    abstract val hookIntoCompile: Property<Boolean>

    /** Add `dev.isaacudy.udytils:postgres` to the consumer's `implementation` so generated code compiles. Default: true. */
    abstract val runtimeDependency: Property<Boolean>

    /** Banner comment in generated files / schema snapshot. */
    abstract val schemaBanner: Property<String>

    /**
     * Override the Kotlin mapping for a raw Postgres type. [factoryTemplate] is
     * an Exposed factory expression with `{name}`, `{precision}`, `{scale}`,
     * `{length}` placeholders, e.g.
     * `sqlTypeOverride("citext", "String", "text(\"{name}\")")`.
     */
    fun sqlTypeOverride(
        sqlType: String,
        kotlinType: String,
        factoryTemplate: String,
        imports: List<String> = emptyList(),
    ) {
        sqlTypeOverrides.put(sqlType.lowercase(), "$kotlinType|$factoryTemplate|${imports.joinToString(",")}")
    }

    /** Fix an irregular plural the default inflector gets wrong: `rowNameOverride("people", "Person")`. */
    fun rowNameOverride(sqlTable: String, singularPascalBase: String) {
        rowNameOverrides.put(sqlTable, singularPascalBase)
    }
}
