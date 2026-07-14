package dev.isaacudy.udytils.architecture

import java.io.File

/**
 * Parses the project's `build.gradle.kts` files into a [ModuleGraph] of declared dependency edges.
 *
 * Lifted from the original `ModuleDependencyTests`: walks every build script (excluding the
 * `embedded-*` composite submodules, `build/` output, `.gradle/`, and the root script) and records
 * an edge from the script's own module to each referenced module — carrying the line number and any
 * `// architecture-exception:` rule ids attached immediately above the line. Two dependency
 * notations are read:
 *
 *  - `projects.x.y.z` typesafe accessors (camelCase mapped to the kebab-case project name), and
 *  - `project(":x:y")` string notation, taken verbatim as the referenced project path.
 *
 * The edge's `from` is the referenced-by-directory project. Repositories that remap a project's
 * directory in `settings.gradle.kts` (`project(":postgres-core").projectDir = file("postgres/core")`)
 * are handled: the remapped Gradle path is used, so `from` and `to` agree on one naming scheme.
 *
 * The root build script is skipped deliberately — cross-cutting blocks there (e.g.
 * `dependencySubstitution` lists) name every project without depending on any of them.
 */
fun ModuleGraph.Companion.parse(): ModuleGraph {
    val root = locateProjectRoot()
    val remappings = File(root, "settings.gradle.kts")
        .takeIf { it.exists() }
        ?.let { projectDirRemappings(it.readLines()) }
        .orEmpty()
    val edges = root.walkTopDown()
        .filter { it.name == "build.gradle.kts" }
        .filter { !it.absolutePath.contains("/embedded-") }
        .filter { !it.absolutePath.contains("/build/") }
        .filter { !it.absolutePath.contains("/.gradle/") }
        .filter { it.parentFile != root } // skip the root build.gradle.kts
        .flatMap { it.toEdges(root, remappings) }
        .toList()
    return ModuleGraph(edges)
}

private val TYPESAFE_ACCESSOR = Regex("""\bprojects(?:\.[a-zA-Z][a-zA-Z0-9]*)+""")

/** `project(":x:y")` / `rootProject.project(":x:y")` string-notation dependency. */
private val STRING_NOTATION = Regex("""\bproject\(\s*"(:[A-Za-z0-9_:.\-]+)"\s*\)""")

/** `project(":name").projectDir = file("some/dir")` remapping lines in `settings.gradle.kts`. */
private val PROJECT_DIR_REMAPPING =
    Regex("""project\("(:[A-Za-z0-9_:.\-]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)""")

/**
 * Directory → Gradle-path remappings declared in `settings.gradle.kts`, so a build script's `from`
 * uses the project's real (possibly flat) name rather than its directory-derived one.
 *
 * `internal` for the same reason as [collectExemptions]: the public entry point walks the real
 * filesystem, which is not unit-testable.
 */
internal fun projectDirRemappings(settingsLines: List<String>): Map<String, String> =
    settingsLines
        .mapNotNull { line -> PROJECT_DIR_REMAPPING.find(line) }
        .associate { match -> match.groupValues[2] to match.groupValues[1] }

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

private fun File.toEdges(root: File, remappings: Map<String, String>): List<ModuleEdge> {
    val dir = this.parentFile.relativeTo(root).invariantSeparatorsPath
    val from = remappings[dir] ?: (":" + dir.replace('/', ':'))
    val relFile = this.relativeTo(root).invariantSeparatorsPath
    return edgesFromLines(from, relFile, this.readLines())
}

/**
 * The dependency edges declared in one build script's [lines]. `internal` for the same reason as
 * [collectExemptions]: the public entry point walks the real filesystem.
 */
internal fun edgesFromLines(from: String, relFile: String, lines: List<String>): List<ModuleEdge> {
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
        for (match in STRING_NOTATION.findAll(line)) {
            edges += ModuleEdge(
                from = from,
                to = match.groupValues[1],
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
