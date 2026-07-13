package dev.isaacudy.udytils.rules.core

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup
import dev.isaacudy.udytils.architecture.Violation
import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

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

    @Describe("The core module must not declare a Gradle dependency on any other project")
    val standaloneInBuild by rule {
        rationale(
            "the import rule catches code-level coupling; this one catches a build-level " +
                "dependency added before any import exists, which would still change what every " +
                "core consumer transitively resolves",
        )
        note(
            "the graph reads projects.x.y accessors and string-notation project dependencies; a " +
                "dependency declared by maven coordinate resolved through dependencySubstitution " +
                "is not visible to it",
        )
        moduleGraph { graph, exempt ->
            graph.edges
                .filter { it.from == ":core" }
                .filterNot(exempt)
                .map { edge -> Violation("${edge.file}:${edge.line}", "declares a dependency on ${edge.to}") }
        }
    }
}
