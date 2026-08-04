package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import java.nio.file.Path

/**
 * How [DevServer] should store its data.
 *
 * The toolkit reads no environment variables of its own — an application owns the naming
 * of its own switches (`MYAPP_DEV_DB`, `MYAPP_DEV_SCENARIO`, …) and turns them into one of
 * these.
 */
sealed interface DevServerStorage {

    /**
     * Keeps the cluster in a directory under [baseDirectory] so data survives restarts —
     * the recommended default for an application's dev mode, because a hot reload or a
     * `Ctrl-C` then doesn't cost you the state you just clicked your way into.
     *
     * The cluster itself lives in `<baseDirectory>/pg<major>`, where `<major>` is the
     * Postgres major version of the bundled binaries: a binaries bump starts a fresh
     * cluster next to the old one instead of failing on a data directory the new server
     * can't read.
     *
     * [port] is fixed so `psql` commands, IDE data sources and `.env` files keep working
     * across restarts. If it collides with something else on the machine, change it.
     */
    data class Persistent(
        val baseDirectory: Path,
        val port: Int = DEFAULT_PORT,
    ) : DevServerStorage {
        companion object {
            /** Deliberately not 5432, so a dev server can't be mistaken for a local Postgres install. */
            const val DEFAULT_PORT: Int = 15432
        }
    }

    /**
     * A clean temp directory and a random port on every start, deleted on shutdown. For
     * demos, throwaway experiments and tests, where "the same database as last time" is
     * the wrong answer.
     */
    data object Ephemeral : DevServerStorage
}

/**
 * Everything [DevServer] needs: where the data lives, what to seed a brand-new cluster
 * with, and where the Flyway migrations are.
 *
 * The two scenario inputs are deliberately separate, because they mean different things:
 *
 * - [freshScenario] is the application's own default — "if this cluster is brand new, this
 *   is what I want in it". Skipped without complaint when the data directory already
 *   holds a database.
 * - [requestedScenario] is an operator asking for a specific starting state right now
 *   (typically from an env var). Applying one over an existing database is not supported
 *   and fails loudly rather than half-seeding on top of whatever is already there.
 */
data class DevServerConfig(
    val storage: DevServerStorage,
    /** Applied when the cluster is created; ignored when reopening an existing one. */
    val freshScenario: DevScenario = EmptyScenario,
    /** Explicitly asked for by the operator; fails if the database already has data. */
    val requestedScenario: DevScenario? = null,
    /** Flyway locations, matching `PostgresMigrator`'s default. */
    val migrationLocations: List<String> = listOf("classpath:db/migration"),
    val maxPoolSize: Int = PostgresConfig.DEFAULT_MAX_POOL_SIZE,
)
