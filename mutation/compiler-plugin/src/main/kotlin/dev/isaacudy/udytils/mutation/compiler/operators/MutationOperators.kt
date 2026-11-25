package dev.isaacudy.udytils.mutation.compiler.operators

/** Every operator the compiler plugin implements. */
object MutationOperators {
    val all: List<MutationOperator> = listOf(
        NegateCondition,
        BooleanReturn,
        EmptyReturn,
        RemoveCall,
        ConditionalsBoundary,
        LogicalConnector,
        Elvis,
    )

    /** The operators compiled in when the build names none. */
    val defaults: List<MutationOperator> = all

    fun select(ids: Collection<String>): List<MutationOperator> {
        if (ids.isEmpty()) return defaults
        val unknown = ids.filter { id -> all.none { it.id == id } }
        require(unknown.isEmpty()) {
            "Unknown mutation operator ${unknown.joinToString { "\"$it\"" }}. Operators: ${all.joinToString { it.id }}."
        }
        return all.filter { it.id in ids }
    }
}
