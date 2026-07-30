package dev.isaacudy.udytils.architecture.fixtures.sharedrules.client

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.SidedUseCaseRules

/** Fixture: inherits the shared rules and adds a side-specific one on top. */
@Describe("Fixture: the client-side UseCase construct.")
internal object UseCase : SidedUseCaseRules<ClientGroup>(side = "client") {
    @Describe("A client UseCase must expose exactly one operator invoke")
    val clientOnlyRule by rule {
        constrain { _, _ -> emptyList() }
    }
}
