package dev.isaacudy.udytils.postgres.embedded

/**
 * Resolves the Postgres major version of the Zonky binaries actually on the classpath.
 *
 * A persistent data directory belongs to the major version that initialised it, so
 * [DevServer] puts the major version in the directory name — bumping the binaries then
 * starts a fresh cluster instead of failing on an incompatible one. That only works if the
 * version is read from the binaries themselves rather than from a constant that can drift:
 * the `embedded-postgres-binaries-*` artifacts each contain exactly one
 * `postgres-<os>-<arch>.txz` and carry their Postgres version in the artifact version, so
 * the jar the archive is loaded from names the version.
 */
internal object ZonkyBinaries {

    /**
     * Archive names used by Zonky's `DefaultPostgresBinaryResolver`. Every published
     * binaries artifact contains exactly one of these at the archive root; enumerating them
     * avoids duplicating Zonky's os/arch normalisation, and only one will resolve on any
     * given machine's classpath anyway.
     */
    private val ARCHIVE_NAMES = listOf(
        "postgres-darwin-arm_64.txz",
        "postgres-darwin-x86_64.txz",
        "postgres-linux-arm_64.txz",
        "postgres-linux-x86_64.txz",
        "postgres-linux-i386.txz",
        "postgres-windows-x86_64.txz",
    )

    private val BINARIES_JAR = Regex("""embedded-postgres-binaries-.*-(\d+)\.\d+(?:\.\d+)?[^/]*\.jar""")

    /**
     * The major version, or `null` when it can't be read — the archive is repackaged
     * (a fat jar embeds the `.txz` without the artifact's file name) or the binaries are
     * supplied by a custom `PgBinaryResolver`. Callers fall back to a version-less
     * directory name and report it, rather than guessing a version the binaries may not be.
     */
    fun majorVersionOrNull(classLoader: ClassLoader): Int? = ARCHIVE_NAMES
        .asSequence()
        .flatMap { name -> classLoader.getResources(name).toList().asSequence() }
        .mapNotNull { url -> url.toString().substringBefore("!/").substringAfterLast('/') }
        .mapNotNull { jarName -> BINARIES_JAR.find(jarName)?.groupValues?.get(1)?.toIntOrNull() }
        .firstOrNull()
}
