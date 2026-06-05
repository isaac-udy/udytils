package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory

/**
 * Owns an in-process Postgres started via Zonky's embedded-postgres.
 *
 * Zonky downloads and runs a real Postgres binary in a temporary data
 * directory — not an in-memory fake — so NOTIFY/LISTEN, triggers, and every
 * other feature behave identically to a real server. The data directory is
 * deleted on [close] (when [cleanDataDirectory] is true), so each boot starts
 * from an empty schema.
 *
 * Intended for local dev and tests only; never wire this into production
 * startup. Feed [config] into your DI wiring (e.g. `postgresDependencies`) in
 * place of an env-derived config.
 */
class EmbeddedPostgresLifecycle(
    cleanDataDirectory: Boolean = true,
    maxPoolSize: Int = PostgresConfig.DEFAULT_MAX_POOL_SIZE,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(EmbeddedPostgresLifecycle::class.java)

    private val pg: EmbeddedPostgres = EmbeddedPostgres.builder()
        .setCleanDataDirectory(cleanDataDirectory)
        .start()
        .also { logger.info("Embedded Postgres started on port {}", it.port) }

    val port: Int get() = pg.port

    /** A [PostgresConfig] pointing at the embedded server. */
    val config: PostgresConfig = PostgresConfig(
        jdbcUrl = "jdbc:postgresql://localhost:${pg.port}/postgres",
        username = "postgres",
        password = "postgres",
        maxPoolSize = maxPoolSize,
    )

    override fun close() {
        logger.info("Stopping embedded Postgres")
        pg.close()
    }
}
