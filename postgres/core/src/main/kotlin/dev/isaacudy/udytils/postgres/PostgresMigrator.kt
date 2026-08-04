package dev.isaacudy.udytils.postgres

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Runs Flyway migrations against the configured Postgres datasource.
 *
 * Migration SQL files live on the classpath at `db/migration/V<N>__<name>.sql`
 * by default. Flyway discovers them at startup, applies any that haven't been
 * run yet, and records progress in the `flyway_schema_history` table.
 *
 * Call [migrate] once at server bootstrap BEFORE any application code tries to
 * use the database.
 *
 * **Fat-jar hazard.** Flyway is assembled from ServiceLoader plugins declared in
 * `META-INF/services/org.flywaydb.core.extensibility.Plugin`, a path that both
 * `flyway-core` and `flyway-database-postgresql` ship with different contents. Shadow
 * (which is what Ktor's `buildFatJar` uses) lets one clobber the other, and Flyway then
 * either throws an NPE while being configured or silently finds zero migrations and
 * reports success. [migrate] fails fast with a diagnostic when it sees that state — see
 * `postgres/README.md`, "Fat jars & ServiceLoader clobbering", for the workaround. Set
 * [verifyFlywayPlugins] to `false` only if you deliberately run without
 * `flyway-database-postgresql` on the classpath.
 */
class PostgresMigrator(
    private val dataSource: DataSource,
    /** Flyway locations to scan, e.g. `classpath:db/migration` or `filesystem:...`. */
    private val locations: List<String> = listOf("classpath:db/migration"),
    private val baselineOnMigrate: Boolean = true,
    /** Guards against a fat jar having clobbered Flyway's ServiceLoader manifest. */
    private val verifyFlywayPlugins: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(PostgresMigrator::class.java)

    /**
     * Applies every pending migration and returns what happened. A [Summary.discovered]
     * of zero is legitimate (a new project with no migrations yet) and never fails — but
     * it is also what the clobbered-manifest bug looks like, which is why the manifest is
     * checked directly rather than inferred from the count.
     */
    fun migrate(): Summary {
        if (verifyFlywayPlugins) {
            FlywayPluginManifest.verify(FlywayPluginManifest.readFromClasspath(javaClass.classLoader))
        }
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .baselineOnMigrate(baselineOnMigrate)
            .load()
        val discovered = flyway.info().all().size
        val result = flyway.migrate()
        logger.info(
            "Flyway migration complete: {} of {} discovered migrations executed, schema now at version {}",
            result.migrationsExecuted,
            discovered,
            result.targetSchemaVersion ?: "(empty)",
        )
        return Summary(
            discovered = discovered,
            executed = result.migrationsExecuted,
            schemaVersion = result.targetSchemaVersion,
        )
    }

    /** What a [migrate] call did: how many migrations were on the classpath, and how many ran. */
    data class Summary(
        val discovered: Int,
        val executed: Int,
        val schemaVersion: String?,
    )
}
