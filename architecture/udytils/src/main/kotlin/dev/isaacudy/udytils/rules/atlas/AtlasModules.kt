package dev.isaacudy.udytils.rules.atlas

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The atlas family is a UI atlas generator: a scanner/assembler core library and a Gradle " +
        "plugin that wraps it. It is fully standalone within udytils.",
)
object AtlasModules : RuleGroup() {

    @Describe("The atlas family must not depend on any other udytils family")
    val standaloneFamily by rule {
        rationale(
            "the atlas scans any Enro+Paparazzi project without requiring the rest of udytils " +
                "on the classpath; importing another family would entangle them",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/atlas/" in it },
                forbidden = listOf(Packages.UDYTILS),
                allowed = listOf(Packages.ATLAS),
                because = "the atlas family is standalone within udytils",
            )
        }
    }
}
