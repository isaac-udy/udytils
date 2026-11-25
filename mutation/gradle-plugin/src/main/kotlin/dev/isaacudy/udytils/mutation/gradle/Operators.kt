package dev.isaacudy.udytils.mutation.gradle

/**
 * The operator IDs the compiler plugin implements, in the compiler plugin's order.
 * `OperatorsTest` checks this list against the compiler plugin's `MutationOperators`.
 */
internal object Operators {
    val all = listOf(
        "negate-condition",
        "boolean-return",
        "empty-return",
        "remove-call",
        "conditionals-boundary",
        "logical-connector",
        "elvis",
    )

    /** The operators compiled in when the `mutation { }` block names none. */
    val defaults = all

    /** Fails for an ID the compiler plugin does not implement, naming the [source] of the ID. */
    fun requireKnown(ids: Collection<String>, source: String) {
        val unknown = ids.filter { it !in all }
        require(unknown.isEmpty()) {
            "$source names unknown mutation operator ${unknown.joinToString { "\"$it\"" }}. Operators: ${all.joinToString()}."
        }
    }

    /**
     * Fails for an unknown operator, or for an operator that a rule or `-Pmutation.operators` names
     * but [compiled] does not include.
     */
    fun checkSelection(compiled: Set<String>, rules: List<RuleSpec>, runOperators: List<String>) {
        requireKnown(compiled, "mutation { operators }")
        val requested = rules.map { "rule(\"${it.id}\") { operators() }" to it.operators } +
            listOf("-P${MutationPlugin.OPERATORS_PROPERTY}" to runOperators)
        for ((source, ids) in requested) {
            requireKnown(ids, source)
            val missing = ids.filter { it !in compiled }
            require(missing.isEmpty()) {
                "$source names ${missing.joinToString { "\"$it\"" }}, which the build does not compile in. " +
                    "Add the operator to mutation { operators(...) }. Compiled operators: ${compiled.sorted().joinToString()}."
            }
        }
    }
}
