package dev.isaacudy.udytils.architecture.fixtures.sharedrules.server

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.fixtures.sharedrules.SidedUseCaseRules

/** Fixture: pure inheritance — no side-specific rules of its own. */
@Describe("Fixture: the server-side UseCase construct.")
internal object UseCase : SidedUseCaseRules<ServerGroup>(side = "server")
