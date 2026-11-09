package dev.isaacudy.udytils.postgres.embedded

import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigratedTemplateTest {

    private val postgres = EmbeddedPostgresLifecycle()
    private val template = MigratedTemplate(postgres)

    @AfterTest
    fun cleanUp() {
        template.close()
        postgres.close()
    }

    @Test
    fun cloneIsMigratedAndEmpty() {
        template.clone().use { database ->
            assertEquals(0, database.config.countRows("widgets"))
        }
    }

    @Test
    fun clonesAreIsolated() {
        template.clone().use { first ->
            template.clone().use { second ->
                first.config.execute("INSERT INTO widgets (name) VALUES ('only in first')")
                assertEquals(1, first.config.countRows("widgets"))
                assertEquals(0, second.config.countRows("widgets"))
            }
        }
        template.clone().use { later -> assertEquals(0, later.config.countRows("widgets")) }
    }

    @Test
    fun closingACloneDropsItWithSessionsStillOpen() {
        val database = template.clone()
        val session = DriverManager.getConnection(database.config.jdbcUrl, database.config.username, database.config.password)
        database.close()
        assertFailsWith<Exception> { database.config.countRows("widgets") }
        runCatching { session.close() }
    }
}
