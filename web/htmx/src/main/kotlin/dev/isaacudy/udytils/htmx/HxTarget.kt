package dev.isaacudy.udytils.htmx

/** An htmx extended CSS selector, as accepted by `hx-target`, `hx-include` and `hx-disabled-elt`. */
class HxTarget private constructor(val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is HxTarget && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        val This: HxTarget = HxTarget("this")

        fun element(id: ElementId): HxTarget = HxTarget(id.selector)

        fun closest(selector: String): HxTarget = HxTarget("closest ${selector.requireSelector()}")

        fun find(selector: String): HxTarget = HxTarget("find ${selector.requireSelector()}")

        fun next(selector: String): HxTarget = HxTarget("next ${selector.requireSelector()}")

        fun previous(selector: String): HxTarget = HxTarget("previous ${selector.requireSelector()}")

        private fun String.requireSelector(): String = also {
            require(isNotBlank()) { "A selector must not be blank" }
        }
    }
}
