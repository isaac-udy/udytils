package dev.isaacudy.udytils.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
