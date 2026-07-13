package dev.isaacudy.udytils.architecture.docs

import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.docs.fixtures.GroupInOwnPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Layer docs are named after each group's sub-package ([DocSources.outputName]); two groups in one
 * package used to silently render to the same file, leaving the golden test failing with the same
 * path reported stale repeatedly and no hint why. The render must refuse loudly instead.
 */
class LayerDocNameCollisionTest {

    private object FirstGroup : RuleGroup()
    private object SecondGroup : RuleGroup()

    @Test
    fun `two groups in the same package collide`() {
        val errors = layerDocNameCollisions(listOf(FirstGroup, SecondGroup))
        assertEquals(1, errors.size)
        assertTrue("FirstGroup" in errors.single() && "SecondGroup" in errors.single())
        assertTrue("docs.md" in errors.single(), "the colliding file name should be spelled out: ${errors.single()}")
    }

    @Test
    fun `groups in distinct packages do not collide`() {
        assertEquals(
            emptyList(),
            layerDocNameCollisions(listOf(FirstGroup, GroupInOwnPackage)),
        )
    }
}
