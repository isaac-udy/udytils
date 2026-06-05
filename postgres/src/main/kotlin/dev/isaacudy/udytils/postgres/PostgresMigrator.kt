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
 */
class PostgresMigrator(
    private val dataSource: DataSource,
    /** Flyway locations to scan, e.g. `classpath:db/migration` or `filesystem:...`. */
    private val locations: List<String> = listOf("classpath:db/migration"),
    private val baselineOnMigrate: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(PostgresMigrator::class.java)

    fun migrate() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .baselineOnMigrate(baselineOnMigrate)
            .load()
        val result = flyway.migrate()
        logger.info(
            "Flyway migration complete: {} executed, schema now at version {}",
            result.migrationsExecuted,
            result.targetSchemaVersion ?: "(empty)",
        )
    }
}
