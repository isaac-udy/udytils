package dev.isaacudy.udytils.architecture.fixtures.sharedrules

import dev.isaacudy.udytils.architecture.Construct
import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.hasNameEndingWith
import dev.isaacudy.udytils.architecture.isClass
import dev.isaacudy.udytils.architecture.isInPackage

/**
 * Fixture for [dev.isaacudy.udytils.architecture.SharedRuleBaseClassTest]: a construct base class
 * shared by two groups. Rules declared here register once per concrete `object`, under that
 * object's own `<Group>.<Construct>.<rule>` id.
 *
 * The side is a **constructor parameter**, not an open val: rule blocks run during base-class
 * initialization, before a subclass override would be initialized (pinned by the open-val test in
 * SharedRuleBaseClassTest). `@Describe` statements are compile-time constants, so shared statements
 * are worded side-neutrally; side-specific wording belongs on the concrete object's own rules.
 */
internal abstract class SidedUseCaseRules<G : RuleGroup>(
    side: String,
) : Construct<G>(
    requirements = listOf(
        isClass,
        hasNameEndingWith("UseCase"),
        isInPackage("feature..$side.domain.."),
    ),
) {
    @Describe("A UseCase must not contain mutable state")
    val noMutableState by rule {
        rationale("Shared rule body, instantiated for the $side side")
        constrain { _, _ -> emptyList() }
    }

    @Describe("A UseCase may inject domain interfaces to perform its logic")
    val mayInjectDomainInterfaces by guidance
}
