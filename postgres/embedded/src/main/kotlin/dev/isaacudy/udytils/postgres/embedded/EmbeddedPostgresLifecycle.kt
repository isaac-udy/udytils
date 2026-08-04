package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Owns an in-process Postgres started via Zonky's embedded-postgres.
 *
 * Zonky runs a real Postgres binary — not an in-memory fake — so NOTIFY/LISTEN, triggers,
 * column types and every other feature behave identically to a real server.
 *
 * Two storage modes:
 *
 * - **Ephemeral** (default, [dataDirectory] `null`): Zonky picks a temp directory, which
 *   is deleted on [close] when [cleanDataDirectory] is true, so each boot starts from an
 *   empty schema.
 * - **Persistent** ([dataDirectory] set): the cluster lives in that directory and survives
 *   [close], so a restart sees the previous run's data. [cleanDataDirectory] is ignored in
 *   this mode — a directory the caller named is never wiped from here; delete it yourself
 *   to reset. **Local dev only**: a data directory belongs to the Postgres *major* version
 *   of the binaries that initialised it, and a cluster from another major refuses to start,
 *   so callers should put the major version in the path (see [DevServer], which lays out
 *   `<base>/pg<major>` for exactly this reason).
 *
 * Intended for local dev and tests only; never wire this into production startup. Feed
 * [config] into your DI wiring (e.g. `postgresDependencies`) in place of an env-derived
 * config.
 */
class EmbeddedPostgresLifecycle(
    cleanDataDirectory: Boolean = true,
    maxPoolSize: Int = PostgresConfig.DEFAULT_MAX_POOL_SIZE,
    /** Persistent cluster directory, or `null` for a Zonky-managed temp directory. */
    val dataDirectory: Path? = null,
    /** Fixed port, or `null` to let Zonky pick a free one. */
    port: Int? = null,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(EmbeddedPostgresLifecycle::class.java)

    /**
     * True when this instance created the cluster rather than reopening one. Always true
     * in the ephemeral mode; for a persistent [dataDirectory] it is decided by whether the
     * directory already held an initialised cluster (`PG_VERSION`) before start.
     *
     * Callers use this for seed-once semantics: seed data belongs to a cluster that was
     * just created, and re-seeding an existing one would duplicate or conflict.
     */
    val freshlyInitialized: Boolean =
        dataDirectory == null || !Files.exists(dataDirectory.resolve(PG_VERSION_FILE))

    private val pg: EmbeddedPostgres = startPostgres(
        dataDirectory = dataDirectory,
        cleanDataDirectory = cleanDataDirectory,
        port = port,
        freshlyInitialized = freshlyInitialized,
    ).also {
        logger.info(
            "Embedded Postgres started on port {} ({})",
            it.port,
            dataDirectory?.let { dir -> "data directory $dir" } ?: "ephemeral data directory",
        )
    }

    val port: Int get() = pg.port

    /** The running server's version string, e.g. `18.4`. Queried on first access. */
    val serverVersion: String by lazy {
        pg.postgresDatabase.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SHOW server_version").use { rows ->
                    if (rows.next()) rows.getString(1) else "unknown"
                }
            }
        }
    }

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

    private companion object {
        /** Written by `initdb`; its presence is what makes a directory a Postgres cluster. */
        const val PG_VERSION_FILE = "PG_VERSION"

        fun startPostgres(
            dataDirectory: Path?,
            cleanDataDirectory: Boolean,
            port: Int?,
            freshlyInitialized: Boolean,
        ): EmbeddedPostgres {
            val builder = EmbeddedPostgres.builder()
            if (dataDirectory != null) {
                Files.createDirectories(dataDirectory)
                builder.setDataDirectory(dataDirectory)
                // Never clean a directory the caller named: cleaning re-runs initdb over it
                // at start AND deletes it on close, which is the opposite of what asking
                // for a persistent directory means.
                builder.setCleanDataDirectory(false)
            } else {
                builder.setCleanDataDirectory(cleanDataDirectory)
            }
            if (port != null) builder.setPort(port)

            return try {
                builder.start()
            } catch (error: Exception) {
                throw IllegalStateException(startFailureMessage(dataDirectory, port, freshlyInitialized), error)
            }
        }

        fun startFailureMessage(dataDirectory: Path?, port: Int?, freshlyInitialized: Boolean): String {
            val causes = buildList {
                if (port != null) {
                    add("port $port may already be in use (another dev server, or a real Postgres)")
                }
                if (dataDirectory != null && !freshlyInitialized) {
                    val clusterVersion = runCatching {
                        Files.readAllLines(dataDirectory.resolve(PG_VERSION_FILE)).firstOrNull()?.trim()
                    }.getOrNull()
                    add(
                        "the existing cluster in $dataDirectory was initialised by Postgres " +
                            "${clusterVersion ?: "(unknown)"}, and a cluster refuses to start under binaries " +
                            "of a different major version — delete that directory to start fresh",
                    )
                }
            }
            return buildString {
                append("Failed to start embedded Postgres")
                if (causes.isEmpty()) return@buildString
                append(". Likely cause: ")
                append(causes.joinToString("; "))
            }
        }
    }
}
