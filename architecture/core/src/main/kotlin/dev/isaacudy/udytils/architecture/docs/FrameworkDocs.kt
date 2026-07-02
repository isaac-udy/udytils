package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.Rule

/**
 * Documentation the framework owns so consumers never redeclare it: the standard README sections
 * (appended after the definition's own `@Describe` narrative) and the shipped standalone docs
 * (`authoring.md`, `exceptions.md`) — emitted into every consumer's doc set, overridable by a
 * same-named standalone file in the consumer's catalog.
 */

/** The framework-shipped standalone docs, templated with the consumer's commands. */
internal fun frameworkStandaloneDocs(definition: ArchitectureDefinition): Map<String, String> =
    listOf("authoring.md", "exceptions.md").associateWith { name ->
        val resource = "/dev/isaacudy/udytils/architecture/docs/$name"
        val text = object {}.javaClass.getResourceAsStream(resource)?.bufferedReader()?.readText()
            ?: error("Missing framework doc resource: $resource")
        text
            .replace("{{regenerateCommand}}", definition.docs.regenerateCommand)
            .replace("{{verifyCommand}}", definition.docs.verifyCommand)
    }

/**
 * The standard README sections, generated so every consumer documents the *method* identically:
 * How this works, Running the checks, The documents (TOC), Rule IDs (with examples and the groups
 * table derived from the consumer's catalog), and the Enforcement status legend.
 */
internal fun renderReadmeStandardSections(
    definition: ArchitectureDefinition,
    catalog: CatalogIndex,
    toc: List<Pair<String, String>>,
): String = buildString {
    val config = definition.docs
    val catalogPath = "${config.sourceRoot}/${definition.javaClass.packageName.replace('.', '/')}"

    appendLine("## How this works")
    appendLine()
    appendLine("- The **rules** are Kotlin (checked with Konsist), maintained by hand: one `RuleGroup` object per layer, one top-level `Construct<Group>` object per code shape (in its own file, listed in the group's `constructs`), a rule or guidance property on each. They live in [`$catalogPath/`]($catalogPath).")
    appendLine("- The **narrative** lives in the catalog too: `@Describe(\"…\")` annotations carry the documentation text for every group, construct, rule, and guidance entry — including this README's introduction, which is the annotation on `${definition.name}`.")
    appendLine("- **Examples** are markdown files next to the rules they belong to: `<Construct>.examples.md` beside `<Construct>.kt` holds the example blocks for that construct, rendered after its rules.")
    appendLine("- The **documentation** — this README and everything under `${config.outputDir}/` — is **generated** from those sources. Never edit the generated files; edit the catalog or an examples file, then regenerate.")
    appendLine("- Read [authoring](${config.outputDir}/authoring.md) before adding rules: what should be a requirement vs a rule vs guidance, what audits are for, and the language conventions.")
    appendLine()
    appendLine("## Running the checks")
    appendLine()
    appendLine("- Run: `${config.verifyCommand}`")
    appendLine("- Expect `BUILD SUCCESSFUL`. The task always re-executes — no `--rerun-tasks` needed.")
    appendLine("- Every rule reports as its own nested test: `<Layer> › <Construct> › <rule>`, so a failure names the exact rule.")
    appendLine("- HTML report: `${config.module}/build/reports/tests/verifyArchitecture/index.html`.")
    appendLine("- After changing the catalog or an examples file, regenerate the docs with `${config.regenerateCommand}`. The suite fails if the generated docs drift from the sources, if prose references a rule id that doesn't exist, or if a link/anchor is broken.")
    appendLine()
    appendLine("## The documents")
    appendLine()
    toc.forEach { (path, title) -> appendLine("- [$title]($path)") }
    appendLine()
    appendLine("## Rule IDs")
    appendLine()
    appendLine("Every rule and construct has a stable ID: the **path of the object/property names that declare it**.")
    appendLine()
    ruleIdExamples(catalog).takeIf { it.isNotEmpty() }?.let { examples ->
        appendLine("| ID | Reads as |")
        appendLine("| --- | --- |")
        examples.forEach { (id, readsAs) -> appendLine("| `$id` | $readsAs |") }
        appendLine()
    }
    appendLine("- Groups and constructs are PascalCase `object`s; rules are camelCase properties on them.")
    appendLine("- A construct's **requirements** (the predicates that decide whether a declaration *is* that construct) are not individually identified — the construct is the unit.")
    appendLine("- Test failures, the [rule index](${config.outputDir}/rule-index.md), and [architecture exceptions](${config.outputDir}/exceptions.md) all reference rules by this path.")
    appendLine("- The layer docs don't repeat ids next to each rule — to find a rule's id (e.g. for an exception), look it up by statement in the [rule index](${config.outputDir}/rule-index.md) or in the layer's `.kt`.")
    appendLine()
    appendLine("The groups:")
    appendLine()
    appendLine("| Group | Doc |")
    appendLine("| --- | --- |")
    catalog.groups.forEach { group ->
        val docName = group.javaClass.packageName.substringAfterLast('.')
        appendLine("| `${group.id}` | [${config.outputDir}/$docName.md](${config.outputDir}/$docName.md) |")
    }
    appendLine()
    appendLine("## Enforcement status")
    appendLine()
    appendLine("Each entry's status is **derived from how it is declared in the catalog**, so it can never disagree with reality:")
    appendLine()
    appendLine("| Status | Declared as | Meaning |")
    appendLine("| --- | --- | --- |")
    appendLine("| `tested` | a `rule` ending in `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` | A check enforces the rule and fails citing its id. `enforcedBy(...)` rules are enforced transitively by the rules they name. |")
    appendLine("| `construct` | a `Construct(...)`'s requirement predicates | A classification. A declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check. |")
    appendLine("| `unverifiable` | a `rule` ending in `unverifiable()` | A mandatory rule that static analysis can't reliably check — enforced by review. Renders under **Rules** with an automatic \"not automatically verifiable\" note, and may carry an audit. |")
    appendLine("| `guidance` | `@Describe(\"…\") val x by guidance` | An advisory convention (may/should). Enforced by review; renders under **Guidance**, separate from **Rules**. Guidance may declare an `audit { }` — a check that never fails the build but reports non-conforming code in the test output. |")
    appendLine("| `codegen` | a `rule` ending in `codegen()` | Guaranteed by a code generator — nothing in source for the checks to scan. |")
}.trimEnd() + "\n"

/** Example rows for the Rule IDs table, drawn from the consumer's own catalog. */
private fun ruleIdExamples(catalog: CatalogIndex): List<Pair<String, String>> = buildList {
    val construct = catalog.groups.flatMap { it.constructs }.firstOrNull()
    if (construct != null) {
        val groupId = construct.id.substringBefore('.')
        add(construct.id to "the `${construct.id.substringAfterLast('.')}` construct (a classification) in the `$groupId` group")
        constructRuleExample(catalog)?.let { rule ->
            add(rule.id to "the `${rule.id.substringAfterLast('.')}` rule of the `${rule.id.split('.')[1]}` construct")
        }
    }
    catalog.groups.firstNotNullOfOrNull { group -> group.declaredRules.firstOrNull() }?.let { rule ->
        add(rule.id to "a layer-level rule (not tied to a construct)")
    }
}

private fun constructRuleExample(catalog: CatalogIndex): Rule? =
    catalog.groups.flatMap { it.constructs }.firstNotNullOfOrNull { it.declaredRules.firstOrNull() }
