package dev.isaacudy.udytils.rules.ui

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The ui module holds Compose Multiplatform components, ViewModel state, and navigation-based " +
        "flows (errors, confirmations, permissions). It builds on core and on Enro; the server-side " +
        "families are out of bounds.",
)
object UiModule : RuleGroup() {

    @Describe("The ui module may only depend on the core module")
    val dependsOnlyOnCore by rule {
        rationale(
            "ui exists to render core's state and error types; rpc transports, database code and " +
                "the architecture framework have no business in UI code, and keeping them out keeps " +
                "ui consumable by apps that use none of them",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/ui/src/" in it },
                forbidden = listOf(Packages.URPC, Packages.POSTGRES, Packages.ARCHITECTURE),
                because = "ui may only build on core",
            )
        }
    }
}
