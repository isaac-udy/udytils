package dev.isaacudy.udytils.architecture

/**
 * A kind of test the catalog's consumer defines, such as a unit test or a snapshot test. The engine
 * treats [id] as opaque: a coverage tool maps it to the test tasks that run that category.
 */
class TestCategory(val id: String, val name: String) {
    init {
        require(ID.matches(id)) { "TestCategory id `$id` must match ${ID.pattern}" }
    }

    override fun equals(other: Any?): Boolean = other is TestCategory && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    private companion object {
        val ID = Regex("[a-z][a-z0-9-]*")
    }
}

/** Which test categories a [Construct]'s declarations are expected to be covered by. */
sealed class TestExpectation {
    /** The construct declares no expectation. */
    data object Unspecified : TestExpectation()

    /** No test is expected to cover the construct, for the stated [reason]. */
    data class None(val reason: String) : TestExpectation() {
        init {
            require(reason.isNotBlank()) { "TestExpectation.none(…) requires a reason" }
        }
    }

    /** A test in any one of [categories] covers the construct. */
    data class AnyOf(val categories: List<TestCategory>) : TestExpectation() {
        init {
            require(categories.isNotEmpty()) { "TestExpectation.anyOf(…) requires at least one TestCategory" }
            require(categories.distinct().size == categories.size) { "TestExpectation.anyOf(…) lists a TestCategory twice: $categories" }
        }
    }

    companion object {
        fun anyOf(vararg categories: TestCategory): TestExpectation = AnyOf(categories.toList())
        fun none(reason: String): TestExpectation = None(reason)
    }
}
