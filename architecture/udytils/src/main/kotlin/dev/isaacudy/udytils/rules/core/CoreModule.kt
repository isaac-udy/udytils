package dev.isaacudy.udytils.rules.core

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The core module is the foundation of udytils: async state modelling, coroutine utilities, " +
        "presentable errors, and file helpers. Every other family may depend on it; it depends on " +
        "none of them.",
)
object CoreModule : RuleGroup() {

    @Describe("The core module must not depend on any other udytils module")
    val standalone by rule {
        rationale(
            "core sits at the bottom of the dependency graph, so a dependency on any other " +
                "family would create a cycle or drag UI/server concerns into every consumer",
        )
        note(
            "ui declares ViewModelState inside core's dev.isaacudy.udytils.state package, so this " +
                "boundary is attributed by file location and checked against ui's unambiguous " +
                "package roots",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/core/src/" in it && "/postgres/" !in it && "/architecture/" !in it },
                forbidden = Packages.UI + listOf(Packages.URPC, Packages.POSTGRES, Packages.ARCHITECTURE),
                because = "core must not depend on any other udytils family",
            )
        }
    }
}
