package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration

/**
 * The single configuration point a consumer provides: which rule groups make up the architecture,
 * what code the rules govern, and how the generated docs are laid out. Subclass as an `object`
 * carrying the README template in its [Describe]:
 *
 *     @Describe("# My Architecture …")
 *     object MyArchitecture : ArchitectureDefinition(
 *         groups = listOf(DomainLayer, …),
 *         scope = { Konsist.scopeFromProject().slice { … } },
 *         membership = { it.residesInGovernedCode() },
 *         docs = DocsConfig(
 *             module = "tools/architecture",
 *             regenerateCommand = "./gradlew :tools:architecture:test -PupdateArchitectureDocs=true",
 *         ),
 *     )
 */
abstract class ArchitectureDefinition(
    val groups: List<RuleGroup>,
    /** Builds the Konsist scope the rules verify — the consumer decides what code is governed. */
    val scope: () -> KoScope,
    /**
     * The classifiable universe for the cross-layer membership rule: every declaration matching
     * this predicate must belong to exactly one construct. Null disables the membership rule
     * (each group's exhaustiveness check still applies inside its `inPackage`).
     */
    val membership: ((KoBaseDeclaration) -> Boolean)? = null,
    val docs: DocsConfig,
) {
    /** The definition object's name (e.g. `UkptArchitecture`) — used in banners and discovery. */
    val name: String get() = this::class.simpleName ?: "Architecture"

    /** The README template: this object's [Describe] text (`{{toc}}` expands). */
    val readme: String
        get() = this::class.describeText()
            ?: error("$name needs a @Describe with the README template")
}

/** Where the catalog and generated docs live, and how the docs are regenerated. */
class DocsConfig(
    /** Repo-relative path of the module owning the catalog + docs, e.g. `platform/common/architecture`. */
    val module: String,
    /** Module-relative source root the catalog packages live under. */
    val sourceRoot: String = "src/main/kotlin",
    /** Module-relative directory the generated docs are written to. */
    val outputDir: String = "docs",
    /** Override for the docs-regeneration command shown in banners; defaults to the plugin task. */
    regenerateCommand: String? = null,
    /** Override for the verification command shown in the README; defaults to the plugin task. */
    verifyCommand: String? = null,
    /** The environment variable the golden test reads to switch into regeneration mode. */
    val regenerateFlag: String = "UPDATE_ARCHITECTURE_DOCS",
) {
    /** `platform/common/architecture` → `:platform:common:architecture`. */
    val gradlePath: String = ":" + module.replace('/', ':')

    val regenerateCommand: String = regenerateCommand ?: "./gradlew $gradlePath:updateArchitectureDocumentation"
    val verifyCommand: String = verifyCommand ?: "./gradlew $gradlePath:verifyArchitecture"
}
