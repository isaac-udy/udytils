package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.RuleGroup
import java.io.File

/** One generated documentation file, addressed relative to the architecture module root. */
data class GeneratedDoc(val relativePath: String, val content: String)

/**
 * Renders the complete generated doc set for [definition]:
 *
 *  - `<outputDir>/<layer>.md` — compiled per layer from `@Describe` + examples files ([renderLayerDoc])
 *  - `<outputDir>/<name>.md` — each standalone source in the catalog root, markers expanded
 *  - `<outputDir>/rule-index.md` — entirely from the catalog
 *  - `README.md` — from the definition object's `@Describe` text (`{{toc}}` expands)
 *
 * Any validation problem (unresolvable marker or fragment, stale prose id, broken link) fails the
 * render with every error listed — the golden test therefore refuses to regenerate from broken
 * sources.
 */
fun renderArchitectureDocs(definition: ArchitectureDefinition, moduleRoot: File): List<GeneratedDoc> {
    val config = definition.docs
    val catalog = CatalogIndex(definition)
    val sources = DocSources.discover(moduleRoot, definition)
    val errors = mutableListOf<String>()
    errors += layerDocNameCollisions(definition.groups)
    val sourceLinkBase = "../".repeat(config.outputDir.split('/').size).dropLast(1) + "/" + config.sourceRoot
    val regenerate = "Regenerate with `${config.regenerateCommand}`."

    val layerDocs = sources.layers.map { layer ->
        val content = renderLayerDoc(layer, sources::sourcePath, sourceLinkBase, errors)
        val note = "Generated from the `@Describe` annotations in `${sources.packageDirPath(layer.group)}/` " +
            "and the `*.examples.md` files beside them."
        GeneratedDoc("${config.outputDir}/${sources.outputName(layer.group)}.md", banner(note, regenerate) + content)
    }
    val standaloneDocs = sources.standalone.map { file ->
        val note = "Generated from `${sources.sourcePath(file)}`."
        GeneratedDoc(
            "${config.outputDir}/${file.name}",
            banner(note, regenerate) + expandMarkers(file.readText(), catalog, sources.sourcePath(file), errors),
        )
    }
    // Framework-shipped docs (authoring, exceptions) — a consumer standalone file of the same
    // name overrides the shipped version.
    val consumerNames = sources.standalone.map { it.name }.toSet()
    val frameworkDocs = frameworkStandaloneDocs(definition)
        .filterKeys { it !in consumerNames }
        .map { (name, content) ->
            val note = "Provided by the udytils architecture system. To override it, add a file named " +
                "`$name` next to this project's rule definitions."
            GeneratedDoc("${config.outputDir}/$name", banner(note, regenerate) + content)
        }
    val ruleIndex = GeneratedDoc(
        "${config.outputDir}/rule-index.md",
        banner("Generated from the RuleGroups and Constructs in this project.", regenerate) + renderRuleIndexDoc(definition, sourceLinkBase),
    )

    val definitionPath = "${config.sourceRoot}/${definition.javaClass.packageName.replace('.', '/')}/${definition.name}.kt"
    val linked = layerDocs + standaloneDocs + frameworkDocs + ruleIndex
    val toc = linked.map { it.relativePath to titleOf(it) }
    val ruleToc = layerDocs.map { it.relativePath to titleOf(it) }
    val referenceToc = (listOf(ruleIndex) + standaloneDocs + frameworkDocs).map { it.relativePath to titleOf(it) }
    val readme = GeneratedDoc(
        "README.md",
        banner("The introduction comes from the `@Describe` annotation on `${definition.name}` (`$definitionPath`); the remaining sections are provided by the udytils architecture system.", regenerate) +
            expandMarkers(
                definition.readme,
                catalog,
                "README template (@Describe on ${definition.name})",
                errors,
                toc = toc,
            ).trimEnd() + "\n\n" +
            renderReadmeStandardSections(definition, catalog, ruleToc, referenceToc),
    )

    val all = listOf(readme) + linked
    validateProseRuleIds(all, catalog, errors)
    validateLinks(all, moduleRoot, errors)
    check(errors.isEmpty()) {
        "Architecture doc generation failed:\n" + errors.joinToString("\n") { " - $it" }
    }
    return all
}

/**
 * Layer docs are named after each group's sub-package, so two groups sharing a package would
 * silently render to the same file — the last one written wins and the golden test then reports
 * the same path stale over and over. Fail the render loudly instead. `internal` so the check is
 * unit-testable without a filesystem.
 */
internal fun layerDocNameCollisions(groups: List<RuleGroup>): List<String> =
    groups
        .groupBy { it.javaClass.packageName.substringAfterLast('.') }
        .filterValues { it.size > 1 }
        .map { (name, collided) ->
            "layer doc name collision: ${collided.joinToString(", ") { it.id }} would all render to " +
                "`$name.md`. Layer docs are named after the group's package — give each RuleGroup " +
                "its own package."
        }

/** GitHub-style note alert, placed above the document title. */
private fun banner(sourceNote: String, regenerate: String): String = buildString {
    appendLine("> [!NOTE]")
    appendLine("> **This file is generated. Do not edit it directly.**")
    appendLine("> $sourceNote")
    appendLine("> $regenerate")
    appendLine()
}
