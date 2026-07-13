package dev.isaacudy.udytils.architecture

import java.io.File

/**
 * Parses the project's `build.gradle.kts` files into a [ModuleGraph] of declared dependency edges.
 *
 * Lifted from the original `ModuleDependencyTests`: walks every build script (excluding the
 * `embedded-*` composite submodules, `build/` output, `.gradle/`, and the root script), finds
 * `projects.x.y.z` typesafe accessors, and records each as an edge from the script's own module to
 * the referenced module — carrying the line number and any `// architecture-exception:` rule ids
 * attached immediately above the line.
 */
fun ModuleGraph.Companion.parse(): ModuleGraph {
    val root = locateProjectRoot()
    val edges = root.walkTopDown()
        .filter { it.name == "build.gradle.kts" }
        .filter { !it.absolutePath.contains("/embedded-") }
        .filter { !it.absolutePath.contains("/build/") }
        .filter { !it.absolutePath.contains("/.gradle/") }
        .filter { it.parentFile != root } // skip the root build.gradle.kts
        .flatMap { it.toEdges(root) }
        .toList()
    return ModuleGraph(edges)
}

private val TYPESAFE_ACCESSOR = Regex("""\bprojects(?:\.[a-zA-Z][a-zA-Z0-9]*)+""")

/**
 * The rule ids on an `// architecture-exception:` marker line: one or more, comma-separated.
 *
 * An id is either a dotted path id as the rule catalog actually emits them
 * (`ModuleRules.platformNotFeature`, `DomainLayer.DomainInterface.primaryReturnType`) or a legacy
 * dashed token (`R-MOD-10`). The previous pattern (`[A-Z0-9\-,\s]+`) accepted only upper-case
 * letters, so it could not capture a path id at all — it matched just the leading `M` of
 * `ModuleRules.…` and silently produced a bogus id, meaning no build-file exemption ever applied to
 * a module-graph rule. Even this file's own KDoc example failed to parse.
 *
 * The id character class deliberately excludes spaces, and the comma alternation is explicit, so a
 * trailing prose reason on the marker line terminates the match instead of being swallowed into an
 * id. Reasons are normally written on the following comment lines (see `docs/exceptions.md`), which
 * [collectExemptions] walks past harmlessly.
 */
private val EXCEPTION_COMMENT = Regex(
    """//\s*architecture-exception:\s*([A-Za-z0-9_.\-]+(?:\s*,\s*[A-Za-z0-9_.\-]+)*)""",
)

private fun locateProjectRoot(): File {
    var current: File? = File(".").canonicalFile
    while (current != null && !File(current, "settings.gradle.kts").exists()) {
        current = current.parentFile
    }
    return requireNotNull(current) { "Could not locate project root (no settings.gradle.kts found)" }
}

private fun File.toEdges(root: File): List<ModuleEdge> {
    val from = ":" + this.parentFile.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
    val relFile = this.relativeTo(root).invariantSeparatorsPath
    val lines = this.readLines()
    val edges = mutableListOf<ModuleEdge>()
    for ((index, line) in lines.withIndex()) {
        for (match in TYPESAFE_ACCESSOR.findAll(line)) {
            edges += ModuleEdge(
                from = from,
                to = accessorToModulePath(match.value),
                file = relFile,
                line = index + 1,
                exemptRuleIds = collectExemptions(lines, index),
            )
        }
    }
    return edges.distinctBy { Triple(it.from, it.to, it.line) }
}

/** `projects.platform.common.textSimilarity` → `:platform:common:text-similarity`. */
private fun accessorToModulePath(accessor: String): String =
    accessor.removePrefix("projects.").split(".")
        .joinToString(":", prefix = ":") { camelToKebab(it) }

private fun camelToKebab(name: String): String =
    name.fold(StringBuilder()) { acc, c ->
        if (c.isUpperCase() && acc.isNotEmpty()) acc.append('-')
        acc.append(c.lowercaseChar())
    }.toString()

/**
 * Walk back from [index] gathering `// architecture-exception:` ids until the first non-comment line.
 *
 * `internal` rather than `private` so [ModuleGraphParserTest] can exercise it directly: the public
 * entry point ([ModuleGraph.Companion.parse]) walks the real filesystem, which is not unit-testable.
 */
internal fun collectExemptions(lines: List<String>, index: Int): Set<String> {
    val ids = mutableSetOf<String>()
    var i = index - 1
    while (i >= 0) {
        val trimmed = lines[i].trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("//")) break
        EXCEPTION_COMMENT.find(trimmed)?.let { match ->
            match.groupValues[1].split(',').forEach { id ->
                id.trim().takeIf { it.isNotEmpty() }?.let(ids::add)
            }
        }
        i--
    }
    return ids
}
