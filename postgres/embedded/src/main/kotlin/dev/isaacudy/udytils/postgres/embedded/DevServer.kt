package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.PostgresMigrator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Entry point for running an application against an embedded Postgres in local dev.
 *
 * The embedded server is **scoped to the JVM lifetime**, not to any application or
 * framework lifecycle. Ktor's auto-reload restarts the application module on classpath
 * changes; tying the database to `ApplicationStopped` would kill it on every reload and
 * lose whatever state you were working with. So [start] returns the same handle for the
 * life of the JVM, and shutdown is registered once via [Runtime.addShutdownHook].
 *
 * Typical wiring, with the application owning its own env-var vocabulary:
 *
 * ```kotlin
 * fun main() {
 *     val postgresConfig = when (System.getenv("MYAPP_DEV_DB")) {
 *         "embedded" -> DevServer.start(
 *             DevServerConfig(
 *                 storage = DevServerStorage.Persistent(
 *                     baseDirectory = Path.of(System.getProperty("user.home"), ".myapp", "devdb"),
 *                 ),
 *                 freshScenario = DefaultScenario,
 *                 requestedScenario = System.getenv("MYAPP_DEV_SCENARIO")?.let(Scenarios::byName),
 *             )
 *         ).postgresConfig
 *         else -> PostgresConfig(jdbcUrl = ..., username = ..., password = ...)
 *     }
 *     startServer(postgresConfig)
 * }
 * ```
 */
object DevServer {

    private val logger = LoggerFactory.getLogger(DevServer::class.java)

    @Volatile
    private var handle: DevServerHandle? = null

    @Volatile
    private var startedWith: DevServerConfig? = null

    /**
     * Starts the embedded server, migrates it, and seeds it if the cluster is new.
     *
     * Idempotent: the first call in a JVM does the work and registers the shutdown hook;
     * later calls return the same handle, so a reload neither restarts Postgres nor
     * re-applies a scenario. A later call with a *different* config is a no-op and says so
     * in the log.
     *
     * Blocking by design — this is a boot path, and nothing the application does with the
     * database is valid until migration and seeding have finished.
     */
    fun start(config: DevServerConfig): DevServerHandle {
        live()?.let { return it.alsoWarnIfConfigDiffers(config) }
        return synchronized(this) {
            live()?.alsoWarnIfConfigDiffers(config) ?: launch(config).also { started ->
                handle = started
                startedWith = config
                Runtime.getRuntime().addShutdownHook(
                    Thread(
                        {
                            runCatching { started.close() }.onFailure {
                                logger.warn("Failed to stop embedded Postgres on JVM shutdown: {}", it.message)
                            }
                        },
                        "embedded-postgres-shutdown",
                    ),
                )
            }
        }
    }

    private fun live(): DevServerHandle? = handle?.takeIf { !it.isClosed }

    private fun DevServerHandle.alsoWarnIfConfigDiffers(config: DevServerConfig): DevServerHandle = also {
        if (startedWith != null && startedWith != config) {
            logger.warn(
                "DevServer is already running with a different config; ignoring the new one. " +
                    "Running: {}. Requested: {}.",
                startedWith,
                config,
            )
        }
    }

    /**
     * The whole start sequence without the JVM-singleton bookkeeping. Internal so tests can
     * run several independent dev servers in one JVM.
     */
    internal fun launch(config: DevServerConfig): DevServerHandle {
        val (dataDirectory, port) = when (val storage = config.storage) {
            is DevServerStorage.Persistent ->
                storage.baseDirectory.resolve(clusterDirectoryName()) to storage.port

            DevServerStorage.Ephemeral -> null to null
        }

        val lifecycle = EmbeddedPostgresLifecycle(
            maxPoolSize = config.maxPoolSize,
            dataDirectory = dataDirectory,
            port = port,
        )
        return runCatching { prepare(lifecycle, config, dataDirectory) }
            .onFailure { lifecycle.close() }
            .getOrThrow()
    }

    private fun prepare(
        lifecycle: EmbeddedPostgresLifecycle,
        config: DevServerConfig,
        dataDirectory: Path?,
    ): DevServerHandle {
        // One-shot, non-pooled datasource: migration and seeding both finish before the
        // application builds its real Hikari pool from the returned PostgresConfig.
        val postgresConfig = lifecycle.config
        val dataSource = PGSimpleDataSource().apply {
            setUrl(postgresConfig.jdbcUrl)
            user = postgresConfig.username
            password = postgresConfig.password
        }
        val migration = PostgresMigrator(dataSource, locations = config.migrationLocations).migrate()

        val scenario = selectScenario(lifecycle.freshlyInitialized, config, dataDirectory)
        if (scenario != null) {
            logger.info("Applying dev scenario '{}'", scenario.name)
            runBlocking { scenario.apply(Database.connect(dataSource)) }
        }

        logger.info(
            banner(
                config = config,
                dataDirectory = dataDirectory,
                serverVersion = lifecycle.serverVersion,
                port = lifecycle.port,
                migration = migration,
                appliedScenario = scenario,
            ),
        )

        return DevServerHandle(
            lifecycle = lifecycle,
            postgresConfig = postgresConfig,
            dataDirectory = dataDirectory,
            freshlyInitialized = lifecycle.freshlyInitialized,
            appliedScenario = scenario,
        )
    }

    /**
     * Seed-once: a scenario belongs to a cluster that was just created. Reopening a
     * persistent cluster skips seeding, unless the caller explicitly asked for a scenario —
     * which over existing data is unsupported, and fails rather than inserting on top.
     */
    private fun selectScenario(
        freshlyInitialized: Boolean,
        config: DevServerConfig,
        dataDirectory: Path?,
    ): DevScenario? {
        if (freshlyInitialized) return config.requestedScenario ?: config.freshScenario
        val requested = config.requestedScenario ?: return null
        throw IllegalStateException(
            """
            Dev scenario '${requested.name}' was explicitly requested, but the dev database at
            ${dataDirectory ?: "(ephemeral)"} already contains data. Scenarios seed a brand-new
            database; applying one on top of an existing one would insert over whatever is already
            there. Either drop the scenario request to keep using the existing data, or wipe the dev
            data directory (e.g. your project's wipeDevDatabase task, or delete the directory above)
            and start again.
            """.trimIndent(),
        )
    }

    /**
     * `pg<major>` so a binaries bump starts a fresh cluster beside the old one instead of
     * failing on a data directory the new server can't read.
     */
    private fun clusterDirectoryName(): String {
        val major = ZonkyBinaries.majorVersionOrNull(EmbeddedPostgres::class.java.classLoader)
        if (major == null) {
            logger.warn(
                "Could not determine the Postgres major version of the embedded binaries; using the " +
                    "'{}' data directory. If the binaries are upgraded, delete that directory by hand.",
                UNVERSIONED_CLUSTER_DIRECTORY,
            )
            return UNVERSIONED_CLUSTER_DIRECTORY
        }
        return "pg$major"
    }

    private const val UNVERSIONED_CLUSTER_DIRECTORY = "pg-unknown"

    private fun banner(
        config: DevServerConfig,
        dataDirectory: Path?,
        serverVersion: String,
        port: Int,
        migration: PostgresMigrator.Summary,
        appliedScenario: DevScenario?,
    ): String {
        val mode = if (config.storage is DevServerStorage.Persistent) "embedded-persistent" else "embedded-ephemeral"
        val seeding = when {
            appliedScenario != null -> "applied scenario '${appliedScenario.name}'" +
                appliedScenario.description.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()

            else -> "skipped, the dev database already contains data"
        }
        val lines = buildList {
            add("Dev database: $mode")
            add("Postgres $serverVersion on port $port")
            if (dataDirectory != null) {
                add("Data directory: $dataDirectory")
                add("  survives restarts; delete it (or run your project's wipe task) to reset")
            } else {
                add("Temporary data directory; everything is discarded on shutdown")
            }
            add("Migrations: ${migration.executed} applied of ${migration.discovered} found")
            add("Seeding: $seeding")
        }
        val rule = "─".repeat(lines.maxOf { it.length } + 2)
        return lines.joinToString(separator = "\n", prefix = "\n$rule\n", postfix = "\n$rule") { " $it" }
    }
}

/**
 * The running dev server. Feed [postgresConfig] into the application's DI wiring in place
 * of the production config.
 *
 * Closing is the JVM shutdown hook's job — an application never needs to call [close], and
 * shouldn't tie it to a framework lifecycle (that is what the JVM scoping is for). Tests
 * that start their own dev servers do close them.
 */
class DevServerHandle internal constructor(
    private val lifecycle: EmbeddedPostgresLifecycle,
    val postgresConfig: PostgresConfig,
    /** The cluster's directory, or `null` when running ephemerally. */
    val dataDirectory: Path?,
    /** True when this run created the cluster — i.e. when seeding was on the table. */
    val freshlyInitialized: Boolean,
    /** The scenario that ran, or `null` when seeding was skipped. */
    val appliedScenario: DevScenario?,
) : AutoCloseable {

    val port: Int get() = lifecycle.port

    @Volatile
    var isClosed: Boolean = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        lifecycle.close()
    }
}
