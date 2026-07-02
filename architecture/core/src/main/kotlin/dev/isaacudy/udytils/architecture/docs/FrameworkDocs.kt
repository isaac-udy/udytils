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

/** One-line hooks for the framework-known reference docs; other standalone docs render bare. */
private val referenceHooks = mapOf(
    "rule-index.md" to "all rules, ids, and enforcement",
    "exceptions.md" to "exempting code from rules",
    "authoring.md" to "conventions for new rules",
)

/**
 * The standard README sections, generated so every consumer documents the *method* identically:
 * the doc index (rule docs, then reference docs), followed by "Architecture Testing System" — an
 * H1 section explaining the catalog/testing/doc generation, with Run the tests, Regenerate the
 * documentation, and Rule IDs (examples drawn from the consumer's catalog) nested under it.
 *
 * The template is written at column 0 because `trimIndent` can't cope with interpolated
 * multi-line blocks (their lines carry no indentation, so nothing would be trimmed).
 */
internal fun renderReadmeStandardSections(
    definition: ArchitectureDefinition,
    catalog: CatalogIndex,
    ruleDocs: List<Pair<String, String>>,
    referenceDocs: List<Pair<String, String>>,
): String {
    val config = definition.docs
    val catalogPath = "${config.sourceRoot}/${definition.javaClass.packageName.replace('.', '/')}"
    val rules = ruleDocs.joinToString("\n") { (path, title) -> "- [$title]($path)" }
    val reference = referenceDocs.joinToString("\n") { (path, title) ->
        val hook = referenceHooks[path.substringAfterLast('/')]
        if (hook == null) "- [$title]($path)" else "- [$title]($path) — $hook"
    }
    // Carries its own blank line on each side so an empty catalog still leaves clean spacing.
    val idExamplesTable = ruleIdExamples(catalog)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(
            separator = "\n",
            prefix = "\n| ID | Reads as |\n| --- | --- |\n",
            postfix = "\n",
        ) { (id, readsAs) -> "| `$id` | $readsAs |" }
        .orEmpty()

    return """
## Rules

$rules

## Reference

$reference

---

# Architecture Testing System

This project uses the [udytils architecture system](https://github.com/isaac-udy/udytils) to define, test, and document its architecture rules. Rules are declared in Kotlin code, built on the Konsist library, and structured using the following types:

- **RuleGroup** — names and defines a set of Constructs, Rules, and Guidance.
  - A RuleGroup can be (optionally) scoped to a particular package pattern
- **Construct** — names and defines the rules for a code-level construct (such as a class, interface, function or property).  
  - A Construct must be associated with a RuleGroup.
  - A Construct defines a set of requirements in its constructor. If a piece of code matches the requirements for a particular Construct, it will be required to meet the rules associated with that construct. 
  - To provide example code for a Construct, create a `<Construct>.examples.md` file next to the associated `<Construct.kt>` file
- **Rule** — a mandatory statement about a `Construct` or `RuleGroup`.
- **Guidance** — an advisory statement about a `Construct` or `RuleGroup`.
 
Documentation for RuleGroups and Constructs is recorded by annotating the RuleGroup or Construct with the `@Describe` annotation. Documentation for Rules and Guidance is also provided by annotating the Rule or Guidance statement with `@Describe` but Rules and Guidance also provide the ability to add "rationale" and "notes" through functions in their builder definitions.

This README and everything under `${config.outputDir}/` is generated from the catalog. Never edit these files directly — edit the catalog and regenerate. Read [authoring](${config.outputDir}/authoring.md) before adding rules.

## Run the tests

```
${config.verifyCommand}
```

## Regenerate the documentation

```
${config.regenerateCommand}
```

Run this after changing the catalog or an examples file. The tests fail if the generated documentation is manually edited, or if the documentation references a rule that doesn't exist.

## Rule IDs

Every rule and construct has a stable id: the path of the object/property names that declare it.
$idExamplesTable
Test failures, the [rule index](${config.outputDir}/rule-index.md), and [architecture exceptions](${config.outputDir}/exceptions.md) reference rules by id. Requirements don't have their own ids — they belong to their construct.
""".trim() + "\n"
}

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
