package dev.isaacudy.udytils.architecture.fixtures.sharedrules.server

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.SidedGroupRules

@Describe("Fixture: the server-side domain group.")
internal object ServerGroup : SidedGroupRules(
    side = "server",
    inPackage = "feature..server.domain..",
    constructs = listOf(UseCase),
)
