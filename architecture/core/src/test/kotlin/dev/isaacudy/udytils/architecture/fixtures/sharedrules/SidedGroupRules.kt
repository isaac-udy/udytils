package dev.isaacudy.udytils.architecture.fixtures.sharedrules

import dev.isaacudy.udytils.architecture.Construct
import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

/**
 * Fixture for [dev.isaacudy.udytils.architecture.SharedRuleBaseClassTest]: a group base class
 * carrying rules shared by two sided groups, each registering under the concrete group's own id.
 */
internal abstract class SidedGroupRules(
    side: String,
    inPackage: String,
    constructs: List<Construct<*>>,
) : RuleGroup(inPackage = inPackage, constructs = constructs) {
    @Describe("Domain code must not import platform types")
    val noPlatformDeps by rule {
        rationale("Shared group rule body, instantiated for the $side side")
        scope { _, _ -> emptyList() }
    }
}
