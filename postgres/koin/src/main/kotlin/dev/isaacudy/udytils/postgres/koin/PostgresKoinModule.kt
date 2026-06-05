package dev.isaacudy.udytils.postgres.koin

import dev.isaacudy.udytils.postgres.PgNotificationBus
import dev.isaacudy.udytils.postgres.PostgresConfig
import dev.isaacudy.udytils.postgres.PostgresMigrator
import dev.isaacudy.udytils.postgres.buildHikariDataSource
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import javax.sql.DataSource

/**
 * Builds a Koin module providing the Postgres [DataSource], the Exposed
 * [Database] handle, the [PostgresMigrator], and the [PgNotificationBus].
 *
 * Pass an explicit [PostgresConfig] — there is no environment-derived default
 * here, so the consuming application owns where its connection details and
 * pool name come from.
 *
 * Callers should invoke `get<PostgresMigrator>().migrate()` during server
 * startup (before any application code uses the database), and `close()` the
 * [PgNotificationBus] when the owning lifecycle shuts down.
 */
fun postgresDependencies(config: PostgresConfig): Module = module {
    single { config }

    single<DataSource> { buildHikariDataSource(get()) }

    single<Database> { Database.connect(get<DataSource>()) }

    single { PostgresMigrator(get()) }

    single { PgNotificationBus(get<PostgresConfig>()) }
}
