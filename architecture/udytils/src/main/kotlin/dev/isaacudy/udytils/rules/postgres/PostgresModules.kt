package dev.isaacudy.udytils.rules.postgres

import dev.isaacudy.udytils.rules.Packages
import dev.isaacudy.udytils.rules.forbiddenImports

import dev.isaacudy.udytils.architecture.Describe
import dev.isaacudy.udytils.architecture.RuleGroup

@Describe(
    "The postgres family is a JVM-only Postgres + Exposed toolkit: runtime column types and " +
        "migration/notification helpers, a build-time codegen engine, a Gradle plugin, and dev/test " +
        "helpers. It is fully standalone within udytils.",
)
object PostgresModules : RuleGroup() {

    @Describe("The postgres family must not depend on any other udytils family")
    val standaloneFamily by rule {
        rationale(
            "a server can adopt the postgres toolkit without inheriting KMP, Compose or rpc " +
                "dependencies — and that only stays true while the family imports nothing from " +
                "the rest of udytils",
        )
        scope { scope, exempt ->
            forbiddenImports(
                scope = scope,
                exempt = exempt,
                inFiles = { "/postgres/" in it },
                forbidden = listOf(Packages.UDYTILS),
                allowed = listOf(Packages.POSTGRES),
                because = "the postgres family is standalone within udytils",
            )
        }
    }
}
