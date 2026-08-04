package dev.isaacudy.udytils.postgres.embedded

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * A named, reproducible "starting state" for a locally-running server.
 *
 * Scenarios are **project-owned**: this toolkit defines the interface and [EmptyScenario],
 * and the application writes the rest against its own generated Exposed tables. They run
 * once, against a freshly-migrated database that [DevServer] has just created — so a
 * scenario can assume an empty schema, insert with fixed ids, and stay deterministic.
 *
 * Selection is the application's business too (typically an env var it names itself);
 * hand the chosen scenario to [DevServerConfig].
 */
interface DevScenario {
    /** Stable identifier, e.g. the value an application's `*_DEV_SCENARIO` env var takes. */
    val name: String

    /** Short human description, shown in the dev-server boot banner. */
    val description: String get() = ""

    suspend fun apply(database: Database)
}

/**
 * Migrated, otherwise-empty database — the default for a fresh dev cluster. Useful as an
 * explicit selection too ("I just want a clean server"), so it reads the same as any other
 * scenario in an application's scenario list.
 */
object EmptyScenario : DevScenario {
    override val name: String = "empty"
    override val description: String = "Fresh schema, no rows."

    override suspend fun apply(database: Database) {
        // Nothing to do: the schema Flyway just created IS the scenario.
    }
}
