package dev.isaacudy.udytils.rules.architecture

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The architecture family is the architecture-as-code framework: the rule/docs engine " +
        "(architecture-core), the exemption annotation (architecture-annotations), and the Gradle " +
        "plugin. It is fully standalone within udytils — a consumer adopts it without touching the " +
        "other families.",
)
object ArchitectureModules : RuleGroup() {

    @Describe("The architecture family must not depend on any other udytils family")
    val standaloneFamily by rule {
        rationale(
            "the framework governs codebases that may use none of udytils' other libraries; any " +
                "import from another family would make it unusable outside this repository's stack",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/architecture/" in it && "/architecture/udytils/" !in it },
                forbidden = listOf(Packages.UDYTILS),
                allowed = listOf(Packages.ARCHITECTURE),
                because = "the architecture family is standalone within udytils",
            )
        }
    }
}
