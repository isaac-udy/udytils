package dev.isaacudy.udytils.postgres

/**
 * Self-check for Flyway's ServiceLoader manifest.
 *
 * `flyway-core` and `flyway-database-postgresql` both ship
 * `META-INF/services/org.flywaydb.core.extensibility.Plugin` — the same path, with
 * disjoint contents. On a normal classpath both files coexist in their own jars and
 * Flyway's `PluginRegister` sees the union. In a fat jar the duplicate paths collide and
 * one file silently clobbers the other, which breaks Flyway in one of two ways: an NPE
 * while constructing the configuration, or a scan that finds zero migrations and reports
 * success (an empty schema in production).
 *
 * The union of every copy on the classpath is checked by package prefix rather than by
 * class name, so the check survives Flyway upgrades that add, rename or drop plugins.
 */
internal object FlywayPluginManifest {

    const val SERVICE_RESOURCE: String = "META-INF/services/org.flywaydb.core.extensibility.Plugin"

    /** Every entry `flyway-core` contributes starts with this. */
    private const val CORE_PREFIX = "org.flywaydb.core."

    /** Every entry `flyway-database-postgresql` contributes starts with this. */
    private const val POSTGRES_PREFIX = "org.flywaydb.database.postgresql"

    /** Reads every copy of the service file visible to [classLoader]. */
    fun readFromClasspath(classLoader: ClassLoader): Set<String> {
        val contents = classLoader.getResources(SERVICE_RESOURCE)
            .toList()
            .map { url -> url.openStream().use { it.readBytes().toString(Charsets.UTF_8) } }
        return union(contents)
    }

    /**
     * Unions the entries of several service-file [contents]. ServiceLoader syntax: one
     * class name per line, `#` starts a comment, blank lines are ignored.
     */
    fun union(contents: List<String>): Set<String> = contents
        .flatMap { it.lineSequence() }
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    /**
     * Throws [IllegalStateException] with a diagnostic naming the fat-jar cause when
     * [entries] is missing either artifact's contribution.
     */
    fun verify(entries: Set<String>) {
        val hasCore = entries.any { it.startsWith(CORE_PREFIX) }
        val hasPostgres = entries.any { it.startsWith(POSTGRES_PREFIX) }
        if (hasCore && hasPostgres) return
        val missing = when {
            !hasCore && !hasPostgres -> "no flyway plugins at all"
            !hasCore -> "no '$CORE_PREFIX*' entries (flyway-core's contribution)"
            else -> "no '$POSTGRES_PREFIX*' entries (flyway-database-postgresql's contribution)"
        }
        throw IllegalStateException(
            """
            Flyway's plugin manifest is incomplete: $SERVICE_RESOURCE has $missing (${entries.size} entries found).
            flyway-core and flyway-database-postgresql both ship that path with disjoint contents, and a
            shadow/fat jar lets one silently clobber the other — Shadow's mergeServiceFiles() was confirmed
            NOT merging in Shadow 9.1.0 (the version bundled with Ktor's Gradle plugin), so verify by extraction:
                unzip -p build/libs/<app>-all.jar $SERVICE_RESOURCE
            Fix: check a hand-merged copy (the union of both jars' entries) into the executable module's own
            resources — project resources win the conflict — and regenerate it whenever the resolved Flyway
            version changes. This check exists because the alternative is a NullPointerException while
            constructing the Flyway configuration, or worse, Flyway finding zero migrations, reporting success,
            and leaving an empty schema in production.
            See postgres/README.md — "Fat jars & ServiceLoader clobbering".
            """.trimIndent(),
        )
    }
}
