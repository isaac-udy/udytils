package dev.isaacudy.udytils.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Connection configuration for a Postgres instance.
 *
 * This carries no application-specific defaults — the consumer supplies the
 * JDBC URL and credentials (e.g. from environment variables). Pool tuning has
 * neutral defaults that suit a small instance and can be overridden.
 *
 * [isAutoCommit] defaults to `false` because this library is built for use
 * with Exposed, which manages its own transactions and requires the underlying
 * pool to NOT auto-commit. HikariCP's own default is `true`; keep this `false`
 * whenever the [java.sql.DataSource] backs an Exposed `Database`.
 */
data class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    /** Don't squat on connections when idle. */
    val minimumIdle: Int = 0,
    /** Release idle connections fast on a small DB (1 min). */
    val idleTimeoutMs: Long = 60_000L,
    /** Must remain `false` when the DataSource backs an Exposed Database. */
    val isAutoCommit: Boolean = false,
    val poolName: String = "postgres",
) {
    companion object {
        const val DEFAULT_MAX_POOL_SIZE = 3
    }
}

/**
 * Builds a HikariCP [HikariDataSource] from a [PostgresConfig]. Extracted from
 * any DI wiring so the connection graph is constructable without Koin.
 */
fun buildHikariDataSource(config: PostgresConfig): HikariDataSource {
    val hikari = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        maximumPoolSize = config.maxPoolSize
        minimumIdle = config.minimumIdle
        idleTimeout = config.idleTimeoutMs
        isAutoCommit = config.isAutoCommit
        driverClassName = "org.postgresql.Driver"
        poolName = config.poolName
    }
    return HikariDataSource(hikari)
}
