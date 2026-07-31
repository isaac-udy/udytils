package dev.isaacudy.udytils.architecture.fixtures.sharedrules.client

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.SidedGroupRules

@Describe("Fixture: the client-side domain group.")
internal object ClientGroup : SidedGroupRules(
    side = "client",
    inPackage = "feature..client.domain..",
    constructs = listOf(UseCase),
)
