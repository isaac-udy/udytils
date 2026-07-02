package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
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
    val sourceLinkBase = "../".repeat(config.outputDir.split('/').size).dropLast(1) + "/" + config.sourceRoot
    val regenerate = "Regenerate with `${config.regenerateCommand}`."

    val layerDocs = sources.layers.map { layer ->
        val content = renderLayerDoc(layer, sources::sourcePath, sourceLinkBase, errors)
        val note = "Sources: @Describe annotations in the Kotlin catalog in `${sources.packageDirPath(layer.group)}/` " +
            "(narrative + rules), plus the `*.examples.md` files beside it."
        GeneratedDoc("${config.outputDir}/${sources.outputName(layer.group)}.md", banner(note, regenerate) + content)
    }
    val standaloneDocs = sources.standalone.map { file ->
        val note = "Narrative source: `${sources.sourcePath(file)}`; rule content comes from the rule catalog."
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
            val note = "Shipped by the architecture framework (`dev.isaacudy.udytils:architecture-core`); " +
                "override it by adding `$name` to the catalog root."
            GeneratedDoc("${config.outputDir}/$name", banner(note, regenerate) + content)
        }
    val ruleIndex = GeneratedDoc(
        "${config.outputDir}/rule-index.md",
        banner("Generated entirely from the rule catalog.", regenerate) + renderRuleIndexDoc(definition, sourceLinkBase),
    )

    val definitionPath = "${config.sourceRoot}/${definition.javaClass.packageName.replace('.', '/')}/${definition.name}.kt"
    val linked = layerDocs + standaloneDocs + frameworkDocs + ruleIndex
    val toc = linked.map { it.relativePath to titleOf(it) }
    val ruleToc = layerDocs.map { it.relativePath to titleOf(it) }
    val referenceToc = (listOf(ruleIndex) + standaloneDocs + frameworkDocs).map { it.relativePath to titleOf(it) }
    val readme = GeneratedDoc(
        "README.md",
        banner("Intro source: the @Describe annotation on `${definition.name}` (`$definitionPath`); the standard sections come from the framework.", regenerate) +
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

/** GitHub-style note alert, placed above the document title. */
private fun banner(sourceNote: String, regenerate: String): String = buildString {
    appendLine("> [!NOTE]")
    appendLine("> **This file is generated — do not edit it by hand.**")
    appendLine("> $sourceNote")
    appendLine("> $regenerate")
    appendLine()
}
