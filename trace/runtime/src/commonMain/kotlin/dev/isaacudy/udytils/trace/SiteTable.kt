package dev.isaacudy.udytils.trace

/** Maps a site to the architecture Constructs of its declaration; the trace-site-table Gradle plugin generates the implementation. */
interface SiteTable {
    fun constructsOf(siteId: Long): List<String>
}

/** Selects the sites whose declaration belongs to any of [constructs], with [flags]. */
fun SiteTable.selecting(vararg constructs: String, flags: Int = SiteFlags.RECORD): (TraceSite) -> Int = { site ->
    if (constructsOf(site.siteId).any { it in constructs }) flags else 0
}
