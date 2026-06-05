package dev.isaacudy.udytils.postgres.codegen

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import java.io.File
import java.sql.Connection

/**
 * Boots an ephemeral embedded Postgres, applies the Flyway migrations from
 * [migrationsDir], then hands a live [Connection] to [block]. The instance is
 * always shut down afterward. [tag] just prefixes the progress logs.
 *
 * Zonky downloads and runs a real Postgres binary in a temp data directory —
 * not an in-memory fake — so triggers, functions and every other feature
 * behave exactly as in production.
 */
internal fun <T> withMigratedDatabase(migrationsDir: File, tag: String, block: (Connection) -> T): T {
    require(migrationsDir.isDirectory) { "Migrations dir not found: ${migrationsDir.absolutePath}" }

    println("[$tag] starting embedded Postgres…")
    val started = System.nanoTime()
    val pg = EmbeddedPostgres.start()
    val bootMs = (System.nanoTime() - started) / 1_000_000
    println("[$tag] embedded Postgres ready in $bootMs ms")

    try {
        val dataSource = pg.postgresDatabase
        val migrationCount = migrationsDir.list()?.size ?: 0
        println("[$tag] applying $migrationCount migration files from ${migrationsDir.absolutePath}")
        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:${migrationsDir.absolutePath}")
            .load()
            .migrate()

        return dataSource.connection.use { block(it) }
    } finally {
        pg.close()
    }
}
