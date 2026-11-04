package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.PostgresMigrator
import org.postgresql.ds.PGSimpleDataSource
import java.sql.DriverManager
import java.util.UUID

/**
 * A migrated, empty database on [postgres] that tests clone instead of migrating again: [clone]
 * copies the template in milliseconds however many migrations it holds, so every test can start
 * from its own empty schema.
 *
 * ```
 * val postgres = EmbeddedPostgresLifecycle()
 * val template = MigratedTemplate(postgres)
 *
 * template.clone().use { database ->
 *     val config = database.config   // feed into postgresDependencies(...)
 * }
 * ```
 *
 * The template refuses connections once migrated: `CREATE DATABASE … TEMPLATE` fails while any
 * session is connected to the source.
 */
class MigratedTemplate(
    private val postgres: EmbeddedPostgresLifecycle,
    val name: String = "udytils_template",
    migrationLocations: List<String> = listOf("classpath:db/migration"),
) : AutoCloseable {

    init {
        require(DATABASE_NAME.matches(name)) { "Template name '$name' must match ${DATABASE_NAME.pattern}" }
        maintenance("DROP DATABASE IF EXISTS \"$name\" WITH (FORCE)")
        maintenance("CREATE DATABASE \"$name\"")
        PostgresMigrator(dataSource(name), locations = migrationLocations).migrate()
        maintenance("ALTER DATABASE \"$name\" WITH ALLOW_CONNECTIONS false")
    }

    /** A new database copied from the template. Closing it drops the database. */
    fun clone(maxPoolSize: Int = 4): ClonedDatabase {
        val database = "t_" + UUID.randomUUID().toString().replace("-", "")
        maintenance("CREATE DATABASE \"$database\" TEMPLATE \"$name\"")
        return ClonedDatabase(
            config = postgres.config.copy(jdbcUrl = jdbcUrl(database), maxPoolSize = maxPoolSize),
            drop = { maintenance("DROP DATABASE IF EXISTS \"$database\" WITH (FORCE)") },
        )
    }

    override fun close() {
        maintenance("DROP DATABASE IF EXISTS \"$name\" WITH (FORCE)")
    }

    private fun jdbcUrl(database: String) = "jdbc:postgresql://localhost:${postgres.port}/$database"

    private fun dataSource(database: String) = PGSimpleDataSource().apply {
        setUrl(jdbcUrl(database))
        user = postgres.config.username
        password = postgres.config.password
    }

    private fun maintenance(sql: String) {
        val config = postgres.config
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private companion object {
        val DATABASE_NAME = Regex("[a-z_][a-z0-9_]*")
    }
}

/** A database cloned from a [MigratedTemplate]; [close] drops it, closing any open sessions. */
class ClonedDatabase internal constructor(
    val config: PostgresConfig,
    private val drop: () -> Unit,
) : AutoCloseable {
    override fun close() = drop()
}
