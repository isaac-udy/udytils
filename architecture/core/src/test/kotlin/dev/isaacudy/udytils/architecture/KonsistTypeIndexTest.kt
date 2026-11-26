package dev.isaacudy.udytils.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class KonsistTypeIndexTest {
    @Test
    fun `this Konsist version stores every replaced index property in a Lazy with an initializer field`() {
        val initializers = konsistTypeIndexInitializers(KONSIST_TYPE_INDEX_PROPERTIES)
        assertEquals(KONSIST_TYPE_INDEX_PROPERTIES.toSet(), initializers.keys)
    }
}
