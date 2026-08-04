package dev.isaacudy.udytils.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the `// architecture-exception:` build-file comment parser.
 *
 * The regression these guard: the id pattern used to be `[A-Z0-9\-,\s]+`, which accepted only
 * upper-case letters and so could not capture a dotted path id at all — for
 * `ModuleRules.platformNotFeature` it matched the leading `M` and produced that as the id, meaning
 * no build-file exemption ever applied to a module-graph rule. The documented example in
 * `docs/exceptions.md` did not parse against the parser that was supposed to read it.
 */
class ModuleGraphParserTest {

    /** Runs the parser over [lines], treating the LAST line as the dependency line. */
    private fun exemptionsAbove(vararg lines: String): Set<String> =
        collectExemptions(lines.toList(), lines.lastIndex)

    @Test
    fun `parses a dotted path rule id`() {
        assertEquals(
            setOf("ModuleRules.platformNotFeature"),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `parses the example documented in exceptions_md, reason lines and all`() {
        // Verbatim from docs/exceptions.md — the parser must read its own documentation.
        assertEquals(
            setOf("ModuleRules.platformNotFeature"),
            exemptionsAbove(
                "        // architecture-exception: ModuleRules.platformNotFeature",
                "        // reason=\"Pulls feature-level analytics types that haven't yet been promoted to \" +",
                "        //   \"a platform module. Refactor tracked separately.\"",
                "        implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `parses a deeply nested path id`() {
        assertEquals(
            setOf("DomainLayer.DomainInterface.primaryReturnType"),
            exemptionsAbove(
                "// architecture-exception: DomainLayer.DomainInterface.primaryReturnType",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `parses a comma-separated list of ids`() {
        assertEquals(
            setOf("ModuleRules.platformNotFeature", "ModuleRules.platformNotApp"),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature, ModuleRules.platformNotApp",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `still parses legacy dashed rule ids`() {
        assertEquals(
            setOf("R-MOD-10"),
            exemptionsAbove(
                "// architecture-exception: R-MOD-10",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `stacked marker lines union their ids`() {
        assertEquals(
            setOf("ModuleRules.platformNotFeature", "ModuleRules.platformNotApp"),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature",
                "// architecture-exception: ModuleRules.platformNotApp",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `trailing prose on the marker line is not swallowed into the id`() {
        assertEquals(
            setOf("ModuleRules.platformNotFeature"),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature — legacy, remove once promoted",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `an intervening code line stops the walk`() {
        assertEquals(
            emptySet(),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature",
                "implementation(projects.platform.common.log)",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `a blank line stops the walk`() {
        assertEquals(
            emptySet(),
            exemptionsAbove(
                "// architecture-exception: ModuleRules.platformNotFeature",
                "",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    @Test
    fun `an unmarked comment yields no exemptions`() {
        assertEquals(
            emptySet(),
            exemptionsAbove(
                "// just an ordinary comment",
                "implementation(projects.feature.core.api)",
            ),
        )
    }

    // --- edgesFromLines: the two dependency notations ---

    @Test
    fun `parses a typesafe accessor edge`() {
        val edges = edgesFromLines(
            from = ":app",
            relFile = "app/build.gradle.kts",
            lines = listOf("    implementation(projects.platform.common.textSimilarity)"),
        )
        assertEquals(listOf(":platform:common:text-similarity"), edges.map { it.to })
        assertEquals(":app", edges.single().from)
        assertEquals(1, edges.single().line)
    }

    @Test
    fun `parses a string-notation edge verbatim`() {
        val edges = edgesFromLines(
            from = ":urpc:sample",
            relFile = "urpc/sample/build.gradle.kts",
            lines = listOf("""    implementation(rootProject.project(":urpc:protocol"))"""),
        )
        assertEquals(listOf(":urpc:protocol"), edges.map { it.to })
    }

    @Test
    fun `parses a flat-named string-notation edge`() {
        val edges = edgesFromLines(
            from = ":postgres-koin",
            relFile = "postgres/koin/build.gradle.kts",
            lines = listOf("""    api(project(":postgres-core"))"""),
        )
        assertEquals(listOf(":postgres-core"), edges.map { it.to })
    }

    @Test
    fun `string notation carries exemptions like typesafe accessors do`() {
        val edges = edgesFromLines(
            from = ":feature",
            relFile = "feature/build.gradle.kts",
            lines = listOf(
                "    // architecture-exception: ModuleRules.platformNotFeature",
                """    implementation(project(":platform:common:api"))""",
            ),
        )
        assertEquals(setOf("ModuleRules.platformNotFeature"), edges.single().exemptRuleIds)
    }

    @Test
    fun `plain project property access is not an edge`() {
        val edges = edgesFromLines(
            from = ":app",
            relFile = "app/build.gradle.kts",
            lines = listOf(
                "    val dir = project.layout.buildDirectory",
                "    println(project.name)",
            ),
        )
        assertEquals(emptyList(), edges)
    }

    @Test
    fun `both notations on one line produce both edges`() {
        val edges = edgesFromLines(
            from = ":app",
            relFile = "app/build.gradle.kts",
            lines = listOf("""    listOf(projects.core, project(":ui"))"""),
        )
        assertEquals(setOf(":core", ":ui"), edges.map { it.to }.toSet())
    }

    // --- projectDirRemappings: settings.gradle.kts projectDir mappings ---

    @Test
    fun `reads projectDir remappings from settings lines`() {
        assertEquals(
            mapOf("postgres/core" to ":postgres-core", "architecture/udytils" to ":udytils-architecture"),
            projectDirRemappings(
                listOf(
                    """include(":postgres-core")""",
                    """project(":postgres-core").projectDir = file("postgres/core")""",
                    """project(":udytils-architecture").projectDir = file("architecture/udytils")""",
                ),
            ),
        )
    }

    @Test
    fun `ordinary include lines produce no remappings`() {
        assertEquals(
            emptyMap(),
            projectDirRemappings(
                listOf(
                    """include(":core")""",
                    """include(":urpc:client")""",
                ),
            ),
        )
    }

    // --- the walk: which build scripts are part of the graph ---

    /**
     * Writes a synthetic project tree under a root directory named [rootName] and parses it.
     *
     * The root's *name* is a parameter because it used to change the result: exclusions were matched
     * against the absolute path, so a root called `embedded-udytils` excluded its own subtree.
     */
    private fun parseTree(rootName: String): ModuleGraph {
        val root = File(createTempDirectory("module-graph").toFile(), rootName).apply { mkdirs() }
        fun write(path: String, vararg lines: String) {
            val file = File(root, path)
            file.parentFile.mkdirs()
            file.writeText(lines.joinToString("\n"))
        }
        write("settings.gradle.kts", """include(":core")""", """include(":ui")""")
        write("build.gradle.kts", """    listOf(project(":core"), project(":ui")) // root: not a dependency""")
        write("core/build.gradle.kts", "    // no dependencies")
        write("ui/build.gradle.kts", """    api(project(":core"))""")
        write("embedded-enro/enro/build.gradle.kts", """    api(project(":enro-core"))""")
        write("build/generated/build.gradle.kts", """    api(project(":generated"))""")
        write(".gradle/tmp/build.gradle.kts", """    api(project(":cached"))""")
        return ModuleGraph.parseFrom(root)
    }

    @Test
    fun `parses edges from a project whose root directory is itself named embedded-`() {
        // The regression: udytils is consumed as `<consumer>/embedded-udytils`, and matching the
        // exclusions against the absolute path excluded every one of its own build scripts, leaving
        // an empty graph in which every module-graph rule passed vacuously.
        val edges = parseTree("embedded-udytils").edges
        assertTrue(edges.isNotEmpty(), "the root's own name must not exclude the project's own scripts")
        assertEquals(
            parseTree("udytils").edges.map { it.from to it.to },
            edges.map { it.from to it.to },
        )
    }

    @Test
    fun `the walk skips composite submodules, build output and the root script`() {
        val edges = parseTree("embedded-udytils").edges
        assertEquals(listOf(":ui" to ":core"), edges.map { it.from to it.to })
    }
}
