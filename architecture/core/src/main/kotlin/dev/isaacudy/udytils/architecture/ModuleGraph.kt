package dev.isaacudy.udytils.architecture

/**
 * A declared module-dependency edge parsed from a `build.gradle.kts` typesafe project accessor
 * (e.g. `projects.app.server` in `:feature:core:server`). Carries the source location and any
 * `// architecture-exception:` rule ids attached to the line, so build-graph rules can both
 * report a precise location and honour the comment-based exemption channel.
 */
data class ModuleEdge(
    val from: String,                 // e.g. ":feature:core:server"
    val to: String,                   // e.g. ":app:server"
    val file: String,                 // build.gradle.kts path (relative to project root)
    val line: Int,                    // 1-based line number of the dependency
    val exemptRuleIds: Set<String>,   // rule ids from `// architecture-exception:` comments above the line
    val configuration: String? = null, // e.g. "implementation", "testImplementation", "kspJvm"
    val sourceSet: String? = null,     // the enclosing KMP source-set block, e.g. "commonTest"
) {
    val location: String get() = "$from -> $to ($file:$line)"

    /** Whether the dependency is declared for test sources only (a test source set or test configuration). */
    val isTest: Boolean
        get() = sourceSet?.let { it == "test" || it.endsWith("Test") } == true ||
            configuration?.let { TEST_CONFIGURATION.containsMatchIn(it) } == true
}

private val TEST_CONFIGURATION = Regex("^test|Test")

/**
 * All declared inter-module dependency edges in the project, excluding the `embedded-*`
 * composite-build submodules and generated `build/` output. Built once per [verify] run.
 *
 * [parse] lives in `ModuleGraphParser.kt` (lifted from the original ModuleDependencyTests).
 */
class ModuleGraph(
    val edges: List<ModuleEdge>,
) {
    companion object
}
