package dev.isaacudy.udytils.postgres.embedded

import dev.isaacudy.udytils.postgres.PostgresConfig
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.deleteRecursively

/**
 * A port the OS just told us is free. Tests can't take the dev-server default (15432) —
 * a developer may well have their own dev server running on it.
 */
internal fun freePort(): Int = ServerSocket(0).use { it.localPort }

internal fun tempDirectory(prefix: String): Path = Files.createTempDirectory(prefix)

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
internal fun Path.deleteRecursivelyQuietly() {
    runCatching { deleteRecursively() }
}

/** Plain JDBC, deliberately: assertions shouldn't depend on the Exposed API under test. */
internal fun <T> PostgresConfig.query(sql: String, read: (java.sql.ResultSet) -> T): T =
    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> read(rows) }
        }
    }

internal fun PostgresConfig.execute(sql: String) {
    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
        connection.createStatement().use { it.execute(sql) }
    }
}

internal fun PostgresConfig.countRows(table: String): Int =
    query("SELECT count(*) FROM $table") { rows ->
        rows.next()
        rows.getInt(1)
    }
